#!/usr/bin/env python3
"""បង្កើត Android Gradle project ពី file របស់ IDE (WebView / Kotlin / Java)។

- `webview` : file ទាំងអស់ → `app/src/main/assets/www/` ហើយ WebView បើក index.html
- `kotlin`  : `*.kt` របស់អ្នកប្រើ → compile ដោយ Kotlin (Gradle) ជា app native
- `java`    : `*.java` របស់អ្នកប្រើ → compile ដោយ Java (Gradle) ជា app native

Gradle + Android SDK compile ក្នុង GitHub Actions (IP ថ្ងៃនេះមិន build ក្នុង server)។
"""
from __future__ import annotations

import re
import xml.etree.ElementTree as ET

from androidutil import MAX_ICON_BYTES, launcher_icons, xml_escape

MODES = ("webview", "kotlin", "java")

# permission ដែលអនុញ្ញាត (ឈ្មោះ Android ដើម) → ពិពណ៌នាខ្មែរ
PERMISSIONS = {
    "INTERNET": "អ៊ីនធឺណិត",
    "ACCESS_NETWORK_STATE": "ស្ថានភាព network",
    "VIBRATE": "រំញ័រ",
    "CAMERA": "កាមេរ៉ា",
    "RECORD_AUDIO": "ថតសំឡេង",
    "ACCESS_FINE_LOCATION": "ទីតាំង GPS",
    "ACCESS_COARSE_LOCATION": "ទីតាំង (rough)",
    "READ_EXTERNAL_STORAGE": "អាន file",
    "WRITE_EXTERNAL_STORAGE": "សរសេរ file",
    "POST_NOTIFICATIONS": "ជូនដំណឹង",
    "WAKE_LOCK": "បើកអេក្រង់",
    "FLASHLIGHT": "ពិល",
    "SYSTEM_ALERT_WINDOW": "បង្ហាញលើ app ផ្សេង",
    "FOREGROUND_SERVICE": "service ខាងក្រោយ",
    "FOREGROUND_SERVICE_SPECIAL_USE": "service ប្រភេទពិសេស",
}
ORIENTATIONS = {"unspecified": "", "portrait": "portrait", "landscape": "landscape",
                "sensor": "sensor"}

WEBVIEW_KT = '''package @@PKG@@

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

/** WebView បើក UI ក្នុង app/src/main/assets/www/index.html (offline)។ */
class MainActivity : Activity() {
    private lateinit var web: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.WHITE
        window.navigationBarColor = Color.WHITE
        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.mediaPlaybackRequiresUserGesture = false
            settings.setSupportZoom(false)
            webViewClient = WebViewClient()
            loadUrl("file:///android_asset/www/index.html")
        }
        setContentView(web)
    }

    override fun onDestroy() {
        if (::web.isInitialized) web.destroy()
        super.onDestroy()
    }
}
'''

WEBVIEW_JAVA = '''package @@PKG@@;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/** WebView បើក UI ក្នុង app/src/main/assets/www/index.html (offline)។ */
public class MainActivity extends Activity {
    private WebView web;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.WHITE);
        getWindow().setNavigationBarColor(Color.WHITE);
        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setMediaPlaybackRequiresUserGesture(false);
        web.setWebViewClient(new WebViewClient());
        web.loadUrl("file:///android_asset/www/index.html");
        setContentView(web);
    }

    @Override
    protected void onDestroy() {
        if (web != null) web.destroy();
        super.onDestroy();
    }
}
'''

