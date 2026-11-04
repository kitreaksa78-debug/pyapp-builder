#!/usr/bin/env python3
"""បង្កើត project Android native (**Kotlin + Gradle**) សម្រាប់ build ជា APK។

UI សរសេរដោយ JavaScript/CSS ដាក់ក្នុង `app/src/main/assets/www/` —
`MainActivity.kt` (WebView) បើក `file:///android_asset/www/index.html` ដោយផ្ទាល់
(offline)។ Gradle + Android SDK compile ក្នុង GitHub Actions។
"""
from __future__ import annotations

from javaapp import MAX_ICON_BYTES, launcher_icons, xml_escape

MAIN_ACTIVITY = '''package @@PKG@@

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * App Android សាងសង់ដោយ JavaScript — WebView បើក UI ក្នុង
 * app/src/main/assets/www/index.html (offline, មិនត្រូវការ internet)។
 */
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
            settings.builtInZoomControls = false
            settings.displayZoomControls = false

            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()
            loadUrl("file:///android_asset/www/index.html")
        }

        setContentView(web)
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (::web.isInitialized && web.canGoBack()) {
            web.goBack()
            return
        }
        super.onBackPressed()
    }

    override fun onDestroy() {
        if (::web.isInitialized) {
            web.destroy()
        }
        super.onDestroy()
    }
}
'''

MANIFEST = '''<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.VIBRATE" />

    <application
@@ICON@@        android:allowBackup="true"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:usesCleartextTraffic="true"
        android:theme="@android:style/Theme.Material.Light.NoActionBar">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:theme="@android:style/Theme.Material.Light.NoActionBar">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>

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

    // Key ថេរនៅក្នុង repo ⇒ build ថ្មីដំឡើងជំនួស app ចាស់បាន (គ្មាន error signature)
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

README = '''# @@TITLE@@ (JavaScript · Kotlin WebView)

App Android បង្កើតដោយ PyApp Builder — UI សរសេរដោយ **JavaScript + CSS**។

- package: `@@PKG@@`
- minSdk 21 · targetSdk 34 · compileSdk 34 (Kotlin 1.9 · AGP 8.5)
- UI៖ `app/src/main/assets/www/index.html` (បង្កើតដោយស្វ័យប្រវត្តិពី `app.js` + `style.css`)
- `app/src/main/java/@@PATH@@/MainActivity.kt`៖ WebView បើក `file:///android_asset/www/index.html` — offline

## កែ UI

កែកូដ JavaScript/CSS ក្នុងទំព័រ builder ហើយ Build ម្តងទៀត។

## Build APK ដោយខ្លួនឯង

```bash
# ត្រូវការ JDK 17 និង Android SDK (ANDROID_HOME)
gradle assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```
'''


def kotlin_project_files(title: str, package: str, js: str, css: str = "",
                         icon: bytes | None = None,
                         version: str = "1.0", build: str = "1") -> dict:
    """បង្កើត file ទាំងអស់របស់ project Kotlin ជា dict {path: content}។"""
    from webapp import build_app_html, validate_css, validate_js

    package = package.strip()
    path = package.replace(".", "/")
    js = validate_js(js)
    css = validate_css(css)
    icons = launcher_icons(icon) if icon else {}

    icon_attr = ""
    if icons:
        icon_attr = ('        android:icon="@mipmap/ic_launcher"\n'
                     '        android:roundIcon="@mipmap/ic_launcher_round"\n')

    files = {
        "settings.gradle.kts": SETTINGS_GRADLE.replace("@@ROOT@@", package.split(".")[-1]),
        "build.gradle.kts": ROOT_GRADLE,
        "gradle.properties": GRADLE_PROPS,
        "app/build.gradle.kts": (APP_GRADLE.replace("@@PKG@@", package)
                                 .replace("@@VERSION@@", xml_escape(version))
                                 .replace("@@BUILD@@", str(build))),
        "app/src/main/AndroidManifest.xml": (MANIFEST.replace("@@ICON@@", icon_attr)),
        "app/src/main/java/{}/MainActivity.kt".format(path): MAIN_ACTIVITY.replace("@@PKG@@", package),
        "app/src/main/res/values/strings.xml": STRINGS.replace("@@TITLE@@", xml_escape(title)),
        "app/src/main/assets/www/index.html": build_app_html(title, js, css),
        "app/src/main/assets/www/app.js": js,
        "app/src/main/assets/www/style.css": css,
        ".gitignore": GITIGNORE,
        "README.md": (README.replace("@@TITLE@@", title)
                      .replace("@@PKG@@", package)
                      .replace("@@PATH@@", path)),
    }
    files.update(icons)
    return files
