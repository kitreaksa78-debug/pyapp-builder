#!/usr/bin/env python3
"""បង្កើត project Android native (C# + .NET for Android) សម្រាប់ build ជា APK។

អ្នកប្រើសរសេរ **កូដ C#** ដោយខ្លួនឯង (`MainActivity.cs`) — package name
បញ្ចូលដោយស្វ័យប្រវត្តិ។ `dotnet publish` + Android SDK compile ក្នុង
GitHub Actions ដូច្នេះមិនត្រូវការ runtime នៅលើទូរស័ព្ទទេ។
"""
from __future__ import annotations

import re

from javaapp import launcher_icons, xml_escape

MAX_CODE = 60_000

CLASS_RE = re.compile(r"\bclass\s+MainActivity\b")
ACTIVITY_ATTR_RE = re.compile(r"\[\s*Activity\b")
USING_APP_RE = re.compile(r"^[ \t]*using\s+Android\.App[ \t]*;", re.MULTILINE)


DEFAULT_CS_SOURCE = '''using Android.App;
using Android.Graphics;
using Android.OS;
using Android.Views;
using Android.Webkit;
using Android.Widget;

namespace PyApp
{
    /// <summary>
    /// App Android សរសេរដោយ C# (.NET for Android) — កែកូដនេះបានសេរី។
    /// </summary>
    [Activity(Label = "@string/app_name", MainLauncher = true, Exported = true)]
    public class MainActivity : Activity
    {
        // ដាក់ URL ដែល app នឹងបើកនៅទីនេះ (បើមិនប្រើ ទុកទទេ "")
        private const string HomeUrl = "https://pyapp-builder.onrender.com/";

        private WebView _web;

        protected override void OnCreate(Bundle savedInstanceState)
        {
            base.OnCreate(savedInstanceState);

            var root = new LinearLayout(this) { Orientation = Orientation.Vertical };
            root.SetBackgroundColor(Color.White);

            _web = new WebView(this);
            WebSettings settings = _web.Settings;
            settings.JavaScriptEnabled = true;
            settings.DomStorageEnabled = true;

            _web.SetWebViewClient(new WebViewClient());
            _web.SetWebChromeClient(new WebChromeClient());
            _web.LoadUrl(HomeUrl);

            root.AddView(_web, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MatchParent,
                ViewGroup.LayoutParams.MatchParent));

            SetContentView(root);
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


def validate_cs_code(code: str) -> str:
    code = (code or "").replace("\r\n", "\n").strip()
    if not code:
        raise ValueError("កូដ C# ទទេ — សរសេរ class MainActivity")
    if len(code) > MAX_CODE:
        raise ValueError("កូដវែងពេក (អតិបរមា 60,000 តួ)")
    if not CLASS_RE.search(code):
        raise ValueError("កូដត្រូវមាន class ឈ្មោះ MainActivity (ឈ្មោះដូចដើម)")
    return code


def normalize_cs(code: str) -> str:
    """បន្ថែម `[Activity(...)]` និង `using Android.App;` បើអ្នកប្រើមិនបានសរសេរ។

    បើគ្មាន `[Activity]` ទេ .NET មិនបង្កើត entry ក្នុង manifest ⇒ app បើកមិនបាន។
    """
    body = code.strip("\n")

    if not ACTIVITY_ATTR_RE.search(body):
        lines = body.split("\n")
        for index, line in enumerate(lines):
            if re.search(r"\bclass\s+MainActivity\b", line):
                indent = re.match(r"[ \t]*", line).group(0)
                lines.insert(index, indent + '[Activity(Label = "@string/app_name", '
                                            'MainLauncher = true, Exported = true)]')
                break
        body = "\n".join(lines)

    if not USING_APP_RE.search(body):
        body = "using Android.App;\n" + body

    return body + "\n"


MANIFEST = '''<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="@@PKG@@">

    <!-- 1. Internet and Network -->
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
    <uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />

    <!-- 2. Storage -->
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
    <uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE" />

    <!-- 3. Camera and Mic -->
    <uses-permission android:name="android.permission.CAMERA" />
    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    <uses-feature android:name="android.hardware.camera" android:required="false" />

    <!-- 4. Location -->
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />

    <!-- 5. Overlay and Background -->
    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />
    <uses-permission android:name="android.permission.VIBRATE" />

    <!-- 6. SMS -->
    <uses-permission android:name="android.permission.SEND_SMS" />
    <uses-permission android:name="android.permission.READ_SMS" />
    <uses-permission android:name="android.permission.RECEIVE_SMS" />

    <!-- 7. Others -->
    <uses-permission android:name="android.permission.READ_PHONE_STATE" />
    <uses-permission android:name="android.permission.CALL_PHONE" />
    <uses-permission android:name="android.permission.BLUETOOTH" />
    <uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <application