DEFAULT_NATIVE_KT = '''package @@PKG@@

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.widget.EditText

/**
 * App native Kotlin (Android Views) — គ្មាន WebView។
 * សរសេរកូដនៅទីនេះ ហើយ Build APK ដើម្បីទទួល APK ពិត។
 */
class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pad = (20 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(pad, pad * 2, pad, pad)
            setBackgroundColor(Color.parseColor("#0D1117"))
        }

        val title = TextView(this).apply {
            text = "សួស្តី 👋"
            textSize = 26f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }

        val hint = TextView(this).apply {
            text = "app នេះសរសេរដោយ Kotlin"
            textSize = 15f
            setTextColor(Color.parseColor("#8B949E"))
            gravity = Gravity.CENTER
            setPadding(0, pad / 2, 0, pad)
        }

        val name = EditText(this).apply {
            hint = "ឈ្មោះរបស់អ្នក"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#8B949E"))
        }

        val btn = Button(this).apply {
            text = "ចុចទីនេះ"
            setOnClickListener {
                val who = name.text.toString().trim()
                title.text = if (who.isEmpty()) "បានចុច 🎉" else "សួស្តី $who 🎉"
                Toast.makeText(this@MainActivity, "Kotlin + Android", Toast.LENGTH_SHORT).show()
            }
        }

        root.addView(title)
        root.addView(hint)
        root.addView(name, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        root.addView(btn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        setContentView(root)
    }
}
'''

DEFAULT_NATIVE_JAVA = '''package @@PKG@@;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** App native Java (Android Views) — គ្មាន WebView។ */
public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        float d = getResources().getDisplayMetrics().density;
        int pad = (int) (20 * d);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(pad, pad * 2, pad, pad);
        root.setBackgroundColor(Color.parseColor("#0D1117"));

        final TextView title = new TextView(this);
        title.setText("សួស្តី 👋");
        title.setTextSize(26f);
        title.setTextColor(Color.WHITE);
        title.setGravity(Gravity.CENTER);

        TextView hint = new TextView(this);
        hint.setText("app នេះសរសេរដោយ Java");
        hint.setTextSize(15f);
        hint.setTextColor(Color.parseColor("#8B949E"));
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, pad / 2, 0, pad);

        final EditText name = new EditText(this);
        name.setHint("ឈ្មោះរបស់អ្នក");
        name.setTextColor(Color.WHITE);
        name.setHintTextColor(Color.parseColor("#8B949E"));

        Button btn = new Button(this);
        btn.setText("ចុចទីនេះ");
        btn.setOnClickListener(v -> {
            String who = name.getText().toString().trim();
            title.setText(who.isEmpty() ? "បានចុច 🎉" : "សួស្តី " + who + " 🎉");
            Toast.makeText(MainActivity.this, "Java + Android", Toast.LENGTH_SHORT).show();
        });

        root.addView(title);
        root.addView(hint);
        root.addView(name, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(btn, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        setContentView(root);
    }
}
'''

MANIFEST = '''<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

@@PERMISSIONS@@
    <application
@@ICON@@        android:allowBackup="true"
        android:label="@string/app_name"
        android:supportsRtl="true"
@@CLEARTEXT@@        android:theme="@android:style/Theme.Material.NoActionBar">
        <activity
            android:name=".MainActivity"
            android:exported="true"
@@ORIENTATION@@            android:theme="@android:style/Theme.Material.NoActionBar">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
@@APP_EXTRA@@    </application>

</manifest>
'''

ROOT_GRADLE = '''plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
}
'''

SETTINGS_GRADLE = '''pluginManagement {
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

rootProject.name = "@@ROOT@@"
include(":app")
'''

GRADLE_PROPS = '''org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=false
android.nonTransitiveRClass=true
kotlin.code.style=official
'''

APP_GRADLE = '''plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "@@PKG@@"
    compileSdk = 34

    defaultConfig {
        applicationId = "@@PKG@@"
        minSdk = 21
        targetSdk = 34
        versionCode = @@BUILD@@
        versionName = "@@VERSION@@"
    }

    signingConfigs {
        create("stable") {
            storeFile = file("../signing/debug.p12")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
            storeType = "PKCS12"
        }
    }

    buildTypes {
        getByName("debug") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("stable")
        }
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("stable")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    lint {
        abortOnError = false
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = "17"
    }
}
'''

STRINGS = '''<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">@@TITLE@@</string>
</resources>
'''

GITIGNORE = '''.gradle/
build/
app/build/
local.properties
out/
*.iml
.idea/
'''

