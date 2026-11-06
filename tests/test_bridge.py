#!/usr/bin/env python3
"""តេស្ត JS↔Kotlin bridge (WebView) ដែលបង្កើតដោយ androidproject.py។

រត់: python3 tests/test_bridge.py
គ្មាន Kotlin compiler ក្នុង VM → ពិនិត្យ structure (brace/paren balance, imports,
@JavascriptInterface, placeholders) និងពិនិត្យ JS ដោយ `node --check` ពិតប្រាកដ។
"""
import os
import subprocess
import sys
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, ROOT)
import androidproject as ap  # noqa: E402

fails = 0


def ok(cond, label):
    global fails
    if cond:
        print('  ok   ' + label)
    else:
        fails += 1
        print('  FAIL ' + label)


def eq(actual, expected, label):
    ok(actual == expected, label + '  (got %r want %r)' % (actual, expected)
       if actual != expected else label)


def balance(src):
    """រាប់ {} និង () ដោយមិនរាប់ក្នុង string/char/comment (Kotlin)។"""
    curly = paren = 0
    i = 0
    n = len(src)
    while i < n:
        ch = src[i]
        nxt = src[i + 1] if i + 1 < n else ''
        if ch == '/' and nxt == '/':
            i = src.find('\n', i)
            if i < 0:
                break
            continue
        if ch == '/' and nxt == '*':
            i = src.find('*/', i + 2)
            if i < 0:
                break
            i += 2
            continue
        if ch in '"\'':
            quote = ch
            i += 1
            while i < n:
                if src[i] == '\\':
                    i += 2
                    continue
                if src[i] == quote:
                    break
                i += 1
            i += 1
            continue
        if ch == '{':
            curly += 1
        elif ch == '}':
            curly -= 1
        elif ch == '(':
            paren += 1
        elif ch == ')':
            paren -= 1
        i += 1
    return curly, paren


HTML = '<!doctype html><html><body><h1>សួស្តី</h1></body></html>'
FILES = {'index.html': HTML, 'app.js': 'console.log(1);'}

proj = ap.android_project_files('Bridge App', 'org.pyapp.demo', 'webview', FILES)
kt = proj['app/src/main/java/org/pyapp/demo/MainActivity.kt']
html = proj['app/src/main/assets/www/index.html']
bridge = proj['app/src/main/assets/www/appforge-bridge.js']
manifest = proj['app/src/main/AndroidManifest.xml']

print('== Kotlin bridge ==')
ok('addJavascriptInterface(Bridge(), "AppForge")' in kt, 'WebView ភ្ជាប់ bridge ឈ្មោះ AppForge')
ok('inner class Bridge' in kt, 'Bridge ជា inner class របស់ MainActivity')
ok('import android.webkit.JavascriptInterface' in kt, 'import JavascriptInterface')
ok('loadUrl("file:///android_asset/www/index.html")' in kt, 'បើក index.html ពី assets')
ok('package org.pyapp.demo' in kt and '@@PKG@@' not in kt, 'package ត្រូវបានជំនួស')

for method in ('toast', 'vibrate', 'copy', 'share', 'openUrl', 'notify', 'info',
               'net', 'battery', 'pickImage', 'permission', 'granted', 'version'):
    ok(('fun %s(' % method) in kt, 'bridge method: %s' % method)
ok(kt.count('@JavascriptInterface') >= 13, 'ម៉ាស៊ីន JS ចូលបាន ≥13 method (@JavascriptInterface)')
ok('web.evaluateJavascript' in kt and '__AppForgeNative' in kt,
   'Kotlin ហៅ JS តាម __AppForgeNative (async callback)')
ok('requestPermissions(arrayOf(name), permCode)' in kt, 'ស្នើ runtime permission ពិត')
ok('onRequestPermissionsResult' in kt and 'onActivityResult' in kt, 'override callback ទាំងពីរ')
ok('thread' not in kt.lower().replace('thread {', '').replace('thread(', '')
   or 'Thread {' in kt, 'decode រូបភាពក្នុង background Thread')
ok('"data:image/jpeg;base64,"' in kt, 'pickImage ត្រឡប់ data URL')
curly, paren = balance(kt)
eq(curly, 0, 'Kotlin {} balance')
eq(paren, 0, 'Kotlin () balance')

print('== JS helper (assets/www/appforge-bridge.js) ==')
ok(bridge is not None and 'window.Native' in bridge, 'បង្កើត appforge-bridge.js ស្វ័យប្រវត្តិ')
ok('window.__AppForgeNative' in bridge, 'ផ្តល់ callback សម្រាប់ Kotlin')
ok('_pickImage' in bridge and '_permission' in bridge, 'callback pickImage / permission')
with tempfile.TemporaryDirectory() as tmp:
    js = os.path.join(tmp, 'bridge.js')
    with open(js, 'w', encoding='utf-8') as f:
        f.write(bridge)
    r = subprocess.run(['node', '--check', js], capture_output=True, text=True)
    ok(r.returncode == 0, 'node --check bridge JS' + ('' if r.returncode == 0
                                                      else ': ' + r.stderr.strip()[:200]))

print('== index.html injection ==')
eq(html.count('appforge-bridge.js'), 1, '<script> បញ្ចូលតែម្តង')
ok(html.index('appforge-bridge.js') < html.lower().rindex('</body>'),
   '<script> នៅមុន </body>')
ok(html.startswith(HTML[:20]), 'content ដើមរបស់អ្នកប្រើនៅដដែល')

print('== permissions ==')
for perm in ('android.permission.VIBRATE', 'android.permission.POST_NOTIFICATIONS',
             'android.permission.ACCESS_NETWORK_STATE', 'android.permission.INTERNET'):
    ok(perm in manifest, 'manifest មាន ' + perm.split('.')[-1])

print('== មិនប៉ះ app type ផ្សេង ==')
native = ap.android_project_files('K', 'org.pyapp.demo', 'kotlin', {'MainActivity.kt': ''})
ok(not any(k.endswith('appforge-bridge.js') for k in native), 'Kotlin native គ្មាន bridge')
offline = ap.android_project_files('W', 'org.pyapp.demo', 'webview', FILES, online=False)
ok('ACCESS_NETWORK_STATE' not in offline['app/src/main/AndroidManifest.xml'],
   'offline → គ្មាន ACCESS_NETWORK_STATE')
own = ap.android_project_files('W', 'org.pyapp.demo', 'webview', dict(
    FILES, **{'appforge-bridge.js': '/* mine */'}))
eq(own['app/src/main/assets/www/appforge-bridge.js'], '/* mine */', 'bridge របស់អ្នកប្រើមិនត្រូវ override')
pre = ap.android_project_files('W', 'org.pyapp.demo', 'webview', dict(
    FILES, **{'index.html': '<html><body><script src="appforge-bridge.js"></script></body></html>'}))
eq(pre['app/src/main/assets/www/index.html'].count('appforge-bridge.js'), 1,
   'បើ index.html មាន script ស្រាប់ → មិនបន្ថែមទ្វេ')

print('')
if fails:
    print('FAILED %d checks' % fails)
    sys.exit(1)
print('ALL BRIDGE CHECKS PASSED')
