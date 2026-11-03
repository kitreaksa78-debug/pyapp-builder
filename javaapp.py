#!/usr/bin/env python3
"""បង្កើត project Android native (Java + Gradle) សម្រាប់ build ជា APK។

មិនប្រើ buildozer/Kivy ទេ — Gradle + Android SDK compile Java ដោយផ្ទាល់
ដូច្នេះ APK ចេញលឿន (~៣–៥ នាទី) និងបើកដើរបានដោយគ្មាន Python runtime។
"""
from __future__ import annotations

import re

URL_RE = re.compile(r"^https?://[^\s\"'<>]+$")

MAX_HEADING = 120
MAX_MESSAGE = 4000
MAX_LABEL = 40


def java_literal(text: str) -> str:
    """បំលែងអក្សរទៅជា Java string literal (ខ្មែរ → \\uXXXX) ដើម្បីកុំឲ្យខូច encoding។"""
    parts = []
    for ch in str(text):
        if ch == "\\":
            parts.append("\\\\")
        elif ch == '"':
            parts.append('\\"')
        elif ch == "\n":
            parts.append("\\n")
        elif ch == "\r":
            continue
        elif ch == "\t":
            parts.append("\\t")
        elif " " <= ch <= "~":
            parts.append(ch)
        else:
            parts.append("\\u%04x" % ord(ch))
    return '"' + "".join(parts) + '"'


def xml_escape(text: str) -> str:
    return (str(text).replace("&", "&amp;").replace("<", "&lt;")
            .replace(">", "&gt;").replace('"', "&quot;"))


def validate_java_fields(heading: str, message: str, api_url: str,
                         button_label: str, button_url: str) -> dict:
    heading = (heading or "").strip()[:MAX_HEADING]
    message = (message or "").strip()[:MAX_MESSAGE]
    api_url = (api_url or "").strip()
    button_label = (button_label or "").strip()[:MAX_LABEL]
    button_url = (button_url or "").strip()
    if api_url and not URL_RE.match(api_url):
        raise ValueError("API URL មិនត្រឹមត្រូវ — ត្រូវចាប់ផ្តើមដោយ http:// ឬ https://")
    if button_url and not URL_RE.match(button_url):
        raise ValueError("URL ប៊ូតុងមិនត្រឹមត្រូវ — ត្រូវចាប់ផ្តើមដោយ http:// ឬ https://")
    return {"heading": heading, "message": message, "api_url": api_url,
            "button_label": button_label, "button_url": button_url}


ACTIVITY = r'''package @@PKG@@;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * បង្កើតដោយ PyApp Builder — app Android native សរសេរដោយ Java។
 * UI សង់ដោយកូដ (គ្មាន XML layout) ដូច្នេះ build មិនស្ទះដោយរឿង resource។
 */
public class MainActivity extends Activity {

    private static final String API_URL = @@API_URL@@;

    private TextView apiOut;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        int pad = dp(20);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        root.setBackgroundColor(Color.WHITE);

        TextView heading = new TextView(this);
        heading.setText(@@HEADING@@);
        heading.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        heading.setTextColor(Color.parseColor("#0f172a"));
        heading.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(heading);

        TextView message = new TextView(this);
        message.setText(@@MESSAGE@@);
        message.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        message.setTextColor(Color.parseColor("#334155"));
        message.setPadding(0, dp(12), 0, 0);
        root.addView(message);

        Button openButton = new Button(this);
        openButton.setText(@@BUTTON_LABEL@@);
        openButton.setAllCaps(false);
        openButton.setVisibility(@@BUTTON_VIS@@);
        openButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(@@BUTTON_URL@@)));
            }
        });
        root.addView(openButton, buttonLayout());

        Button apiButton = new Button(this);
        apiButton.setText("ហៅ API");
        apiButton.setAllCaps(false);
        apiButton.setVisibility(@@API_VIS@@);
        apiButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                callApi();
            }
        });
        root.addView(apiButton, buttonLayout());

        apiOut = new TextView(this);
        apiOut.setText(@@API_HINT@@);
        apiOut.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        apiOut.setTextColor(Color.parseColor("#334155"));
        apiOut.setTypeface(Typeface.MONOSPACE);
        apiOut.setPadding(dp(10), dp(10), dp(10), dp(10));
        apiOut.setBackgroundColor(Color.parseColor("#f1f5f9"));
        apiOut.setMovementMethod(new ScrollingMovementMethod());
        LinearLayout.LayoutParams outLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        outLp.topMargin = dp(14);
        root.addView(apiOut, outLp);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
    }

    private LinearLayout.LayoutParams buttonLayout() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(16);
        return lp;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void callApi() {
        if (API_URL.isEmpty()) {
            apiOut.setText("API URL មិនបានកំណត់");
            return;
        }
        apiOut.setText("កំពុងភ្ជាប់ " + API_URL + " …");
        new Thread(new Runnable() {
            @Override
            public void run() {
                String result;
                HttpURLConnection conn = null;
                try {
                    conn = (HttpURLConnection) new URL(API_URL).openConnection();
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(15000);
                    conn.setRequestProperty("Accept", "application/json");
                    int code = conn.getResponseCode();
                    InputStream in = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
                    StringBuilder sb = new StringBuilder();
                    if (in != null) {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
                        String line;
                        while ((line = reader.readLine()) != null) {
                            sb.append(line).append('\n');
                        }
                        reader.close();
                    }
                    result = "HTTP " + code + "  (" + API_URL + ")\n" + sb.toString().trim();
                } catch (Exception e) {
                    result = "មិនអាចភ្ជាប់ API បាន: " + e;
                } finally {
                    if (conn != null) {
                        conn.disconnect();
                    }
                }
                if (result.length() > 4000) {
                    result = result.substring(0, 4000) + " …";
                }
                final String out = result;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        apiOut.setText(out);
                    }
                });
            }
        }).start();
    }
}
'''