PACKAGE_RE = re.compile(r"^[ \t]*package[ \t]+([A-Za-z_][\w.]*)[ \t]*;?[ \t]*$", re.MULTILINE)
MAIN_CLASS_RE = re.compile(r"\bclass\s+MainActivity\b")


ET.register_namespace("android", "http://schemas.android.com/apk/res/android")
ANDROID_NS = "{http://schemas.android.com/apk/res/android}"
# component ដែលអនុញ្ញាតឲ្យ project ប្រកាសខ្លួនឯងក្នុង <application>
APP_EXTRA_TAGS = {"service", "receiver", "provider", "meta-data", "activity-alias"}


def _user_manifest(files: dict) -> tuple[list[str], str]:
    """អាន `AndroidManifest.xml` របស់ project → (permissions, application children)។

    IDE បង្កើត manifest ដោយស្វ័យប្រវត្តិ ដូច្នេះ project ដែលត្រូវការ `<service>`
    (ឧ. overlay) ឬ permission ពិសេស អាចដាក់ manifest ខ្លួនឯងក្នុង project ហើយ
    builder បញ្ចូលតែអ្វីដែលចាំបាច់ — label/icon/launcher នៅដើមដដែល។
    """
    text = None
    for path in sorted(files, key=len):
        if path.rsplit("/", 1)[-1] == "AndroidManifest.xml":
            content = files[path]
            if isinstance(content, str) and "<manifest" in content:
                text = content
                break
    if text is None:
        return [], ""
    try:
        root = ET.fromstring(text)
    except ET.ParseError as exc:
        raise ValueError(f"AndroidManifest.xml ខូច: {exc}") from exc
    if root.tag != "manifest":
        raise ValueError("AndroidManifest.xml ត្រូវមាន root <manifest>")

    perms = []
    for el in root:
        if el.tag in ("uses-permission", "uses-permission-sdk-23"):
            name = el.get(ANDROID_NS + "name")
            if name:
                perms.append(name)

    blocks = []
    app = root.find("application")
    if app is not None:
        for el in app:
            if el.tag not in APP_EXTRA_TAGS:
                continue
            name = el.get(ANDROID_NS + "name") or ""
            # launcher activity ត្រូវបានបង្កើតដោយ builder — មិនបញ្ចូលម្តងទៀត
            if el.tag.startswith("activity") and name.endswith("MainActivity"):
                continue
            ET.indent(el, space="    ")
            xml = ET.tostring(el, encoding="unicode").strip()
            xml = xml.replace(' xmlns:android="http://schemas.android.com/apk/res/android"', "")
            blocks.append("\n".join("        " + ln for ln in xml.splitlines()))
    return perms, ("\n".join(blocks) + "\n" if blocks else "")


def _permission_blocks(perms, online: bool, extra=()) -> str:
    names = []
    if online:
        names.append("android.permission.INTERNET")
    # app ដែលបិទ network មិនត្រូវការ permission នេះទេ — បញ្ចូលតែពេលត្រូវការ
    if online or "ACCESS_NETWORK_STATE" in perms:
        names.append("android.permission.ACCESS_NETWORK_STATE")
    for name in perms:
        if name != "INTERNET" and name in PERMISSIONS:
            names.append(f"android.permission.{name}")
    names.extend(n for n in extra if n)
    seen, lines = set(), []
    for name in names:
        if name in seen:
            continue
        seen.add(name)
        lines.append(f'    <uses-permission android:name="{name}" />')
    return "\n".join(lines) + "\n"


def _source_package(source: str, default: str) -> str:
    m = PACKAGE_RE.search(source)
    return m.group(1) if m else default


