#!/usr/bin/env python3
"""បង្កើត app Android ពី **JavaScript + CSS**។

អ្នកប្រើសរសេរ UI ដោយ JavaScript (DOM ធម្មតា) — server វេចខ្ចប់វាជា
`Assets/www/index.html` ហើយ MainActivity (C# WebView) បើក file នោះ
ដោយផ្ទាល់ (offline, គ្មាន internet)។ ផ្ទាំង preview ក្នុងសាយប្រើ
HTML ដូចគ្នាបេះបិទ ⇒ អ្វីដែលឃើញគឺជា app ពិត។
"""
from __future__ import annotations

from javaapp import launcher_icons, xml_escape

from csapp import (CSPROJ, GITIGNORE, MANIFEST, STRINGS)

MAX_JS = 120_000
MAX_CSS = 60_000

DEFAULT_JS_SOURCE = '''// app.js — UI របស់ app សរសេរដោយ JavaScript
// បង្ហាញក្នុង <div id="app"> — ដំណើរការក្នុង WebView ដូច browser ធម្មតា។

const app = document.getElementById('app');

app.innerHTML = `
  <header class="hero">
    <span class="badge">JS</span>
    <h1>សួស្តី 👋</h1>
    <p>app នេះបង្កើតដោយ JavaScript</p>
  </header>

  <main class="body">
    <section class="card">
      <h2>សាកល្បងចុច</h2>
      <p>អ្នកបានចុច <b id="count">0</b> ដង</p>
      <button id="tap" class="btn">ចុចទីនេះ</button>
    </section>

    <section class="card">
      <h2>បញ្ជីការងារ</h2>
      <ul id="list" class="list">
        <li>សរសេរ UI ដោយ JS</li>
        <li>មើលផ្ទាំង preview</li>
        <li>ចុច Build APK</li>
      </ul>
    </section>
  </main>

  <nav class="tabs">
    <span class="on">ដើម</span><span>ស្វែងរក</span><span>ខ្ញុំ</span>
  </nav>
`;

let count = 0;
const counter = document.getElementById('count');

document.getElementById('tap').addEventListener('click', () => {
  count += 1;
  counter.textContent = count;
  document.getElementById('list').insertAdjacentHTML(
    'beforeend',
    '<li>ចុចលើកទី ' + count + '</li>'
  );
});
'''

DEFAULT_CSS_SOURCE = '''/* style.css — រចនាប័ទ្ម app (CSS ធម្មតា) */
:root {
  --bg: #f4f6fb;
  --card: #ffffff;
  --fg: #131a26;
  --dim: #66718a;
  --accent: #2f6df6;
  --line: #e3e8f2;
}

* { box-sizing: border-box; -webkit-tap-highlight-color: transparent; }

html { font-size: 17px; }

html, body {
  margin: 0;
  height: 100%;
  background: var(--bg);
  color: var(--fg);
  font-family: system-ui, -apple-system, "Noto Sans Khmer", Roboto, sans-serif;
  font-size: 1rem;
  line-height: 1.55;
}

#app { display: flex; flex-direction: column; min-height: 100%; }

.hero {
  background: linear-gradient(135deg, var(--accent), #6f9bff);
  color: #fff;
  padding: 22px 18px 26px;
  border-radius: 0 0 26px 26px;
}
.hero h1 { margin: 10px 0 4px; font-size: 30px; }
.hero p { margin: 0; opacity: .92; font-size: 16px; }
.badge {
  display: inline-block; font-size: 12px; font-weight: 700;
  background: rgba(255,255,255,.22); padding: 5px 11px; border-radius: 999px;
}

.body { flex: 1; padding: 16px; display: flex; flex-direction: column; gap: 14px; }

.card {
  background: var(--card);
  border: 1px solid var(--line);
  border-radius: 16px;
  padding: 16px;
  box-shadow: 0 6px 18px rgba(19,26,38,.05);
}
.card h2 { margin: 0 0 8px; font-size: 19px; }
.card p { margin: 0 0 12px; color: var(--dim); font-size: 16px; }
.card p b { color: var(--accent); font-size: 20px; }

.btn {
  width: 100%;
  border: 0;
  border-radius: 12px;
  background: var(--accent);
  color: #fff;
  font-size: 17px;
  font-weight: 600;
  padding: 15px 16px;
}
.btn:active { transform: scale(.985); }

.list { margin: 0; padding-left: 20px; color: var(--fg); font-size: 16px; }
.list li { margin: 6px 0; }

.tabs {
  display: flex;
  justify-content: space-around;
  border-top: 1px solid var(--line);
  background: var(--card);
  padding: 13px 0;
  font-size: 15px;
  color: var(--dim);
}
.tabs .on { color: var(--accent); font-weight: 700; }
'''

