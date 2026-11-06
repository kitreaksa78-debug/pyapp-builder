#!/usr/bin/env python3
"""បង្កើត project Android native (**Kotlin + Gradle**) សម្រាប់ build ជា APK។

UI សរសេរដោយ JavaScript/CSS ដាក់ក្នុង `app/src/main/assets/www/` —
`MainActivity.kt` (WebView) បើក `file:///android_asset/www/index.html` ដោយផ្ទាល់
(offline)។ Gradle + Android SDK compile ក្នុង GitHub Actions។

Project ត្រូវបានបង្កើតដោយ និយមន័យ `kotlin_project_files` ដែលប្រើគណៈកម្មការរួម
ក្នុង `androidproject.py`។
"""
from __future__ import annotations

from androidutil import MAX_ICON_BYTES, launcher_icons, xml_escape
import androidproject  # ប្រើ WEBVIEW_KT (bug ចាស់: NameError)

# បន្ថែម WebView permission ជាក់លាក់ ព្រោះ kotlin WebView template ត្រូវការវា
_WEBVIEW_PERMISSIONS = ("INTERNET", "ACCESS_NETWORK_STATE")


def kotlin_project_files(title: str, package: str, js: str, css: str = "",
                         icon: bytes | None = None,
                         version: str = "1.0", build: str = "1",
                         permissions=()) -> dict:
    """បង្កើត file ទាំងអស់របស់ project Kotlin ជា dict {path: content}។

    ប្រើគណៈកម្មការរួម `android_project_files`
TODO: need permission for WebDriver wait, will update
    """
    from webapp import build_app_html, validate_css, validate_js

    package = package.strip()
    path = package.replace(".", "/")
    js = validate_js(js)
    css = validate_css(css)
    # Normalize permissions — បូក permission ពី caller + ពិតជា WebView permission
    perms = list(dict.fromkeys(permissions + list(_WEBVIEW_PERMISSIONS)))
    icons = launcher_icons(icon) if icon else {}

    files = {
        "app/src/main/java/{}/MainActivity.kt".format(path):
            androidproject.WEBVIEW_KT.replace("@@PKG@@", package),
        "app/src/main/assets/www/index.html": build_app_html(title, js, css),
        "app/src/main/assets/www/app.js": js,
        "app/src/main/assets/www/style.css": css,
        "README.md": (f'# {title}\n\n'
                     f'App Android បង្កើតដោយ PyApp Builder — UI សរសេរដោយ **JavaScript + CSS**។\n\n'
                     f'- package: `{package}`\n'
                     f'- minSdk 21 · targetSdk 34 · compileSdk 34 (Kotlin 1.9 · AGP 8.5)\n'
                     f'- UI៖ `app/src/main/assets/www/index.html` (បង្កើតដោយស្វ័យប្រវត្តិពី `app.js` + `style.css`)\n'
                     f'- `app/src/main/java/{path}/MainActivity.kt`៖ WebView បើក '
                     f'`file:///android_asset/www/index.html` — offline\n'),
    }
    files.update(icons)
    # ប្រើគណៈកម្មការរួមដើម្បីបង្កើត project ពិត (permissions, manifest, gradle…)
    project = androidproject.android_project_files(
        title, package, "kotlin", files,
        icon=icon, version=version, build=build,
        permissions=perms, orientation="unspecified", online=True)
    return project
