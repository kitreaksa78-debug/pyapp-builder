#!/usr/bin/env python3
"""បង្កើត project Android native (Java + Gradle) សម្រាប់ build ជា APK។

អ្នកប្រើសរសេរ **កូដ Java** ដោយខ្លួនឯង (MainActivity.java) — package name
បញ្ចូលដោយស្វ័យប្រវត្តិ។ Gradle + Android SDK compile ក្នុង GitHub Actions
ដូច្នេះមិនត្រូវការ Python runtime នៅលើទូរស័ព្ទទេ។
"""
from __future__ import annotations

import re

MAX_CODE = 60_000

PKG_LINE_RE = re.compile(r"^[ \t]*package[ \t]+[\w.]+[ \t]*;[ \t]*\n?", re.MULTILINE)
CLASS_RE = re.compile(r"\bclass\s+MainActivity\b")
# class ដែល extends Service ⇒ ត្រូវប្រកាសក្នុង manifest (ឧ. foreground service)
SERVICE_RE = re.compile(r"\bclass\s+(\w+)\s+extends\s+Service\b")


def xml_escape(text: str) -> str:
    return (str(text).replace("&", "&amp;").replace("<", "&lt;")
            .replace(">", "&gt;").replace('"', "&quot;"))


def validate_java_code(code: str) -> str:
    code = (code or "").replace("\r\n", "\n").strip()
    if not code:
        raise ValueError("កូដ Java ទទេ — សរសេរ class MainActivity")
    if len(code) > MAX_CODE:
        raise ValueError("កូដវែងពេក (អតិបរមា 60,000 តួ)")
    if not CLASS_RE.search(code):
        raise ValueError("កូដត្រូវមាន class ឈ្មោះ MainActivity (ឈ្មោះដូចដើម)")
    return code


def normalize_java(code: str, package: str) -> str:
    """ដាក់ `package ...;` ខាងលើ — បើអ្នកប្រើសរសេរ package ខ្លួនឯង យកចេញវិញ។"""
    body = PKG_LINE_RE.sub("", code).strip("\n")
    return f"package {package};\n\n{body}\n"


DEFAULT_JAVA_SOURCE = '''import android.app.Activity;
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
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * App Android សរសេរដោយ Java — កែកូដនេះបានសេរី។
 * package name បញ្ចូលដោយស្វ័យប្រវត្តិពីប្រអប់ "Package name" ខាងលើ។
 */
public class MainActivity extends Activity {

    // ដាក់ URL របស់ API នៅទីនេះ (បើមិនប្រើ ទុកទទេ "")
    private static final String API_URL = "https://pyapp-builder.onrender.com/api/health";

    private EditText input;
    private TextView output;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        int pad = dp(20);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        root.setBackgroundColor(Color.WHITE);

        TextView heading = new TextView(this);
        heading.setText("សួស្តី! នេះជា app Java");
        heading.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        heading.setTextColor(Color.parseColor("#0f172a"));
        heading.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(heading);

        TextView about = new TextView(this);
        about.setText("សរសេរកូដ Java ក្នុងប្រអប់ខាងលើ រួចចុច Build APK។");
        about.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        about.setTextColor(Color.parseColor("#475569"));
        about.setPadding(0, dp(10), 0, 0);
        root.addView(about);

        input = new EditText(this);
        input.setHint("សរសេរឈ្មោះរបស់អ្នក");
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        root.addView(input, topMargin(dp(16)));

        Button greet = new Button(this);
        greet.setText("ស្វាគមន៍");
        greet.setAllCaps(false);
        greet.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String name = input.getText().toString().trim();
                if (name.isEmpty()) {
                    name = "មិត្តភក្តិ";
                }
                output.setText("សួស្តី " + name + "! 👋");
            }
        });
        root.addView(greet, topMargin(dp(16)));

        Button api = new Button(this);
        api.setText("ហៅ API");
        api.setAllCaps(false);
        api.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                callApi();
            }
        });
        root.addView(api, topMargin(dp(16)));

        Button open = new Button(this);
        open.setText("បើកគេហទំព័រ");
        open.setAllCaps(false);
        open.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com")));
            }
        });
        root.addView(open, topMargin(dp(16)));

        output = new TextView(this);
        output.setText("លទ្ធផលនឹងបង្ហាញនៅទីនេះ។");
        output.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        output.setTextColor(Color.parseColor("#334155"));
        output.setTypeface(Typeface.MONOSPACE);
        output.setPadding(dp(10), dp(10), dp(10), dp(10));
        output.setBackgroundColor(Color.parseColor("#f1f5f9"));
        output.setMovementMethod(new ScrollingMovementMethod());
        root.addView(output, topMargin(dp(16)));

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
    }

    private LinearLayout.LayoutParams topMargin(int pixels) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = pixels;
        return lp;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void callApi() {
        if (API_URL.isEmpty()) {
            output.setText("API URL មិនបានកំណត់");
            return;
        }
        output.setText("កំពុងភ្ជាប់ " + API_URL + " …");
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
                            sb.append(line).append('\\n');
                        }
                        reader.close();
                    }
                    result = "HTTP " + code + "\\n" + sb.toString().trim();
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
                        output.setText(out);
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
@@PERMISSIONS@@
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
@@SERVICES@@
    </application>

</manifest>
'''