BASE_CSS = '''*{box-sizing:border-box}
html,body{margin:0;height:100%}
#app{min-height:100%}
#js-error{
  position:fixed;inset:auto 12px 12px 12px;z-index:9999;
  background:#2b1013;color:#ffd7d2;border:1px solid #ff6b5e;border-radius:12px;
  padding:12px 14px;font:13px/1.5 ui-monospace,Menlo,monospace;white-space:pre-wrap;
  max-height:55%;overflow:auto;display:none;
}
'''


class WebCodeError(ValueError):
    """កូដ JS/CSS មិនត្រឹមត្រូវ (បង្ហាញជាសារខ្មែរ)។"""


def validate_js(code: str) -> str:
    code = (code or "").replace("\r\n", "\n")
    if not code.strip():
        raise ValueError("កូដ JavaScript ទទួល — សរសេរ UI ក្នុង app.js")
    if len(code) > MAX_JS:
        raise ValueError("កូដ JavaScript វែងពេក (អតិបរមា 120,000 តួ)")
    return code


def validate_css(code: str) -> str:
    code = (code or "").replace("\r\n", "\n")
    if len(code) > MAX_CSS:
        raise ValueError("កូដ CSS វែងពេក (អតិបរមា 60,000 តួ)")
    return code


def _script_safe(code: str) -> str:
    """ការពារ `</script>` ក្នុងកូដអ្នកប្រើ មិនឱ្យបិទ tag មុនកាល។"""
    return code.replace("</script", "<\\/script")


def _style_safe(code: str) -> str:
    return code.replace("</style", "<\\/style")


def build_app_html(title: str, js: str, css: str = "", dark: bool = False) -> str:
    """HTML តែមួយ — ប្រើទាំងផ្ទាំង preview និង `Assets/www/index.html` ក្នុង APK។"""
    safe_title = xml_escape(title or "App")
    error_hook = '''<div id="js-error"></div>
<script>
(function () {
  var box = document.getElementById('js-error');
  function show(msg) {
    box.style.display = 'block';
    box.textContent = 'JavaScript error:\\n' + msg;
  }
  window.addEventListener('error', function (e) {
    show((e.message || 'unknown error') + (e.lineno ? ' (line ' + e.lineno + ')' : ''));
  });
  window.addEventListener('unhandledrejection', function (e) {
    show(String(e.reason && e.reason.message || e.reason || 'promise rejected'));
  });
})();
</script>'''
    return (
        '<!doctype html>\n<html lang="km">\n<head>\n<meta charset="utf-8">\n'
        '<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">\n'
        '<meta name="color-scheme" content="light dark">\n'
        f'<title>{safe_title}</title>\n'
        f'<style>{BASE_CSS}{_style_safe(css)}</style>\n'
        '</head>\n<body>\n'
        '<div id="app"></div>\n'
        f'{error_hook}\n'
        f'<script>\ntry {{\n{_script_safe(js)}\n}} catch (err) {{\n'
        "  var b = document.getElementById('js-error');\n"
        "  b.style.display = 'block';\n"
        "  b.textContent = 'JavaScript error:\\n' + (err && err.message || err);\n"
        '}\n</script>\n'
        '</body>\n</html>\n'
    )