def android_project_files(title: str, package: str, mode: str,
                          files: dict, *, icon: bytes | None = None,
                          version: str = "1.0", build: str = "1",
                          permissions=(), orientation: str = "unspecified",
                          online: bool = True, name_prefix: str = "") -> dict:
    """បង្កើត file ទាំងអស់របស់ Android project ជា dict {path: bytes|str}។"""
    package = package.strip()
    mode = (mode or "webview").strip().lower()
    if mode not in MODES:
        raise ValueError("mode មិនត្រឹមត្រូវ — ត្រូវជា webview, kotlin ឬ java")
    if not re.match(r"^[a-z][a-z0-9_]{1,30}(\.[a-z][a-z0-9_]{1,30}){1,3}$", package):
        raise ValueError("package មិនត្រឹមត្រូវ — ឧ. org.pyapp.demo")
    orientation = (orientation or "unspecified").strip().lower()
    if orientation not in ORIENTATIONS:
        orientation = "unspecified"
    permissions = [p for p in (permissions or ()) if p in PERMISSIONS]
    icons = launcher_icons(icon) if icon else {}
    manifest_perms, app_extra = _user_manifest(files or {})

    path = package.replace(".", "/")
    src = {}
    for name, content in (files or {}).items():
        if isinstance(content, (bytes, bytearray)):
            continue
        src[name] = content

    if mode == "webview":
        html = next((c for n, c in src.items() if n.endswith("index.html")), None)
        if html is None:
            raise ValueError("រកមិនឃើញ index.html — WebView app ត្រូវការ index.html")
        sources = {f"app/src/main/java/{path}/MainActivity.kt":
                   WEBVIEW_KT.replace("@@PKG@@", package)}
    else:
        ext = ".kt" if mode == "kotlin" else ".java"
        picks = {n: c for n, c in src.items() if n.endswith(ext)}
        if not any(MAIN_CLASS_RE.search(c) for c in picks.values()):
            default = DEFAULT_NATIVE_KT if mode == "kotlin" else DEFAULT_NATIVE_JAVA
            picks[f"MainActivity{ext}"] = default.replace("@@PKG@@", package)
        sources = {}
        for n, c in picks.items():
            filename = n.rsplit("/", 1)[-1]
            # package ត្រូវដូច applicationId ដើម្បីឲ្យ manifest `.MainActivity` ដំណើរការ
            decl = "package " + package + (";" if mode == "java" else "")
            body = (PACKAGE_RE.sub(decl, c, count=1)
                    if PACKAGE_RE.search(c) else decl + "\n\n" + c)
            sources[f"app/src/main/java/{path}/{filename}"] = body

    icon_attr = ""
    if icons:
        icon_attr = ('        android:icon="@mipmap/ic_launcher"\n'
                     '        android:roundIcon="@mipmap/ic_launcher_round"\n')

    out = {
        "settings.gradle.kts": SETTINGS_GRADLE.replace("@@ROOT@@", package.split(".")[-1]),
        "build.gradle.kts": ROOT_GRADLE,
        "gradle.properties": GRADLE_PROPS,
        "app/build.gradle.kts": (APP_GRADLE.replace("@@PKG@@", package)
                                 .replace("@@VERSION@@", xml_escape(version))
                                 .replace("@@BUILD@@", str(build))),
        "app/src/main/AndroidManifest.xml": (MANIFEST
            .replace("@@PERMISSIONS@@", _permission_blocks(permissions, online,
                                                           manifest_perms))
            .replace("@@APP_EXTRA@@", app_extra)
            .replace("@@ICON@@", icon_attr)
            .replace("@@CLEARTEXT@@",
                     '        android:usesCleartextTraffic="true"\n' if online else "")
            .replace("@@ORIENTATION@@",
                     (f'            android:screenOrientation="{ORIENTATIONS[orientation]}"\n'
                      if ORIENTATIONS[orientation] else ""))),
        "app/src/main/res/values/strings.xml": STRINGS.replace("@@TITLE@@", xml_escape(title)),
        ".gitignore": GITIGNORE,
    }
    out.update(sources)
    if mode == "webview":
        for name, content in files.items():
            if isinstance(content, (bytes, bytearray)):
                out[f"app/src/main/assets/www/{name}"] = content
            else:
                out[f"app/src/main/assets/www/{name}"] = content
    out.update(icons)
    return out