# permission ដែលត្រូវការពេលមាន Service (foreground service)
SERVICE_PERMISSIONS = ("FOREGROUND_SERVICE", "FOREGROUND_SERVICE_DATA_SYNC",
                       "POST_NOTIFICATIONS")

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

    // Key ថេរនៅក្នុង repo ⇒ build ថ្មីដំឡើងជំនួស app ចាស់បាន (គ្មាន error signature)
    signingConfigs {
        stable {
            storeFile file('../signing/debug.p12')
            storePassword 'android'
            keyAlias 'androiddebugkey'
            keyPassword 'android'
            storeType 'PKCS12'
        }
    }

    buildTypes {
        debug {
            minifyEnabled false
            signingConfig signingConfigs.stable
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

App Android native បង្កើតដោយ PyApp Builder — សរសេរដោយ **Java**។

- package: `@@PKG@@`
- minSdk 21 · targetSdk 34 · compileSdk 34
- កូដ app៖ `app/src/main/java/@@PATH@@/MainActivity.java`

## Build APK ដោយខ្លួនឯង

```bash
# ត្រូវការ JDK 17 និង Android SDK (ANDROID_HOME)
gradle assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```
'''


def manifest_xml(code: str) -> str:
    """Manifest — ប្រកាស Service ដែលអ្នកប្រើសរសេរដោយស្វ័យប្រវត្តិ។"""
    names = []
    for name in SERVICE_RE.findall(code):
        for candidate in (f".{name}", f".MainActivity${name}"):
            if candidate not in names:
                names.append(candidate)

    permissions = ""
    services = ""
    if names:
        permissions = "".join(
            f'    <uses-permission android:name="android.permission.{p}" />\n'
            for p in SERVICE_PERMISSIONS)
        services = "".join(
            "        <service\n"
            f'            android:name="{name}"\n'
            '            android:exported="false"\n'
            '            android:foregroundServiceType="dataSync" />\n'
            for name in names)
    return (MANIFEST.replace("@@PERMISSIONS@@", permissions)
            .replace("@@SERVICES@@", services))


def java_project_files(title: str, package: str, code: str) -> dict:
    """បង្កើត file ទាំងអស់របស់ project Java ជា dict {path: content}។"""
    package = package.strip()
    path = package.replace(".", "/")
    source = normalize_java(code, package)

    return {
        "build.gradle": ROOT_GRADLE,
        "settings.gradle": SETTINGS_GRADLE.replace("@@ROOT@@", package.split(".")[-1]),
        "gradle.properties": GRADLE_PROPS,
        "app/build.gradle": APP_GRADLE.replace("@@PKG@@", package),
        "app/src/main/AndroidManifest.xml": manifest_xml(code),
        "app/src/main/res/values/strings.xml": STRINGS.replace("@@TITLE@@", xml_escape(title)),
        f"app/src/main/java/{path}/MainActivity.java": source,
        ".gitignore": GITIGNORE,
        "README.md": (README.replace("@@TITLE@@", title).replace("@@PKG@@", package)
                      .replace("@@PATH@@", path)),
    }