@@ICON@@        android:allowBackup="true"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:usesCleartextTraffic="true" />
</manifest>
'''

# activity បង្កើតដោយ `[Activity]` attribute របស់ C# — manifest មិនត្រូវប្រកាសវាទេ

CSPROJ = '''<Project Sdk="Microsoft.NET.Sdk">

  <PropertyGroup>
    <TargetFramework>net8.0-android</TargetFramework>
    <SupportedOSPlatformVersion>21</SupportedOSPlatformVersion>
    <OutputType>Exe</OutputType>
    <RootNamespace>@@PKG@@</RootNamespace>
    <ApplicationId>@@PKG@@</ApplicationId>
    <ApplicationVersion>@@BUILD@@</ApplicationVersion>
    <ApplicationDisplayVersion>@@VERSION@@</ApplicationDisplayVersion>
    <AndroidPackageFormat>apk</AndroidPackageFormat>
    <Nullable>disable</Nullable>
    <ImplicitUsings>disable</ImplicitUsings>
    <!-- មិន trim ដើម្បីកុំឱ្យកូដអ្នកប្រើបែកពេលប្រើ reflection -->
    <AndroidLinkMode>None</AndroidLinkMode>
    <RunAOTCompilation>false</RunAOTCompilation>
    <NoWarn>$(NoWarn);XA4211;CS0618;CS0612</NoWarn>

    <!-- Key ថេរនៅក្នុង repo ⇒ build ថ្មីដំឡើងជំនួស app ចាស់បាន (គ្មាន signature mismatch) -->
    <AndroidKeyStore>true</AndroidKeyStore>
    <AndroidSigningKeyStore>signing/debug.p12</AndroidSigningKeyStore>
    <AndroidSigningStorePass>android</AndroidSigningStorePass>
    <AndroidSigningKeyAlias>androiddebugkey</AndroidSigningKeyAlias>
    <AndroidSigningKeyPass>android</AndroidSigningKeyPass>
  </PropertyGroup>

</Project>
'''

STRINGS = '''<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">@@TITLE@@</string>
</resources>
'''

GITIGNORE = '''bin/
obj/
*.user
.vs/
'''

README = '''# @@TITLE@@ (C# · .NET for Android)

App Android native បង្កើតដោយ PyApp Builder — សរសេរដោយ **C#**។

- package: `@@PKG@@`
- minSdk 21 · targetSdk 34 (net8.0-android)
- កូដ app៖ `MainActivity.cs` (class `MainActivity`, `[Activity]` attribute បង្កើត manifest entry)

## Build APK ដោយខ្លួនឯង

```bash
# ត្រូវការ .NET 8 SDK + Android SDK + JDK 17
dotnet workload install android
dotnet publish -f net8.0-android -c Release -p:AndroidPackageFormat=apk
# APK: bin/Release/net8.0-android/publish/*-Signed.apk
```
'''


def cs_project_files(title: str, package: str, code: str,
                     icon: bytes | None = None,
                     version: str = "1.0", build: str = "1") -> dict:
    """បង្កើត file ទាំងអស់របស់ project C# ជា dict {path: content}។"""
    package = package.strip()
    source = normalize_cs(code)
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
        "MainActivity.cs": source,
        "Resources/values/strings.xml": STRINGS.replace("@@TITLE@@", xml_escape(title)),
        ".gitignore": GITIGNORE,
        "README.md": (README.replace("@@TITLE@@", title)
                      .replace("@@PKG@@", package)),
    }
    # icon → Resources/mipmap-*/ic_launcher.png (.NET Android resource)
    for path, blob in icons.items():
        files[path.replace("app/src/main/res/", "Resources/")] = blob
    return files
