#!/usr/bin/env python3
"""តេស្ត ZIP → APK backend: ពន្លា/ពិនិត្យ ZIP និង endpoint /api/zip/build។

រត់: python3 tests/test_zipbuild.py
APK ពិតកើតចេញពី GitHub Actions — នៅទីនេះពិនិត្យ validation, zip-slip, root
detection និងសារកំហុស (គ្មាន GH_TOKEN ក្នុង VM → endpoint ត្រឡប់ 503 ជាការត្រឹមត្រូវ)។
"""
import io
import os
import sys
import xml.etree.ElementTree as ET
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

print('== endpoint /api/zip/inspect (បង្ហាញឯកសារមុន build) ==')
ins = client.post('/api/zip/inspect',
                  data={'zip': (io.BytesIO(make_zip(FLAT)), 'demo.zip')},
                  content_type='multipart/form-data')
ij = ins.get_json() or {}
eq(ins.status_code, 200, 'ZIP ត្រឹមត្រូវ → 200')
ok(ij.get('ok') is True, 'ok=true')
ok(bool(ij.get('upload_id')), 'ត្រឡប់ upload_id (build ដោយមិន upload ម្តងទៀត)')
ok(len(ij.get('list') or []) >= 5, 'បញ្ជីឯកសារពេញលេញ')
ok(all(set(e) == {'path', 'size'} for e in ij['list']), 'ធាតុនីមួយៗមាន path + size')
ok('app/src/main/AndroidManifest.xml' in [e['path'] for e in ij['list']], 'បញ្ជីមាន manifest')
eq(ij.get('name'), 'demo.zip', 'ឈ្មោះ file សម្រាប់ title')
badins = client.post('/api/zip/inspect',
                     data={'zip': (io.BytesIO(make_zip({'a.txt': 'x'})), 'bad.zip')},
                     content_type='multipart/form-data')
eq(badins.status_code, 400, 'ZIP ខ្វះ Gradle → 400')
ok(bool((badins.get_json() or {}).get('problems')), 'បង្ហាញបញ្ហាទាំងអស់')
ok(not (badins.get_json() or {}).get('upload_id'), 'ZIP ខូច → គ្មាន upload_id')

print('== upload cache (inspect → build) ==')
token = zipbuild.save_upload(make_zip(FLAT), 'demo.zip')
data, name = zipbuild.load_upload(token)
eq(name, 'demo.zip', 'load_upload ត្រឡប់ឈ្មោះ')
ok(len(data) > 0, 'load_upload ត្រឡប់ ZIP')
try:
    zipbuild.load_upload('../../etc/passwd')
    ok(False, 'token មិនត្រឹមត្រូវ → ZipError')
except zipbuild.ZipError:
    ok(True, 'token មិនត្រឹមត្រូវ → ZipError')
try:
    zipbuild.load_upload('0' * 16)
    ok(False, 'token បាត់ → ZipError')
except zipbuild.ZipError:
    ok(True, 'token បាត់ → ZipError')
b2 = client.post('/api/zip/build', data={'upload_id': token},
                 content_type='multipart/form-data')
eq(b2.status_code, 503 if not gh_ready else 201, 'build ពី upload_id ដំណើរការ')
exp = client.post('/api/zip/build', data={'upload_id': 'f' * 16},
                  content_type='multipart/form-data')
eq(exp.status_code, 410, 'upload ផុតកំណត់ → 410 (UI upload ម្តងទៀត)')
ok((exp.get_json() or {}).get('expired') is True, 'flag expired')

print('== push_branch clean (គ្មាន file repo មេលាយ) ==')


class FakeGH(web.GitHub):
    def __init__(self):
        self.repo = 'o/r'
        self.calls = []
        self.tree = None

    def call(self, path, method='GET', body=None):
        self.calls.append((path, method))
        if '/git/ref/heads/' in path:
            return {'object': {'sha': 'BASE'}}
        if '/git/commits/' in path and method == 'GET':
            return {'tree': {'sha': 'TREE'}}
        if 'recursive=1' in path:
            return {'tree': [
                {'path': '.github/workflows/build-zip-apk.yml', 'type': 'blob',
                 'mode': '100644', 'sha': 'WF'},
                {'path': 'settings.gradle.kts', 'type': 'blob', 'mode': '100644',
                 'sha': 'STALE'},
                {'path': 'app', 'type': 'tree', 'mode': '040000', 'sha': 'SUBTREE'},
            ]}
        if path.endswith('/git/trees') and method == 'POST':
            self.tree = body
            return {'sha': 'NEWTREE'}
        if path.endswith('/git/commits') and method == 'POST':
            return {'sha': 'COMMIT'}
        return {}


