#!/usr/bin/env python3
"""API សម្រាប់ IDE ក្នុង browser — file validation, ZIP import/export, APK build។

Project file រក្សាទុកក្នុង browser (IndexedDB) ហើយផ្ញើមក server ពេល build/export
⇒ គ្មាន project របស់អ្នកប្រើម្នាក់លាយជាមួយអ្នកផ្សេង, ហើយ Render restart មិនបាត់កូដ។
"""
from __future__ import annotations

import base64
import io
import json
import posixpath
import re
import zipfile

from flask import Blueprint, jsonify, request, send_file, url_for

from androidproject import (MODES, ORIENTATIONS, PERMISSIONS,
                            _PERMS_MAP, android_project_files)
from androidutil import xml_escape
from languages import (all_extensions, binary_extensions, language_list,
                       monaco_map, text_extensions)
from mobileproject import (flutter_project_files, reactnative_project_files)

MAX_FILES = 300
MAX_FILE_BYTES = 5 * 1024 * 1024          # 5 MB / file
MAX_TOTAL_BYTES = 30 * 1024 * 1024        # 30 MB / project
MAX_PATH_LEN = 180

# បញ្ជី extension មកពី languages.py — ប្រភពតែមួយសម្រាប់ backend + editor។
# បន្ថែមភាសាថ្មីនៅ languages.py ហើយ API/editor ទទួលបានដោយស្វ័យប្រវត្តិ។
TEXT_EXT = text_extensions()
BINARY_EXT = binary_extensions()
ALLOWED_EXT = all_extensions()

# folder កំពូលដែលមិនត្រូវដកចេញពេល import (ជា folder ពិតរបស់ project)
_PROJECT_DIRS = {"src", "app", "res", "java", "kotlin", "assets", "www", "lib",
                 "main", "android", "ios", "web", "public", "test", "debug", "release"}

DATA_URL_RE = re.compile(r"^data:([\w.+-]+/[\w.+-]+)?;base64,(.*)$", re.DOTALL)
PKG_RE = re.compile(r"^[a-z][a-z0-9_]{1,30}(\.[a-z][a-z0-9_]{1,30}){1,3}$")

# Android project ពិត (Gradle root + Manifest) — ត្រូវ build ដូចដើម
# មិនមែន generate project ថ្មីជំនួស file របស់អ្នកប្រើ។
_ANDROID_SETTINGS = ("settings.gradle", "settings.gradle.kts")
_ANDROID_GRADLE = ("build.gradle", "build.gradle.kts")

FLUTTER_MODE = "flutter"
REACTNATIVE_MODE = "reactnative"
# mode ទាំងអស់ដែល IDE ផ្តល់ឲ្យ: Android generator + SDK ពិតដែល CI ដំឡើង (Flutter/RN)
BUILD_MODES = tuple(MODES) + (FLUTTER_MODE, REACTNATIVE_MODE)


def _base(path: str) -> str:
    return path.rsplit("/", 1)[-1]


def _manifests(files: dict) -> list[str]:
    return sorted(n for n in files if _base(n) == "AndroidManifest.xml")


def is_android_project(files: dict) -> bool:
    """True បើ file ទាំងនេះជា Android project (មាន AndroidManifest.xml)។"""
    return bool(_manifests(files))


# resource ដែល Manifest សំដៅ តែ project គ្មាន (ឧ. project គ្មាន res/ ទាល់តែសោះ)
_RES_REF_RE = re.compile(r"@(mipmap|drawable|string|style|color|xml)/([A-Za-z0-9_]+)")
_RES_DENSITIES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}


