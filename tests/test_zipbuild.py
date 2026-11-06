#!/usr/bin/env python3
"""តេស្ត ZIP → APK backend: ពន្លា/ពិនិត្យ ZIP និង endpoint /api/zip/build។

រត់: python3 tests/test_zipbuild.py
APK ពិតកើតចេញពី GitHub Actions — នៅទីនេះពិនិត្យ validation, zip-slip, root
detection និងសារកំហុស (គ្មាន GH_TOKEN ក្នុង VM → endpoint ត្រឡប់ 503 ជាការត្រឹមត្រូវ)។
"""
import io
import os
import sys
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, ROOT)
import zipbuild  # noqa: E402

fails = 0


def ok(cond, label):
    global fails
    if cond:
        print('  ok   ' + label)
    else:
        fails += 1
        print('  FAIL ' + label)


def eq(actual, expected, label):
    ok(actual == expected, label if actual == expected
       else '%s  (got %r want %r)' % (label, actual, expected))


def make_zip(entries):
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, 'w', zipfile.ZIP_DEFLATED) as zf:
        for name, data in entries.items():
            zf.writestr(name, data)
    return buf.getvalue()


SETTINGS = 'rootProject.name = "Demo"\ninclude(":app")\n'
ROOT_BUILD = '''
plugins {
    id("com.android.application") version "8.5.2" apply false
}
'''
APP_BUILD = '''
plugins { id("com.android.application") }
android {
    namespace = "com.example.demo"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.example.demo"
        minSdk = 24
        targetSdk = 34
    }
}
'''
MANIFEST = '<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.example.demo"><application/></manifest>'
KT = 'package com.example.demo\nclass MainActivity\n'
WRAPPER = 'distributionUrl=https\\://services.gradle.org/distributions/gradle-8.7-bin.zip\n'

FLAT = {
    'settings.gradle.kts': SETTINGS,
    'build.gradle.kts': ROOT_BUILD,
    'gradlew': '#!/bin/sh\n',
    'gradle/wrapper/gradle-wrapper.properties': WRAPPER,
    'app/build.gradle.kts': APP_BUILD,
    'app/src/main/AndroidManifest.xml': MANIFEST,
    'app/src/main/java/com/example/demo/MainActivity.kt': KT,
    'app/build/intermediates/junk.txt': 'skip me',
    '.git/config': '[core]',
    'README.md': '# demo',
}

print('== extract / zip-slip ==')
files = zipbuild.extract(make_zip(FLAT))
ok('app/build/intermediates/junk.txt' not in files, 'កាត់ folder build/ ចេញ')
ok('.git/config' not in files, 'កាត់ .git ចេញ')
eq(len(files), 8, 'ចំនួន file បន្ទាប់ពីកាត់ (10 - build - .git)')
for bad in ('../evil.txt', '/etc/passwd', 'a/../../evil.txt', 'C:/evil.txt'):
    try:
        zipbuild.extract(make_zip({'settings.gradle': SETTINGS, bad: 'x'}))
        ok(False, 'បដិសេធ path គ្រោះថ្នាក់: ' + bad)
    except zipbuild.ZipError:
        ok(True, 'បដិសេធ path គ្រោះថ្នាក់: ' + bad)
try:
    zipbuild.extract(b'not a zip')
    ok(False, 'បដិសេធ file មិនមែន ZIP')
except zipbuild.ZipError:
    ok(True, 'បដិសេធ file មិនមែន ZIP')
try:
    zipbuild.extract(make_zip({}))
    ok(False, 'បដិសេធ ZIP ទទេ')
except zipbuild.ZipError:
    ok(True, 'បដិសេធ ZIP ទទេ')

print('== inspect (project ត្រឹមត្រូវ) ==')
info = zipbuild.inspect_zip(make_zip(FLAT))
eq(info['problems'], [], 'គ្មានបញ្ហា')
eq(info['root'], '', 'root = zip root')
eq(info['package'], 'com.example.demo', 'applicationId → package សម្រាប់ job')
eq(info['summary']['app_id'], 'com.example.demo', 'summary app_id')
eq(info['summary']['compile_sdk'], '34', 'summary compileSdk')
eq(info['summary']['min_sdk'], '24', 'summary minSdk')
eq(info['summary']['wrapper'], '8.7', 'Gradle wrapper version')
ok(info['summary']['gradlew'] is True, 'រកឃើញ gradlew')
ok(info['files']['app/src/main/AndroidManifest.xml'], 'មាន manifest')