fake = FakeGH()
fake.push_branch('build/x', {'settings.gradle': 'rootProject.name = "x"',
                            'app/build.gradle': 'x'}, 'm', clean=True)
paths = [e['path'] for e in (fake.tree or {}).get('tree', [])]
ok('base_tree' not in (fake.tree or {}), 'clean → គ្មាន base_tree')
ok('.github/workflows/build-zip-apk.yml' in paths, 'clean → workflow នៅជាប់')
ok('settings.gradle.kts' not in paths, 'clean → គ្មាន settings.gradle.kts ចាស់របស់ repo')
ok('settings.gradle' in paths, 'clean → file របស់អ្នកប្រើមាន')
ok('app' not in paths, 'clean → app/ របស់ repo (Flask) មិនលាយ')
fake2 = FakeGH()
fake2.push_branch('build/y', {'a.txt': 'x'}, 'm')
ok('base_tree' in (fake2.tree or {}), 'ធម្មតា → base_tree នៅដូចដើម')

print('== ចាត់ប្រភេទ project ពេល import (គ្រប់ app ទាំងអស់) ==')
import ide  # noqa: E402 — ត្រូវការបន្ទាប់ពី app/zipbuild

ok(ide.project_kind(FLAT) == 'gradle', 'Android project ពេញលេញ → gradle (build ដូចដើម)')
ok(ide.is_android_project({'AndroidManifest.xml': '<manifest/>'}),
   'Manifest មួយក៏រាប់ជា Android project')
SRC_ONLY = {
    'app/src/main/AndroidManifest.xml':
        '<manifest xmlns:android="http://schemas.android.com/apk/res/android" '
        'package="com.example.pool"><application android:label="@string/app_name"/></manifest>',
    'app/src/main/java/com/example/pool/OverlayService.java':
        'package com.example.pool;\nclass OverlayService {}\n',
}
ok(ide.project_kind(SRC_ONLY) == 'android', 'source សុទ្ធ (គ្មាន settings.gradle) → android')
wrapped = ide.prepare_android_project(SRC_ONLY, 'Pool Floating')
ok(all(k in wrapped for k in SRC_ONLY), 'file អ្នកប្រើមិនត្រូវបាត់/របួស')
ok('settings.gradle.kts' in wrapped and 'include(":app")' in wrapped['settings.gradle.kts'],
   'បំពេញ settings.gradle.kts')
ok('app/build.gradle.kts' in wrapped and
   'applicationId = "com.example.pool"' in wrapped['app/build.gradle.kts'],
   'build.gradle.kts ប្រើ package ពី Manifest')
ok('signing/debug.p12' not in wrapped['app/build.gradle.kts'],
   'គ្មាន signing config ដែលខ្វះ file ក្នុង build branch')
ok('com.android.application' in wrapped['build.gradle.kts'], 'root build.gradle.kts មាន AGP')
ok('@string/app_name' in wrapped['app/src/main/AndroidManifest.xml'] and
   'res/values/strings.xml' in "".join(wrapped.keys()),
   'បំពេញ string ដែល Manifest សំដៅ')
KEEP = {'app/build.gradle.kts': 'android { }\n'}
ok(ide.prepare_android_project(dict(SRC_ONLY, **KEEP), 'x')['app/build.gradle.kts'] == 'android { }\n',
   'build.gradle របស់អ្នកប្រើមិនត្រូវសរសេរជំនួស')
ROOTMOD = {'src/main/AndroidManifest.xml': SRC_ONLY['app/src/main/AndroidManifest.xml'],
           'src/main/java/A.kt': 'package com.example.pool\n'}
root_out = ide.prepare_android_project(ROOTMOD, 'Root')
ok('include(":app")' not in root_out['settings.gradle.kts'] and
   'id("com.android.application") version "8.5.2"' in root_out['build.gradle.kts'],
   'project module តែមួយនៅ root → build.gradle.kts មាន version ដែរ')