def _default_icon_png(size: int, letter: str) -> bytes:
    """Launcher icon ធម្មតា (រង្វង់មូលមួយ) ពេល project គ្មាន mipmap។"""
    import io as _io

    from PIL import Image, ImageDraw, ImageFont

    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    draw.rounded_rectangle((0, 0, size - 1, size - 1), radius=max(4, size // 5),
                           fill=(25, 118, 210, 255))
    try:
        font = ImageFont.load_default(size=int(size * 0.52))
    except TypeError:  # Pillow ចាស់
        font = ImageFont.load_default()
    box = draw.textbbox((0, 0), letter, font=font)
    draw.text(((size - (box[2] - box[0])) / 2 - box[0],
               (size - (box[3] - box[1])) / 2 - box[1]),
              letter, font=font, fill=(255, 255, 255, 255))
    buf = _io.BytesIO()
    img.save(buf, format="PNG", optimize=True)
    return buf.getvalue()


def ensure_android_resources(files: dict, title: str) -> dict:
    """បំពេញ resource ដែល Manifest សំដៅ (`@mipmap/ic_launcher`, `@style/...`…) តែគ្មាន។

    AAPT បរាជ័យភ្លាមបើ icon ឬ theme ដែល Manifest យោងមិនមាន — project ជាច្រើន
    (ឧ. ZIP ដែលមានតែ source) គ្មាន folder `res/` ទាល់តែសោះ។ យើងបង្កើតតែ file
    ដែល *ខ្វះ* ហើយមិនសរសេរជំនួស file របស់អ្នកប្រើឡើយ។
    """
    out = dict(files or {})
    letter = next((c for c in str(title or "A") if c.isalnum()), "A").upper()
    cache: dict[int, bytes] = {}
    for manifest in _manifests(out):
        text = out.get(manifest)
        if not isinstance(text, str):
            continue
        prefix = manifest[: -len("AndroidManifest.xml")]
        refs = set(_RES_REF_RE.findall(text))
        if not refs:
            continue
        existing: set[tuple[str, str]] = set()
        for path, content in out.items():
            if not path.startswith(prefix + "res/"):
                continue
            rel = path[len(prefix + "res/"):]
            folder, _, name = rel.partition("/")
            if not name or "/" in name:
                continue
            if folder.startswith("values"):
                if not isinstance(content, str):
                    continue
                for rtype in ("string", "style", "color"):
                    for m in re.finditer(r'<%s\b[^>]*\bname="([^"]+)"' % rtype, content):
                        existing.add((rtype, m.group(1)))
            elif "." in name:
                existing.add((folder.split("-")[0], name.rsplit(".", 1)[0]))
        added: dict[str, list[str]] = {}
        for rtype, name in sorted(refs - existing):
            if rtype in ("mipmap", "drawable"):
                for density, px in _RES_DENSITIES.items():
                    path = f"{prefix}res/{rtype}-{density}/{name}.png"
                    if path in out:
                        continue
                    if px not in cache:
                        cache[px] = _default_icon_png(px, letter)
                    out[path] = cache[px]
            elif rtype == "string":
                added.setdefault(f"{prefix}res/values/strings.xml", []).append(
                    f'    <string name="{name}">{xml_escape(title or "App")}</string>')
            elif rtype == "style":
                added.setdefault(f"{prefix}res/values/styles.xml", []).append(
                    f'    <style name="{name}" '
                    'parent="android:Theme.Material.Light.NoActionBar" />')
            elif rtype == "color":
                added.setdefault(f"{prefix}res/values/colors.xml", []).append(
                    f'    <color name="{name}">#FF1976D2</color>')
            # @xml/... មិនអាចប្រឌិតបាន — ទុកឲ្យ Gradle រាយកំហុសពិត
        for path, lines in added.items():
            current = out.get(path)
            body = "\n".join(lines)
            if isinstance(current, str) and "</resources>" in current:
                out[path] = current.replace("</resources>", body + "\n</resources>")
            elif isinstance(current, str):
                out[path] = current + "\n" + body + "\n"
            else:
                out[path] = ('<?xml version="1.0" encoding="utf-8"?>\n<resources>\n'
                             + body + "\n</resources>\n")
    return out


_LOCAL_CONST_RE = re.compile(r"(?m)^(\s{8,})const\s+val\s+")


def repair_android_sources(files: dict) -> dict:
    """ដោះកំហុស Kotlin ដែលឃើញញឹកញាប់បំផុត: `const val` នៅក្នុង function (Kotlin មិនអនុញ្ញាត)។

    `const` នៅ local ជា compile error ជានិច្ច — ដកចេញតែ modifier ហើយអត្ថន័យដូចដើម
    (ក្លាយជា local `val`)។ បន្ទាត់ក្នុង class/object (indent 4) មិនត្រូវប៉ះឡើយ។
    """
    out = dict(files or {})
    for path, content in list(out.items()):
        if not path.endswith(".kt") or not isinstance(content, str):
            continue
        fixed = _LOCAL_CONST_RE.sub(r"\1val ", content)
        if fixed != content:
            out[path] = fixed
    return out


# តម្លៃត្រឹមត្រូវតែមួយគត់របស់ RepositoriesMode (Gradle 6.8+)
_VALID_REPOS_MODES = ("PREFER_PROJECT", "PREFER_SETTINGS", "FAIL_ON_PROJECT_REPOSITORIES")
_REPOS_MODE_REF = re.compile(r"RepositoriesMode\s*\.\s*([A-Za-z_][A-Za-z0-9_]*)")
_REPOS_IMPORT = "import org.gradle.api.initialization.resolve.RepositoriesMode"


def repair_gradle_scripts(files: dict) -> dict:
    """ជួសជុល settings.gradle(.kts) ដែលបញ្ឈប់ Gradle មុនពេល compile។

    កំហុសពិតដែលជួបញឹកញាប់: `RepositoriesMode.FAIL_ON_PROJECT_REPOSISORIES` (សរសេរខុស)
    → "No such property … for class: RepositoriesMode" ហើយ build បរាជ័យទាំងស្រុង។
    យើងកែឈ្មោះឲ្យត្រូវ (និងបន្ថែម import ដែលខ្វះ) ដោយមិនប៉ះបន្ទាត់ផ្សេង។
    """
    out = dict(files or {})
    for path, content in list(out.items()):
        if not isinstance(content, str) or _base(path) not in _ANDROID_SETTINGS:
            continue
        fixed = _REPOS_MODE_REF.sub(
            lambda m: "RepositoriesMode." + (m.group(1) if m.group(1) in _VALID_REPOS_MODES
                                             else "FAIL_ON_PROJECT_REPOSITORIES"), content)
        if fixed == content:
            continue
        if _REPOS_IMPORT not in fixed:
            fixed = _REPOS_IMPORT + "\n" + fixed
        out[path] = fixed
    return out


def _is_react_native(files: dict) -> bool:
    """React Native/Expo project — package.json មាន dependency ពិត។"""
    for path, content in (files or {}).items():
        if not isinstance(content, str) or _base(path).lower() != "package.json":
            continue
        try:
            data = json.loads(content)
        except ValueError:
            continue
        if not isinstance(data, dict):
            continue
        deps: dict = {}
        for key in ("dependencies", "devDependencies", "peerDependencies"):
            if isinstance(data.get(key), dict):
                deps.update(data[key])
        if any(k in deps for k in ("react-native", "expo")):
            return True
    return False


def project_kind(files: dict) -> str:
    """ប្រភេទ project ដែល import ចូល → ផ្លូវ build ត្រឹមត្រូវ (គ្រប់ app ទាំងអស់)។

    gradle  — Android project ពិត (Manifest + settings.gradle) → build ដូចដើម
    android — Android project ខ្វះ file Gradle → បំពេញឲ្យ រួច build
    ndk/kotlin/java/python — source សុទ្ធ → generate host app ហើយ compile
    webview — HTML/CSS/JS → WebView app
    other   — អ្វីៗផ្សេង → WebView app (មិនបដិសេធ)
    """
    names = list(files)
    # Flutter project (pubspec.yaml) មាន android/ folder ដែរ → ត្រូវពិនិត្យមុន Manifest
    if any(_base(n).lower() in ("pubspec.yaml", "pubspec.yml") for n in names) \
            or any(n.lower().endswith(".dart") for n in names):
        return FLUTTER_MODE
    if _is_react_native(files):
        return REACTNATIVE_MODE
    if is_android_project(files):
        if any(_base(n) in _ANDROID_SETTINGS for n in names):
            return "gradle"
        return "android"
    if any(n.lower().endswith(".py") for n in names):
        return "python"
    if any(n.lower().endswith((".c", ".cc", ".cpp", ".cxx")) for n in names):
        return "ndk"
    if any(n.endswith(".kt") for n in names):
        return "kotlin"
    if any(n.endswith(".java") for n in names):
        return "java"
    if any(_base(n).lower() in ("index.html", "index.htm") for n in names):
        return "webview"
    if any(n.lower().endswith((".html", ".htm", ".css", ".js")) for n in names):
        return "webview"
    return "other"


def android_app_id(files: dict) -> str:
    """applicationId / namespace ពី build.gradle(.kts) ឬ package ពី Manifest។"""
    text = "\n".join(c for c in files.values() if isinstance(c, str))
    for pattern in (r"""applicationId\s*=?\s*["']([A-Za-z0-9_.]+)["']""",
                    r"""namespace\s*=?\s*["']([A-Za-z0-9_.]+)["']""",
                    r"""<(?:manifest|package)[^>]*\spackage\s*=\s*["']([A-Za-z0-9_.]+)["']"""):
        m = re.search(pattern, text)
        if m and PKG_RE.match(m.group(1)):
            return m.group(1)
    return ""


def _android_module(files: dict) -> tuple[str, str]:
    """(module dir, manifest path) សម្រាប់ Android project ដែល import ចូល។

    Project Android Studio ធម្មតា: app/src/main/AndroidManifest.xml → module "app"។
    """
    manifests = _manifests(files)
    if not manifests:
        return "", ""
    for path in manifests:
        if path.endswith("src/main/AndroidManifest.xml"):
            return path[: -len("src/main/AndroidManifest.xml")].rstrip("/"), path
    path = manifests[0]
    return path.rsplit("/", 1)[0], path


_SETTINGS_PLUGINS = '''pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

'''

_APP_PLUGINS_KT = '''plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
'''

_APP_PLUGINS_JAVA = '''plugins {
    id("com.android.application")
}
'''


def _app_gradle(pkg: str, kotlin: bool, dependencies: str, *, versioned: bool = False) -> str:
    """build.gradle.kts អប្បបរមាសម្រាប់ project ដែល import ចូល (គ្មាន signing config
    ព្រោះ build branch ស្អាតគ្មាន `signing/debug.p12` — debug keystore ស្វ័យប្រវត្តិ)។

    `versioned=True` ពេល file នេះជា root build ដែរ (project module តែមួយនៅ root)។
    """
    jvm = ('''
    kotlinOptions {
        jvmTarget = "17"
    }
''' if kotlin else "")
    if versioned:
        plugins = 'plugins {\n    id("com.android.application") version "8.5.2"\n'
        if kotlin:
            plugins += '    id("org.jetbrains.kotlin.android") version "1.9.24"\n'
        plugins += '}\n'
    else:
        plugins = _APP_PLUGINS_KT if kotlin else _APP_PLUGINS_JAVA
    return (plugins + '''
android {
    namespace = "%s"
    compileSdk = 34

    defaultConfig {
        applicationId = "%s"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
%s
    lint {
        abortOnError = false
    }
}
%s''' % (pkg, pkg, jvm, dependencies))


def prepare_android_project(files: dict, title: str) -> dict:
    """បំពេញ file Gradle ដែល Android project import ខ្វះ — មិនប៉ះ file របស់អ្នកប្រើ។

    Project ពី Android Studio រក្សា build.gradle ដើម ១០០%; project ដែលមានតែ source
    (ឬ ZIP ដែលបាត់ settings.gradle) ទទួលបាន wrapper អប្បបរមាដើម្បី compile បាន។
    """
    out = dict(files)
    names = set(out)
    module, manifest = _android_module(out)
    pkg = android_app_id(out) or "org.pyapp.zipbuild"
    root = re.sub(r"[^A-Za-z0-9_.-]+", "_",
                  android_title(title, out).replace(" ", "_")).strip("_") or "app"
    code = "\n".join(c for c in out.values() if isinstance(c, str))
    kotlin = any(n.endswith(".kt") for n in names)

    deps = ""
    if "AppCompatActivity" in code or "Theme.AppCompat" in code:
        deps = ('\ndependencies {\n'
                '    implementation("androidx.appcompat:appcompat:1.6.1")\n'
                '}\n')
    elif "androidx." in code:
        deps = ('\ndependencies {\n'
                '    implementation("androidx.core:core-ktx:1.13.1")\n'
                '}\n')

    has_settings = any(_base(n) in _ANDROID_SETTINGS for n in names)
    has_root_build = any(_base(n) in _ANDROID_GRADLE for n in names)

    if not has_settings:
        include = 'include(":app")\n'
        if module and module != "app":
            include += 'project(":app").projectDir = file("%s")\n' % module
        if not module:
            include = ""
        out["settings.gradle.kts"] = (_SETTINGS_PLUGINS
                                      + 'rootProject.name = "%s"\n' % root + include)

    if not module:
        # Manifest នៅ src/main → project តែមួយនៅ root (គ្មាន module :app)
        if not has_root_build:
            out["build.gradle.kts"] = _app_gradle(pkg, kotlin, deps, versioned=True)
    else:
        if not has_root_build:
            plugins = 'plugins {\n    id("com.android.application") version "8.5.2" apply false\n'
            if kotlin:
                plugins += '    id("org.jetbrains.kotlin.android") version "1.9.24" apply false\n'
            out["build.gradle.kts"] = plugins + '}\n'
        module_build = (module + "/" if module else "") + "build.gradle"
        if not any(n.startswith(module_build) for n in names):
            out[module_build + ".kts"] = _app_gradle(pkg, kotlin, deps)

    if not any(_base(n) == "gradle.properties" for n in names):
        out["gradle.properties"] = ("org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8\n"
                                   "android.useAndroidX=true\n"
                                   "android.nonTransitiveRClass=true\n")

    # Resource ដែល Manifest សំដៅ (@string/…) តែគ្មាន file → aapt2 error
    res = (module + "/" if module else "") + "src/main/res/values/strings.xml"
    manifest_text = out.get(manifest, "") if isinstance(out.get(manifest), str) else ""
    if "@string/" in manifest_text and res not in out:
        existing = "\n".join(c for k, c in out.items()
                             if k.endswith("values/strings.xml") and isinstance(c, str))
        wanted = [m for m in re.findall(r"@string/([A-Za-z0-9_]+)", manifest_text)
                  if ('name="%s"' % m) not in existing]
        if wanted:
            body = "\n".join('    <string name="%s">%s</string>'
                             % (m, xml_escape(m.replace("_", " ").title()))
                             for m in dict.fromkeys(wanted))
            out[res] = ('<?xml version="1.0" encoding="utf-8"?>\n<resources>\n'
                        + body + "\n</resources>\n")
    return out


def ensure_index_html(files: dict, title: str) -> dict:
    """WebView app ត្រូវការ index.html — បង្កើត shim បើ project ដាក់ឈ្មោះផ្សេង។"""
    out = dict(files)
    if any(_base(n).lower() in ("index.html", "index.htm") for n in out):
        return out
    pages = sorted(n for n in out if n.lower().endswith((".html", ".htm")))
    if pages:
        out["index.html"] = ('<!doctype html>\n<meta charset="utf-8">\n<title>%s</title>\n'
                             '<script>location.replace(%s);</script>\n'
                             % (xml_escape(title), json.dumps(pages[0])))
    else:
        out["index.html"] = ('<!doctype html>\n<html><head><meta charset="utf-8">\n'
                             '<meta name="viewport" content="width=device-width, initial-scale=1">\n'
                             '<title>%s</title></head><body><h1>%s</h1></body></html>\n'
                             % (xml_escape(title), xml_escape(title)))
    return out


def android_title(raw: str, files: dict) -> str:
    """ឈ្មោះ app សម្រាប់ job — សម្អាតតួអក្សរដែលធ្វើឲ្យ file/XML ខូច។"""
    title = re.sub(r'[<>:"/\\|?*\x00-\x1f]', "", str(raw or "")).strip()
    if not title:
        text = "\n".join(c for c in files.values() if isinstance(c, str))
        m = re.search(r"""rootProject\.name\s*=\s*["']([^"']+)["']""", text)
        title = (m.group(1).strip() if m else "") or "Android App"
    return title[:48] or "Android App"


class FileError(ValueError):
    """File មិនត្រឹមត្រូវ (បង្ហាញជាសារខ្មែរ)។"""


def _permission_info() -> list[dict]:
    """Build structured permission list for /api/ide/info.

    Returns list of {code, label, kind, min_sdk, max_sdk, requires_runtime, special_use, signature}
    grouped by category so the frontend can present them by groups.
    """
    out: list[dict] = []
    for code, info in _PERMS_MAP.items():
        out.append({
            "code": code,
            "label": info.get("kh", code),
            "kind": info.get("kind", "normal"),
            "min_sdk": info.get("min_sdk"),
            "max_sdk": info.get("max_sdk"),
            "requires_runtime": info.get("kind") == "dangerous",
            "special_use": info.get("special_use"),
            "signature": info.get("signature", False),
        })
    # រៀបចំតាម category ដើម្បី frontend ងាយស្រួល render
    categories: list[dict] = []
    seen_cats: set[str] = set()
    cat_order = [
        "ការភ្ជាប់", "Bluetooth", "ទីតាំង", "កាមេរ៉ា/សំឡេង",
        "Storage/Media", "ជូនដំណឹង", "ទូរស័ព្ទ/SMS",
        "ទំនាក់ទំនង/តាមម៉ោង", "Sensor/សកម្មភាព", "NFC",
        "សេវាកម្ម", "Special app access", "App installation"
    ]
    for code, info in _PERMS_MAP.items():
        kind = info.get("kind", "normal")
        if kind == "signature":
            cat = "សិទ្ធិ system (signature)"
        elif kind == "special":
            cat = "Special app access"
        elif kind == "dangerous":
            label = info.get("kh", code)
            cat = ("ទីតាំង" if "location" in code.lower() else
                   "កាមេរ៉ា/សំឡេង" if "camera" in code.lower() or "audio" in code.lower() or "microphone" in code.lower() else
                   "ទំនាក់ទំនង/តាមម៉ោង" if "contact" in code.lower() or "calendar" in code.lower() else
                   "ទូរស័ព្ទ/SMS" if any(x in code.lower() for x in ("phone", "sms", "call")) else
                   "ជូនដំណឹង" if "notification" in code.lower() else
                   "Storage/Media" if any(x in code.lower() for x in ("storage", "media")) else
                   "Bluetooth" if "bluetooth" in code.lower() or "wifi" in code.lower() else
                   "Sensor/សកម្មភាព" if any(x in code.lower() for x in ("sensor", "activity")) else
                   "ការភ្ជាប់")
        else:
            cat = "ការភ្ជាប់"
        if cat not in seen_cats:
            seen_cats.add(cat)
            categories.append({"name": cat, "perms": []})
        for c in categories:
            if c["name"] == cat:
                c["perms"].append({
                    "code": code,
                    "label": info.get("kh", code),
                    "kind": kind,
                    "min_sdk": info.get("min_sdk"),
                    "max_sdk": info.get("max_sdk"),
                    "requires_runtime": kind == "dangerous",
                    "special_use": info.get("special_use"),
                    "signature": info.get("signature", False),
                })
                break
    return {"flat": out, "categories": categories}



def _ext(name: str) -> str:
    base = name.rsplit("/", 1)[-1]
    if "." not in base:
        return base.lower() if base.startswith(".") else ""
    return base.rsplit(".", 1)[-1].lower()


def validate_path(path: str) -> str:
    path = str(path or "").replace("\\", "/").strip()
    if not path:
        raise FileError("ឈ្មោះ file ទទេ")
    if len(path) > MAX_PATH_LEN:
        raise FileError("path វែងពេក")
    if path.startswith("/") or re.match(r"^[A-Za-z]:", path):
        raise FileError("path ដាច់ខាតមិនត្រូវបានអនុញ្ញាត")
    parts = path.split("/")
    if any(p in ("", ".", "..") for p in parts):
        raise FileError("path មាន '..' ឬ '/' ខុស — មិនអនុញ្ញាត")
    if "\x00" in path:
        raise FileError("path មានតួអក្សរខុស")
    allowed = ALLOWED_EXT | {""}
    if _ext(path) not in allowed:
        raise FileError(f"ប្រភេទ file .{_ext(path)} មិនគាំទ្រ")
    return path


def decode_files(raw) -> dict:
    """{path: str|dataURL} → {path: str|bytes} ជាមួយ validation ពេញលេញ។"""
    if not isinstance(raw, dict):
        raise FileError("files ត្រូវជា object {path: content}")
    if len(raw) > MAX_FILES:
        raise FileError(f"file ច្រើនពេក (អតិបរមា {MAX_FILES})")
    files: dict = {}
    total = 0
    for raw_path, content in raw.items():
        path = validate_path(raw_path)
        if not isinstance(content, str):
            raise FileError(f"{path}: content ត្រូវជា string")
        m = DATA_URL_RE.match(content)
        if m:
            try:
                blob = base64.b64decode(m.group(2), validate=True)
            except Exception as exc:  # noqa: BLE001
                raise FileError(f"{path}: base64 ខូច") from exc
            total += len(blob)
            files[path] = blob
        else:
            blob = content.encode("utf-8")
            total += len(blob)
            files[path] = content
        if total > MAX_TOTAL_BYTES:
            raise FileError("project ធំពេក (អតិបរមា 30 MB)")
    return files


def encode_files(files: dict) -> dict:
    """bytes → data URL សម្រាប់ browser (index.html ជា text ធម្មតា)។"""
    out = {}
    for path, content in files.items():
        if isinstance(content, (bytes, bytearray)):
            ext = _ext(path) or "png"
            mime = "image/png" if ext == "png" else (
                "image/jpeg" if ext in ("jpg", "jpeg") else f"application/{ext}")
            out[path] = f"data:{mime};base64,{base64.b64encode(content).decode('ascii')}"
        else:
            out[path] = content
    return out


def build_zip(files: dict, base: str) -> io.BytesIO:
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w", zipfile.ZIP_DEFLATED) as zf:
        for path, content in files.items():
            zf.writestr(f"{base}/{path}", content)
    buf.seek(0)
    return buf


def _safe_member(name: str) -> str | None:
    """ZIP member → path សុវត្ថិភាព ឬ None (រំលង)។ បដិសេធ traversal។"""
    name = name.replace("\\", "/")
    if name.startswith("__MACOSX/") or name.endswith("/"):
        return None
    parts = [p for p in name.split("/") if p not in ("", ".")]
    if not parts or any(p == ".." for p in parts):
        return None
    path = "/".join(parts)
    # ដក folder កំពូលមួយ (project-root/) បើមាន
    if len(parts) > 1 and all(p not in ALLOWED_EXT for p in parts[:1]):
        pass
    return validate_path(path)


def import_zip(data: bytes) -> dict:
    if not data:
        raise FileError("ZIP ទទេ")
    if len(data) > 40 * 1024 * 1024:
        raise FileError("ZIP ធំពេក (អតិបរមា 40 MB)")
    try:
        zf = zipfile.ZipFile(io.BytesIO(data))
    except zipfile.BadZipFile as exc:
        raise FileError("file មិនមែន ZIP ត្រឹមត្រូវ") from exc
    out: dict = {}
    total = 0
    stripped_root = None
    names = [i.filename for i in zf.infolist() if not i.is_dir()]
    if names and all("/" in n for n in names):
        roots = {n.split("/", 1)[0] for n in names}
        if len(roots) == 1:
            only = roots.pop()
            # `src/`, `app/`, `res/`… ជា folder ពិតរបស់ project — មិនដកចេញ
            # (បើដក project បាត់ path → build ខូច)
            if only.lower() not in _PROJECT_DIRS:
                stripped_root = only
    for info in zf.infolist():
        if info.is_dir():
            continue
        name = info.filename.replace("\\", "/")
        if stripped_root and name.startswith(stripped_root + "/"):
            name = name[len(stripped_root) + 1:]
        if name.startswith("__MACOSX/"):
            continue
        try:
            path = _safe_member(name)
        except FileError:
            continue
        if not path:
            continue
        if info.file_size > MAX_FILE_BYTES:
            raise FileError(f"{path} ធំពេក (អតិបរមា 5 MB)")
        blob = zf.read(info)
        total += len(blob)
        if total > MAX_TOTAL_BYTES:
            raise FileError("ZIP ធំពេក (អតិបរមា 30 MB ពេលបើក)")
        if _ext(path) in BINARY_EXT:
            out[path] = blob
        else:
            out[path] = blob.decode("utf-8", "replace")
        if len(out) > MAX_FILES:
            raise FileError(f"file ច្រើនពេក (អតិបរមា {MAX_FILES})")
    if not out:
        raise FileError("រកមិនឃើញ file ក្នុង ZIP")
    return out


def _decode_icon(value: str, max_bytes: int) -> bytes | None:
    if not value:
        return None
    m = DATA_URL_RE.match(value) or re.match(r"^data:[\w.+-]+/[\w.+-]+;base64,(.*)$",
                                             value, re.DOTALL)
    if not m:
        raise FileError("logo ត្រូវជា data URL (PNG/JPG)")
    raw = m.group(m.lastindex)
    try:
        blob = base64.b64decode(raw, validate=True)
    except Exception as exc:  # noqa: BLE001
        raise FileError("logo base64 ខូច") from exc
    if len(blob) > max_bytes:
        raise FileError("រូប logo ធំពេក (អតិបរមា 5 MB)")
    return blob


def _logs_for(run_url: str, repo: str, token: str) -> dict:
    m = re.search(r"/actions/runs/(\d+)", run_url or "")
    if not (m and repo and token):
        return {"logs": "", "note": "មិនទាន់មាន log (run មិនទាន់ចាប់ផ្តើម)"}
    run_id = m.group(1)
    import urllib.request
    req = urllib.request.Request(
        f"https://api.github.com/repos/{repo}/actions/runs/{run_id}/logs")
    req.add_header("Authorization", "Bearer " + token)
    req.add_header("Accept", "application/vnd.github+json")
    req.add_header("User-Agent", "pyapp-builder")
    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            blob = resp.read(12 * 1024 * 1024)
    except Exception as exc:  # noqa: BLE001 — log គ្រាន់ជាព័ត៌មានបន្ថែម
        return {"logs": "", "note": f"អាន log មិនបាន: {exc}"}
    text = []
    try:
        with zipfile.ZipFile(io.BytesIO(blob)) as zf:
            for info in zf.infolist():
                if info.filename.endswith(".txt"):
                    text.append(f"===== {posixpath.basename(info.filename)} =====")
                    text.append(zf.read(info).decode("utf-8", "replace"))
    except zipfile.BadZipFile:
        text.append(blob.decode("utf-8", "replace"))
    joined = "\n".join(text)
    return {"logs": joined[-60000:], "note": ""}


# ---------------------------------------------------------------- diagnostics
_PROBLEM_PATTERNS = (
    re.compile(
        r"^(?P<sev>e|w):\s+(?:file://)?(?P<file>[^\s:]+\.(?:kt|kts|java|xml|gradle|c|cc|cpp|h|hpp))"
        r"(?::(?P<line>\d+))?(?::(?P<col>\d+))?:\s*(?P<msg>.+)$"),
    re.compile(
        r"^(?P<file>[^\s:]+\.(?:java|kt|xml|c|cc|cpp|h|hpp)):(?P<line>\d+):\s*"
        r"(?P<sev>error|warning):\s*(?P<msg>.+)$"),
    re.compile(r"^(?P<sev>error|warning):\s*(?P<msg>.+)$"),
)
_SEVERITY = {"e": "error", "w": "warning", "error": "error", "warning": "warning"}
MAX_PROBLEMS = 200
_SRC_MARKERS = ("app/src/main/", "src/main/", "/src/")


def _short_path(path: str) -> str:
    """`/home/runner/work/app/app/src/main/java/…/MainActivity.kt` → path ក្នុង project។"""
    for marker in _SRC_MARKERS:
        if marker in path:
            return path.split(marker, 1)[1]
    return path.rsplit("/", 1)[-1]


def _gradle_config_problems(logs: str) -> list[dict]:
    """Gradle បរាជ័យពេលអាន script (settings/build.gradle) → `* What went wrong:` + `>`។

    បញ្ហាប្រភេទនេះគ្មាន file:line ក្នុងទម្រង់ `e:` ធម្មតា ដូច្នេះ parse_problems មិនឃើញ
    ហើយ UI បង្ហាញតែ «Build បរាជ័យ»។ នៅទីនេះយកសារ Gradle ពិត + file/line ដែលវាបញ្ជាក់។
    """
    text = (logs or "").replace("\r", "")
    if "What went wrong:" not in text:
        return []
    lines = [ln.strip() for ln in text.split("\n")]
    found: list[dict] = []
    for i, line in enumerate(lines):
        if not line.endswith("What went wrong:"):
            continue
        body: list[str] = []
        for nxt in lines[i + 1:]:
            if not nxt:
                if body:
                    break
                continue
            if nxt.startswith(("* ", "BUILD ", "##[")):
                break
            body.append(nxt.lstrip("> ").strip())
        msg = " ".join(body).strip()
        if msg:
            found.append({"file": "", "line": 0, "severity": "error",
                          "message": msg[:600]})
    m = re.search(r"(?:Settings|Build) file '([^']+)' line: (\d+)", text)
    if found and m:
        found[0]["file"] = _short_path(m.group(1))
        found[0]["line"] = int(m.group(2))
    return found


def parse_problems(logs: str) -> list[dict]:
    """បំលែង log របស់ Gradle/Kotlin/Java/CMake → Problems ដែលចុចទៅ file បាន។

    មិនបង្កើត problem ក្លែងក្លាយទេ — តែ error/warning ដែលមានក្នុង log ពិតប៉ុណ្ណោះ។
    """
    out: list[dict] = []
    seen: set[tuple] = set()
    for raw in (logs or "").replace("\r", "").split("\n"):
        line = raw.strip()
        if not line or len(line) > 800:
            continue
        for pattern in _PROBLEM_PATTERNS:
            m = pattern.match(line)
            if not m:
                continue
            gd = m.groupdict()
            msg = (gd.get("msg") or "").strip()
            if not msg or msg.startswith("BUILD "):
                continue
            severity = _SEVERITY.get((gd.get("sev") or "error").lower(), "error")
            file = _short_path(gd.get("file") or "")
            try:
                lineno = int(gd.get("line") or 0)
            except (TypeError, ValueError):
                lineno = 0
            key = (file, lineno, msg)
            if key in seen:
                break
            seen.add(key)
            out.append({"file": file, "line": lineno, "severity": severity,
                        "message": msg})
            break
        if len(out) >= MAX_PROBLEMS:
            break
    for problem in _gradle_config_problems(logs):
        key = (problem["file"], problem["line"], problem["message"])
        if key in seen:
            continue
        seen.add(key)
        out.append(problem)
    return out[:MAX_PROBLEMS]


def create_blueprint(create_job, validate_common, repo: str, token: str,
                     zip_workflow: str = "build-zip-apk.yml"):
    bp = Blueprint("ide", __name__)

    @bp.get("/api/ide/info")
    def ide_info():
        return jsonify({
            "modes": list(BUILD_MODES),
            "permissions": _permission_info(),
            "permission_categories": True,
            "orientations": list(ORIENTATIONS),
            "limits": {"files": MAX_FILES, "file_bytes": MAX_FILE_BYTES,
                       "total_bytes": MAX_TOTAL_BYTES},
            "extensions": sorted(ALLOWED_EXT),
            "languages": language_list(),
            "monaco": monaco_map(),
        })

    @bp.post("/api/ide/build")
    def ide_build():
        data = request.get_json(silent=True) or {}
        try:
            files = decode_files(data.get("files") or {})
            kind = project_kind(files)
            requested = str(data.get("mode") or "").strip().lower()
            native_kind = (requested if requested in (FLUTTER_MODE, REACTNATIVE_MODE)
                           else kind if kind in (FLUTTER_MODE, REACTNATIVE_MODE) else "")
            if native_kind:
                # Flutter/React Native — CI ដំឡើង SDK ពិត ហើយ compile APK ពិត
                title = android_title(data.get("title"), files)
                raw_pkg = str(data.get("package") or "").strip()
                package = android_app_id(files) or raw_pkg
                if not PKG_RE.match(package or ""):
                    package = ("org.pyapp.flutterapp" if native_kind == FLUTTER_MODE
                               else "org.pyapp.rnapp")
                version = str(data.get("version") or "1.0").strip()
                if not re.match(r"^[0-9]{1,3}(\.[0-9]{1,3}){0,3}$", version):
                    version = "1.0"
                build_no = str(data.get("build") or "1").strip()
                if not (build_no.isdigit() and 0 < int(build_no) <= 999_999):
                    build_no = "1"
                artifact = str(data.get("artifact") or "apk").strip().lower()
                if artifact not in ("apk", "aab", "both"):
                    artifact = "apk"
                maker = (flutter_project_files if native_kind == FLUTTER_MODE
                         else reactnative_project_files)
                project = maker(title, package, files, version=version,
                                build=build_no, artifact=artifact)
                mode = native_kind
                meta = create_job(title, package, project, artifact=artifact,
                                  kind=native_kind, clean=True)
            elif kind in ("gradle", "android"):
                # Android project ដែល import ចូល → compile ដូចដើម (Gradle + Manifest)
                title = android_title(data.get("title"), files)
                package = android_app_id(files) or "org.pyapp.zipbuild"
                mode = "android"
                artifact = "apk"
                # បំពេញ file Gradle ដែលខ្វះ (project ពី Android Studio មិនប៉ះឡើយ)
                project = files if kind == "gradle" else prepare_android_project(files, title)
                # icon/theme ដែល Manifest យោង តែគ្មាន → AAPT បរាជ័យ; បង្កើតឲ្យតែខ្វះ
                project = ensure_android_resources(project, title)
                project = repair_android_sources(project)
                # settings.gradle ដែលសរសេរខុស (ឧ. FAIL_ON_PROJECT_REPOSISORIES) → build មិនរត់
                project = repair_gradle_scripts(project)
                meta = create_job(title, package, project, artifact=artifact,
                                  kind="zip", workflow=zip_workflow, clean=True)
            else:
                title, package = validate_common(data.get("title", ""),
                                                 data.get("package", ""))
                mode = str(data.get("mode") or "").lower()
                if mode not in MODES:
                    # "auto" ឬតម្លៃមិនស្គាល់ → ជ្រើសពី file ដែលមាន
                    mode = kind if kind in MODES else "webview"
                version = str(data.get("version") or "1.0").strip()
                if not re.match(r"^[0-9]{1,3}(\.[0-9]{1,3}){0,3}$", version):
                    version = "1.0"
                artifact = str(data.get("artifact") or "apk").strip().lower()
                if artifact not in ("apk", "aab", "both"):
                    artifact = "apk"
                build_no = str(data.get("build") or "1").strip()
                if not (build_no.isdigit() and 0 < int(build_no) <= 999_999):
                    build_no = "1"
                perms = data.get("permissions") or []
                if not isinstance(perms, list):
                    perms = []
                icon = _decode_icon(str(data.get("icon") or ""), MAX_FILE_BYTES)
                if mode == "webview":
                    # គ្រប់ web project (ឈ្មោះ index ផ្សេង ឬគ្មាន HTML) → build បានជានិច្ច
                    files = ensure_index_html(files, title)
                project = android_project_files(
                    title, package, mode, files, icon=icon, version=version,
                    build=build_no, permissions=[str(p) for p in perms],
                    orientation=str(data.get("orientation") or "unspecified"),
                    online=bool(data.get("online", True)))
                # clean=True → branch ចាប់ផ្តើមពី tree ស្អាត (តែ .github/ + signing/)
                # ដើម្បីកុំឲ្យ source ចាស់ពី repo លាយចូល project ថ្មី (compile បរាជ័យ)
                meta = create_job(title, package, project, artifact=artifact, clean=True)
        except (FileError, ValueError) as exc:
            return jsonify({"error": str(exc)}), 400
        return jsonify({
            "id": meta["id"], "status": meta["status"], "error": meta["error"],
            "title": meta["title"], "mode": mode, "artifact": artifact,
            "job_url": url_for("job_page", job_id=meta["id"]),
            "status_url": url_for("job_status", job_id=meta["id"]),
            "download_url": url_for("download", job_id=meta["id"]),
            "download_aab_url": url_for("download", job_id=meta["id"], type="aab"),
        }), 201

    @bp.post("/api/ide/zip")
    def ide_zip():
        data = request.get_json(silent=True) or {}
        try:
            files = decode_files(data.get("files") or {})
        except FileError as exc:
            return jsonify({"error": str(exc)}), 400
        name = re.sub(r"[^a-zA-Z0-9_-]+", "-", str(data.get("name") or "project")).strip("-")
        buf = build_zip(files, name or "project")
        return send_file(buf, mimetype="application/zip", as_attachment=True,
                         download_name=f"{name or 'project'}.zip")

    @bp.post("/api/ide/import")
    def ide_import():
        upload = request.files.get("file")
        if upload is None:
            return jsonify({"error": "គ្មាន file ZIP"}), 400
        try:
            files = import_zip(upload.read(40 * 1024 * 1024 + 1))
        except FileError as exc:
            return jsonify({"error": str(exc)}), 400
        return jsonify({"files": encode_files(files), "count": len(files)})

    @bp.get("/api/ide/builds")
    def ide_builds():
        from app import JOBS  # noqa: WPS433 — ចៀសវាង circular import
        items = []
        for d in JOBS.iterdir():
            f = d / "meta.json"
            if not f.is_file():
                continue
            try:
                m = json.loads(f.read_text(encoding="utf-8"))
            except (OSError, ValueError):
                continue
            items.append({k: m.get(k) for k in ("id", "title", "package", "status",
                                                 "created", "error", "run_url")})
        items.sort(key=lambda x: x.get("created") or 0, reverse=True)
        return jsonify({"builds": items[:20]})

    @bp.get("/api/jobs/<job_id>/logs")
    def job_logs(job_id: str):
        from app import load_job  # noqa: WPS433 — ចៀសវាង circular import ពេលចាប់ផ្តើម
        meta = load_job(job_id)
        result = _logs_for(meta.get("run_url", ""), repo, token)
        result["problems"] = parse_problems(result.get("logs", ""))
        return jsonify(result)

    return bp
