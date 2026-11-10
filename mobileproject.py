"""Flutter (Dart) និង React Native (Expo) project ពិត — CI ដំឡើង SDK ហើយ compile។

Android compile ផ្ទាល់បានតែ Kotlin/Java/C/C++ (+Python តាម Chaquopy)។ Dart និង JS
ត្រូវការ SDK រៀងៗខ្លួន ដូច្នេះ module នេះបង្កើត project ពេញលេញដែល workflow យក
`flutter build apk` ឬ `expo prebuild` + Gradle ទៅបង្កើត APK ពិត (មិនមែន WebView)។
"""
from __future__ import annotations

import json
import re

MARKER = ".pyapp-build.json"

FLUTTER_MAIN = r'''import 'package:flutter/material.dart';

void main() => runApp(const MainApp());

class MainApp extends StatelessWidget {
  const MainApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: '@@TITLE@@',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: const Color(0xFF1976D2)),
        useMaterial3: true,
      ),
      home: const HomePage(),
    );
  }
}

class HomePage extends StatelessWidget {
  const HomePage({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('@@TITLE@@')),
      body: const Center(
        child: Padding(
          padding: EdgeInsets.all(24),
          child: Text(
            '@@TITLE@@',
            textAlign: TextAlign.center,
            style: TextStyle(fontSize: 22, fontWeight: FontWeight.w600),
          ),
        ),
      ),
    );
  }
}
'''

RN_APP = r'''import { StatusBar } from 'expo-status-bar';
import { SafeAreaView, StyleSheet, Text, View } from 'react-native';

export default function App() {
  return (
    <SafeAreaView style={styles.container}>
      <StatusBar style="light" />
      <View style={styles.card}>
        <Text style={styles.title}>@@TITLE@@</Text>
        <Text style={styles.hint}>React Native - Expo</Text>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#0d1117',
    alignItems: 'center',
    justifyContent: 'center',
  },
  card: { paddingHorizontal: 24 },
  title: {
    color: '#ffffff',
    fontSize: 24,
    fontWeight: '700',
    textAlign: 'center',
  },
  hint: {
    color: '#8b949e',
    fontSize: 14,
    marginTop: 10,
    textAlign: 'center',
  },
});
'''

RN_INDEX = """import { registerRootComponent } from 'expo';

import App from './App';

registerRootComponent(App);
"""


def _dart_str(text: str) -> str:
    """សម្អាតអត្ថបទសម្រាប់ single-quoted Dart string។"""
    return (str(text or "").replace("\\", "\\\\").replace("'", "\\'")
            .replace("$", "\\$").replace("\n", " "))


def _ident(text: str, default: str = "app") -> str:
    """ឈ្មោះ package/project ត្រឹមត្រូវសម្រាប់ pubspec និង package.json។"""
    name = re.sub(r"[^A-Za-z0-9_]+", "_", str(text or "")).strip("_").lower()
    name = re.sub(r"_+", "_", name)
    if not name:
        return default
    if not name[0].isalpha():
        name = "app_" + name
    return name[:40]


def _version_parts(version: str, build: str) -> tuple[str, str]:
    """version/public (ឧ. "1.2") + build number → (versionName, buildCode)។"""
    ver = str(version or "1.0").strip()
    if not re.match(r"^[0-9]{1,3}(\.[0-9]{1,3}){0,3}$", ver):
        ver = "1.0"
    if ver.count(".") < 2:
        ver = ver + ".0" * (2 - ver.count("."))
    try:
        code = int(str(build or "1"))
    except (TypeError, ValueError):
        code = 1
    return ver, str(max(1, min(code, 999_999)))


def _marker(kind: str, title: str, package: str, name: str,
            version: str, build: str, artifact: str) -> str:
    return json.dumps({
        "type": kind,
        "title": title,
        "package": package,
        "name": name,
        "org": package.rsplit(".", 1)[0] if "." in package else package,
        "version": version,
        "build": build,
        "artifact": artifact,
    }, ensure_ascii=False, indent=2) + "\n"


def _pubspec(name: str, title: str, version: str, build: str) -> str:
    return ("name: %s\ndescription: %s\npublish_to: 'none'\nversion: %s+%s\n\n"
            "environment:\n  sdk: '>=3.0.0 <4.0.0'\n\n"
            "dependencies:\n  flutter:\n    sdk: flutter\n\n"
            "flutter:\n  uses-material-design: true\n"
            % (name, json.dumps(title or name), version, build))


def _merge_pubspec(user: str, name: str, title: str, version: str, build: str) -> str:
    """រក្សា pubspec របស់អ្នកប្រើ (dependencies ពិត) ហើយបំពេញអ្វីដែលខ្វះ។"""
    text = user or ""
    if not re.search(r"(?m)^name:", text):
        text = "name: %s\n" % name + text
    if not re.search(r"(?m)^environment:", text):
        text = text.rstrip() + "\n\nenvironment:\n  sdk: '>=3.0.0 <4.0.0'\n"
    if not re.search(r"(?m)^version:", text):
        text = text.rstrip() + "\nversion: %s+%s\n" % (version, build)
    return text