ok(ide.project_kind({'index.html': ''}) == 'webview', 'HTML → webview')
ok(ide.project_kind({'site/home.html': ''}) == 'webview', 'HTML ឈ្មោះផ្សេង → webview')
ok(ide.project_kind({'MainActivity.kt': ''}) == 'kotlin', '.kt → kotlin')
ok(ide.project_kind({'native-lib.cpp': ''}) == 'ndk', '.cpp → ndk')
ok(ide.project_kind({'main.py': ''}) == 'other', 'ភាសាផ្សេង → other (មិនបដិសេធ)')
shim = ide.ensure_index_html({'site/home.html': '<h1>x</h1>'}, 'My App')
ok('site/home.html' in shim['index.html'], 'បង្កើត index.html ជា shim ទៅ page ដែលមាន')
ok('index.html' in ide.ensure_index_html({'main.py': 'x'}, 'Py'), 'គ្មាន HTML → index.html អប្បបរមា')

print('== import ZIP រក្សា path ពិត ==')

def _zip(entries):
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, 'w') as zf:
        for name, data in entries.items():
            zf.writestr(name, data)
    return buf.getvalue()


src_only = ide.import_zip(_zip({'src/main/AndroidManifest.xml': '<manifest/>',
                                'src/main/java/A.kt': 'package a\n'}))
ok('src/main/AndroidManifest.xml' in src_only,
   'folder src/ មិនត្រូវដកចេញ (path ពិតរបស់ project)')
ok(ide.project_kind(src_only) == 'android', 'project ដែលមានតែ src/ → android')
ok('src/main/AndroidManifest.xml' in ide.prepare_android_project(src_only, 'A'),
   'wrap មិនប្តូរ path របស់ source')
wrapped_zip = ide.import_zip(_zip({'MyApp/index.html': '<h1>x</h1>', 'MyApp/app.js': ''}))
ok(sorted(wrapped_zip) == ['app.js', 'index.html'], 'top folder ជា wrapper → ដកចេញ')

print('== resource ដែល Manifest យោង តែគ្មាន ==')
_nores = {
    'settings.gradle.kts': SETTINGS,
    'build.gradle.kts': ROOT_BUILD,
    'app/build.gradle.kts': APP_BUILD,
    'app/src/main/AndroidManifest.xml': (
        '<manifest xmlns:android="http://schemas.android.com/apk/res/android" '
        'package="com.example.demo">\n'
        '  <application android:icon="@mipmap/ic_launcher" '
        'android:roundIcon="@mipmap/ic_launcher_round" '
        'android:label="@string/app_name" android:theme="@style/AppTheme" />\n'
        '</manifest>'),
    'app/src/main/java/com/example/demo/MainActivity.kt': 'package com.example.demo\n',
}
_fixed = ide.ensure_android_resources(_nores, 'Pool Mod')
for _dens in ('mdpi', 'hdpi', 'xhdpi', 'xxhdpi', 'xxxhdpi'):
    ok(isinstance(_fixed.get('app/src/main/res/mipmap-%s/ic_launcher.png' % _dens), bytes),
       'បង្កើត ic_launcher.png (%s)' % _dens)
ok('app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.png' in _fixed,
   'បង្កើត ic_launcher_round.png')
ok('<string name="app_name">' in _fixed.get('app/src/main/res/values/strings.xml', ''),
   'បង្កើត @string/app_name')
ok('<style name="AppTheme"' in _fixed.get('app/src/main/res/values/styles.xml', ''),
   'បង្កើត @style/AppTheme')
for _p in ('app/src/main/res/values/strings.xml', 'app/src/main/res/values/styles.xml'):
    ET.fromstring(_fixed[_p])
ok(True, 'XML ដែលបង្កើត parse បាន')
_kept = ide.ensure_android_resources(dict(_fixed, **{
    'app/src/main/res/values/strings.xml': '<resources><string name="app_name">Mine</string></resources>'}),
    'Pool Mod')
ok(_kept['app/src/main/res/values/strings.xml'].count('app_name') == 1,
   'resource ដែលមានស្រាប់មិនត្រូវសរសេរជំនួស')