MANIFEST = '''<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />

    <application
        android:allowBackup="true"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:usesCleartextTraffic="true"
        android:theme="@android:style/Theme.Material.Light.NoActionBar">
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>

</manifest>
'''

STRINGS = '''<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">@@TITLE@@</string>
</resources>
'''

ROOT_GRADLE = '''// Project បង្កើតដោយ PyApp Builder (Java + Gradle)
plugins {
    id 'com.android.application' version '8.5.2' apply false
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
include ':app'
'''

APP_GRADLE = '''plugins {
    id 'com.android.application'
}

android {
    namespace '@@PKG@@'
    compileSdk 34

    defaultConfig {
        applicationId '@@PKG@@'
        minSdk 21
        targetSdk 34
        versionCode 1
        versionName '1.0'
    }

    buildTypes {
        debug {
            minifyEnabled false
        }
    }

    compileOptions {
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }

    lint {
        abortOnError false
    }
}

tasks.withType(JavaCompile).configureEach {
    options.encoding = 'UTF-8'
}
'''

GRADLE_PROPS = '''org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=false
android.nonTransitiveRClass=true
'''

GITIGNORE = '''.gradle/
build/
app/build/
local.properties
out/
*.iml
.idea/
'''

README = '''# @@TITLE@@ (Java)

App Android native បង្កើតដោយ PyApp Builder។ សរសេរដោយ **Java** (គ្មាន Python/Kivy)។

- package: `@@PKG@@`
- minSdk 21 · targetSdk 34 · compileSdk 34
- UI សង់ដោយកូដនៅក្នុង `app/src/main/java/@@PATH@@/MainActivity.java`

## Build APK ដោយខ្លួនឯង

```bash
# ត្រូវការ JDK 17 និង Android SDK (ANDROID_HOME)
gradle assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```
'''


def java_project_files(title: str, package: str, heading: str = "", message: str = "",
                       api_url: str = "", button_label: str = "",
                       button_url: str = "") -> dict:
    """បង្កើត file ទាំងអស់របស់ project Java ជា dict {path: content}។"""
    package = package.strip()
    path = package.replace(".", "/")
    heading = heading or title
    message = message or ""
    label = button_label or "បើកគេហទំព័រ"
    url = button_url or "https://www.google.com"

    activity = (ACTIVITY
                .replace("@@PKG@@", package)
                .replace("@@HEADING@@", java_literal(heading))
                .replace("@@MESSAGE@@", java_literal(message))
                .replace("@@BUTTON_LABEL@@", java_literal(label))
                .replace("@@BUTTON_URL@@", java_literal(url))
                .replace("@@BUTTON_VIS@@", "View.VISIBLE" if button_url else "View.GONE")
                .replace("@@API_URL@@", java_literal(api_url))
                .replace("@@API_VIS@@", "View.VISIBLE" if api_url else "View.GONE")
                .replace("@@API_HINT@@", java_literal(
                    "ចុច «ហៅ API» ដើម្បីទាញទិន្នន័យពី " + api_url if api_url
                    else "API URL មិនបានកំណត់")))

    return {
        "build.gradle": ROOT_GRADLE,
        "settings.gradle": SETTINGS_GRADLE.replace("@@ROOT@@", package.split(".")[-1]),
        "gradle.properties": GRADLE_PROPS,
        "app/build.gradle": APP_GRADLE.replace("@@PKG@@", package),
        "app/src/main/AndroidManifest.xml": MANIFEST,
        "app/src/main/res/values/strings.xml": STRINGS.replace("@@TITLE@@", xml_escape(title)),
        f"app/src/main/java/{path}/MainActivity.java": activity,
        ".gitignore": GITIGNORE,
        "README.md": (README.replace("@@TITLE@@", title).replace("@@PKG@@", package)
                      .replace("@@PATH@@", path)),
    }