WEB_ACTIVITY = '''using Android.App;
using Android.Graphics;
using Android.OS;
using Android.Views;
using Android.Webkit;

namespace PyApp
{
    /// <summary>
    /// App Android សាងសង់ដោយ JavaScript — WebView បើក UI ក្នុង
    /// Assets/www/index.html (offline, មិនត្រូវការ internet)។
    /// </summary>
    [Activity(Label = "@string/app_name", MainLauncher = true, Exported = true,
              Theme = "@android:style/Theme.DeviceDefault.Light.NoActionBar")]
    public class MainActivity : Activity
    {
        private WebView _web;

        protected override void OnCreate(Bundle savedInstanceState)
        {
            base.OnCreate(savedInstanceState);

            var bar = Window;                       // status bar ច្បាស់លើ app ភ្លឺ
            bar.SetStatusBarColor(Color.ParseColor("#FFFFFF"));
            bar.SetNavigationBarColor(Color.ParseColor("#FFFFFF"));

            _web = new WebView(this);
            WebSettings settings = _web.Settings;
            settings.JavaScriptEnabled = true;
            settings.DomStorageEnabled = true;
            settings.AllowFileAccess = true;
            settings.SetSupportZoom(false);
            settings.BuiltInZoomControls = false;
            settings.MediaPlaybackRequiresUserGesture = false;

            _web.SetWebViewClient(new WebViewClient());
            _web.SetWebChromeClient(new WebChromeClient());
            _web.LoadUrl("file:///android_asset/www/index.html");

            SetContentView(_web);
        }

        public override void OnBackPressed()
        {
            if (_web != null && _web.CanGoBack())
            {
                _web.GoBack();
                return;
            }
            base.OnBackPressed();
        }
    }
}
'''

WEB_README = '''# @@TITLE@@ (JavaScript · WebView)

App Android បង្កើតដោយ PyApp Builder — UI សរសេរដោយ **JavaScript + CSS**។

- package: `@@PKG@@`
- UI៖ `Assets/www/index.html` (បង្កើតដោយស្វ័យប្រវត្តិពី `app.js` + `style.css`)
- `MainActivity.cs`៖ WebView បើក `file:///android_asset/www/index.html` — offline

## កែ UI

កែកូដ JavaScript/CSS ក្នុងទំព័រ builder ហើយ Build ម្តងទៀត។

## Build APK ដោយខ្លួនឯង

```bash
dotnet workload install android
dotnet publish -f net10.0-android -c Release -p:AndroidPackageFormat=apk
# APK: bin/Release/net10.0-android/publish/*-Signed.apk
```
'''


def web_project_files(title: str, package: str, js: str, css: str = "",
                      icon: bytes | None = None,
                      version: str = "1.0", build: str = "1") -> dict:
    """Project .NET Android ដែលដាក់ JavaScript app ជា asset ក្នុង APK។"""
    package = package.strip()
    js = validate_js(js)
    css = validate_css(css)
    icons = launcher_icons(icon) if icon else {}

    icon_attr = ""
    if icons:
        icon_attr = ('        android:icon="@mipmap/ic_launcher"\n'
                     '        android:roundIcon="@mipmap/ic_launcher_round"\n')

    files = {
        "App.csproj": (CSPROJ.replace("@@PKG@@", package)
                       .replace("@@VERSION@@", xml_escape(version))
                       .replace("@@BUILD@@", str(build))),
        "AndroidManifest.xml": (MANIFEST.replace("@@PKG@@", package)
                                .replace("@@ICON@@", icon_attr)),
        "MainActivity.cs": WEB_ACTIVITY,
        "Assets/www/index.html": build_app_html(title, js, css),
        "Assets/www/app.js": js,
        "Assets/www/style.css": css,
        "Resources/values/strings.xml": STRINGS.replace("@@TITLE@@", xml_escape(title)),
        ".gitignore": GITIGNORE,
        "README.md": (WEB_README.replace("@@TITLE@@", title)
                      .replace("@@PKG@@", package)),
    }
    for path, blob in icons.items():
        files[path.replace("app/src/main/res/", "Resources/")] = blob
    return files