print('== inspect (ZIP មាន top folder) ==')
wrapped = {('MyApp/' + k): v for k, v in FLAT.items()}
info2 = zipbuild.inspect_zip(make_zip(wrapped))
eq(info2['root'], 'MyApp', 'រកឃើញ top folder')
eq(info2['problems'], [], 'top folder → គ្មានបញ្ហា')
ok('settings.gradle.kts' in info2['files'], 'strip prefix រួច keys ចាប់ពី root')
eq(info2['summary']['root'], 'MyApp', 'summary បង្ហាញ root')

print('== inspect (ZIP ខ្វះ file) ==')
missing = {'settings.gradle.kts': SETTINGS, 'app/build.gradle.kts': APP_BUILD}
info3 = zipbuild.inspect_zip(make_zip(missing))
ok(any('AndroidManifest' in p for p in info3['problems']), 'រាយបញ្ហាខ្វះ Manifest')
ok(any('source' in p for p in info3['problems']), 'រាយបញ្ហាខ្វះ source')
nosettings = {'build.gradle.kts': ROOT_BUILD, 'app/src/main/AndroidManifest.xml': MANIFEST,
              'app/src/main/java/A.kt': KT}
info4 = zipbuild.inspect_zip(make_zip(nosettings))
ok(any('settings.gradle' in p for p in info4['problems']), 'រាយបញ្ហាខ្វះ settings.gradle')

print('== endpoint /api/zip/build ==')
import app as web  # noqa: E402
client = web.app.test_client()
r = client.post('/api/zip/build', data={}, content_type='multipart/form-data')
eq(r.status_code, 400, 'គ្មាន file → 400')
eq((r.get_json() or {}).get('step'), 'upload', 'step=upload')
bad = client.post('/api/zip/build',
                  data={'zip': (io.BytesIO(make_zip({'a.txt': 'x'})), 'bad.zip')},
                  content_type='multipart/form-data')
eq(bad.status_code, 400, 'ZIP ខ្វះ Gradle/Manifest → 400')
ok('settings.gradle' in (bad.get_json() or {}).get('error', ''), 'សារកំហុសច្បាស់ពី validation')
good = client.post('/api/zip/build',
                   data={'zip': (io.BytesIO(make_zip(FLAT)), 'demo.zip')},
                   content_type='multipart/form-data')
gh_ready = bool(web.GH_REPO and web.GH_TOKEN)
if gh_ready:
    eq(good.status_code, 201, 'project ត្រឹមត្រូវ → 201 (បញ្ជូនទៅ build)')
    jid = (good.get_json() or {}).get('id', '')
    ok(bool(jid), 'ត្រឡប់ job id')
else:
    eq(good.status_code, 503, 'គ្មាន GH_TOKEN → 503 (build ក្នុង cloud មិនអាចធ្វើ)')
    ok('GH_REPO' in (good.get_json() or {}).get('error', ''), 'សារបញ្ជាក់ GH_REPO/GH_TOKEN')

print('== workflow សម្រាប់ ZIP ==')
wf = open(os.path.join(ROOT, '.github/workflows/build-zip-apk.yml'), encoding='utf-8').read()
ok('assembleDebug' in wf, 'workflow រត់ assembleDebug')
ok('setup-java' in wf and 'setup-android' in wf, 'workflow ដំឡើង JDK + Android SDK')
ok('build.log' in wf, 'workflow ផ្សព្វផ្សាយ build.log')
ok('out/app.apk' in wf, 'workflow ផ្សព្វផ្សាយ app.apk')
ok('${{ inputs.tag }}' in wf, 'workflow ប្រើ tag ពី dispatch')

print('')
if fails:
    print('FAILED %d checks' % fails)
    sys.exit(1)
print('ALL ZIP-BUILD CHECKS PASSED')