def _dart_files(files: dict) -> dict:
    """file .dart របស់អ្នកប្រើ → ក្រោម lib/ (រក្សា path ខាងក្នុង lib)។"""
    out: dict = {}
    for path, content in (files or {}).items():
        if not isinstance(content, str) or not path.lower().endswith(".dart"):
            continue
        rel = path[4:] if path.startswith("lib/") else path.rsplit("/", 1)[-1]
        out[rel] = content
    return out


def flutter_project_files(title: str, package: str, files: dict, *,
                          version: str = "1.0", build: str = "1",
                          artifact: str = "apk") -> dict:
    """Flutter app ពេញលេញ — `flutter build apk` បង្កើត APK ពិត។"""
    name = _ident(title, "flutter_app")
    ver, code = _version_parts(version, build)
    user_pubspec = ""
    for path, content in (files or {}).items():
        if isinstance(content, str) and path.rsplit("/", 1)[-1].lower() in (
                "pubspec.yaml", "pubspec.yml"):
            user_pubspec = content
            break
    dart = _dart_files(files)
    main = dart.get("main.dart")
    if not isinstance(main, str) or not re.search(r"\bvoid\s+main\s*\(", main):
        # គ្មាន entrypoint ត្រឹមត្រូវ → runner ដើម (file ផ្សេងទៀតមិន compile បើគ្មានអ្នក import)
        dart["main.dart"] = FLUTTER_MAIN.replace("@@TITLE@@", _dart_str(title or name))
    out = {
        "pubspec.yaml": (_merge_pubspec(user_pubspec, name, title, ver, code)
                         if user_pubspec.strip() else _pubspec(name, title, ver, code)),
        MARKER: _marker("flutter", title, package, name, ver, code, artifact),
        ".gitignore": (".dart_tool/\nbuild/\n.flutter-plugins\n"
                       ".flutter-plugins-dependencies\nandroid/.gradle/\n"
                       "android/local.properties\n"),
    }
    for rel, content in dart.items():
        out["lib/" + rel] = content
    return out


def _rn_package(name: str, title: str, user_pkg: dict) -> str:
    deps = {
        "expo": "~51.0.0",
        "expo-status-bar": "~1.12.1",
        "react": "18.2.0",
        "react-native": "0.74.5",
    }
    user_deps = (user_pkg or {}).get("dependencies")
    if isinstance(user_deps, dict):
        # dependency របស់អ្នកប្រើឈ្នះ (កុំបញ្ចុះ/ដំឡើង version ខុស)
        for key, value in user_deps.items():
            if isinstance(key, str) and isinstance(value, str):
                deps[key] = value
    deps.setdefault("expo", "~51.0.0")
    deps.setdefault("react-native", "0.74.5")
    data = {
        "name": name,
        "version": "1.0.0",
        "private": True,
        "main": "index.js",
        "scripts": {"start": "expo start", "android": "expo run:android"},
        "dependencies": deps,
        "devDependencies": {"@babel/core": "^7.24.0"},
    }
    return json.dumps(data, ensure_ascii=False, indent=2) + "\n"


def reactnative_project_files(title: str, package: str, files: dict, *,
                              version: str = "1.0", build: str = "1",
                              artifact: str = "apk") -> dict:
    """React Native (Expo) app — CI រត់ `expo prebuild` + Gradle → APK ពិត។"""
    name = _ident(title, "rn_app")
    ver, code = _version_parts(version, build)
    user_pkg: dict = {}
    for path, content in (files or {}).items():
        if (isinstance(content, str) and path.rsplit("/", 1)[-1] == "package.json"):
            try:
                parsed = json.loads(content)
            except ValueError:
                continue
            if isinstance(parsed, dict):
                user_pkg = parsed
            break
    app_json = json.dumps({"expo": {
        "name": title or name,
        "slug": name.replace("_", "-"),
        "version": ver,
        "orientation": "portrait",
        "userInterfaceStyle": "automatic",
        "newArchEnabled": False,
        "android": {"package": package, "versionCode": int(code)},
    }}, ensure_ascii=False, indent=2) + "\n"
    out = {
        "package.json": _rn_package(name, title, user_pkg),
        "app.json": app_json,
        "babel.config.js": "module.exports = function (api) {\n"
                           "  api.cache(true);\n"
                           "  return { presets: ['babel-preset-expo'] };\n};\n",
        MARKER: _marker("reactnative", title, package, name, ver, code, artifact),
        ".gitignore": "node_modules/\nandroid/\nios/\n.expo/\n*.log\n",
    }
    has_app = False
    for path, content in (files or {}).items():
        if not isinstance(content, str):
            continue
        base = path.rsplit("/", 1)[-1]
        if base in ("package.json", "app.json", MARKER, "babel.config.js",
                    "index.js", "App.js", "App.tsx", "App.jsx"):
            if base in ("App.js", "App.tsx", "App.jsx"):
                out[base] = content
                has_app = True
            continue
        out[path] = content
    if not has_app:
        out["App.js"] = RN_APP.replace("@@TITLE@@", (title or name).replace("'", "\\'"))
    if "index.js" not in out:
        out["index.js"] = RN_INDEX
    return out