ok(not any('res/' in n for n in ide.ensure_android_resources(
    dict(_nores, **{'app/src/main/AndroidManifest.xml': MANIFEST}), 'A')),
   'project ដែល Manifest មិនយោង resource → មិនបន្ថែមអ្វី')

print('== កែកំហុស Kotlin ដែលបញ្ឈប់ build ==')
_kt = {'A.kt': 'class A {\n    const val K = 1\n    fun f() {\n        const val r = 22f\n    }\n}\n'}
_fx = ide.repair_android_sources(_kt)['A.kt']
ok('    const val K = 1' in _fx, 'const ក្នុង class នៅដើម')
ok('        val r = 22f' in _fx, 'const នៅ local ត្រូវដកចេញ')
ok(ide.repair_android_sources({'a.java': 'class A {}'})['a.java'] == 'class A {}',
   'file មិនមែន Kotlin មិនត្រូវប៉ះ')

print('== workflow សម្រាប់ ZIP ==')
wf = open(os.path.join(ROOT, '.github/workflows/build-zip-apk.yml'), encoding='utf-8').read()
ok('assembleDebug' in wf, 'workflow រត់ assembleDebug')
ok('setup-java' in wf and 'setup-android' in wf, 'workflow ដំឡើង JDK + Android SDK')
ok('build.log' in wf, 'workflow ផ្សព្វផ្សាយ build.log')
ok('out/app.apk' in wf, 'workflow ផ្សព្វផ្សាយ app.apk')
ok('${{ inputs.tag }}' in wf, 'workflow ប្រើ tag ពី dispatch')

print('== project ដើមគ្រប់ mode (IDE) build បាន ==')
import androidproject  # noqa: E402
import re as _re  # noqa: E402

_samples = {
    'webview': {'index.html': '<!doctype html><h1>x</h1>'},
    'kotlin': {},
    'java': {},
    'ndk': {},
}
for _mode, _files in _samples.items():
    _pkg = 'org.pyapp.qa' + _mode
    try:
        _proj = androidproject.android_project_files(
            'QA ' + _mode, _pkg, _mode, dict(_files))
        _err = ''
    except Exception as _exc:  # noqa: BLE001
        _proj, _err = {}, str(_exc)
    ok(_err == '', 'mode %s → បង្កើត project បាន %s' % (_mode, _err))
    if not _proj:
        continue
    for _need in ('app/build.gradle.kts', 'settings.gradle.kts',
                  'app/src/main/AndroidManifest.xml'):
        ok(_need in _proj, 'mode %s មាន %s' % (_mode, _need))
    try:
        ET.fromstring(_proj['app/src/main/AndroidManifest.xml'])
        ok(True, 'mode %s manifest ជា XML ត្រឹមត្រូវ' % _mode)
    except ET.ParseError as _exc:
        ok(False, 'mode %s manifest XML: %s' % (_mode, _exc))
    _srcs = {n: c for n, c in _proj.items()
             if isinstance(c, str) and n.endswith(('.kt', '.java'))}
    ok(bool(_srcs), 'mode %s មាន source ដើម' % _mode)
    for _name, _code in _srcs.items():
        if not _name.endswith('.kt'):
            continue
        ok(_code.count('{') == _code.count('}'),
           'mode %s %s braces balance' % (_mode, _name.rsplit('/', 1)[-1]))
        # local `val X` ក្នុង scope ជាមួយ `apply { X = ... }` → Kotlin error ពិត
        for _var in _re.findall(r'\bval\s+(\w+)\s*=', _code):
            if _re.search(r'^\s{8,}' + _var + r'\s*=\s', _code, _re.M):
                ok(False, 'mode %s: local val %s ត្រូវ property ក្នុង apply '
                          '(Val cannot be reassigned)' % (_mode, _var))
                break
    ok('hint' not in _re.findall(r'\bval\s+(\w+)\s*=\s*TextView',
                                 _proj.get('app/src/main/java/org/pyapp/qa'
                                           + _mode + '/MainActivity.kt', '')),
       'mode %s មិនមាន local val hint (shadow EditText.hint)' % _mode)

print('')
if fails:
    print('FAILED %d checks' % fails)
    sys.exit(1)
print('ALL ZIP-BUILD CHECKS PASSED')
