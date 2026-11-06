#!/usr/bin/env python3
"""Registry ភាសា — ប្រភពតែមួយសម្រាប់ backend (ide.py) និង editor (ide.js)។

`LANGUAGES` ភ្ជាប់ extension → (ឈ្មោះបង្ហាញ, Monaco language id)។
`BINARY_EXT` ជា file ដែលអានជា base64 (រូបភាព/សំឡេង/archive) មិនមែន text។

ការបន្ថែមភាសាថ្មី = បន្ថែម entry មួយនៅទីនេះ រួច /api/ide/info និង editor
ទទួលបានដោយស្វ័យប្រវត្តិ (គ្មានការកែ JS ដាច់ដោយឡែក)។
"""
from __future__ import annotations

# ext → (display name, monaco language id)
LANGUAGES: dict[str, tuple[str, str]] = {
    # JVM / Android
    "kt": ("Kotlin", "kotlin"),
    "kts": ("Gradle Kotlin DSL", "kotlin"),
    "java": ("Java", "java"),
    "gradle": ("Gradle Groovy", "groovy"),
    "groovy": ("Groovy", "groovy"),
    "pro": ("ProGuard rules", "plaintext"),
    "properties": ("Properties", "ini"),
    # Native / system
    "c": ("C", "c"),
    "h": ("C header", "c"),
    "cc": ("C++", "cpp"),
    "cpp": ("C++", "cpp"),
    "cxx": ("C++", "cpp"),
    "hpp": ("C++ header", "cpp"),
    "hxx": ("C++ header", "cpp"),
    "rs": ("Rust", "rust"),
    "go": ("Go", "go"),
    "swift": ("Swift", "swift"),
    "cs": ("C#", "csharp"),
    "dart": ("Dart / Flutter", "dart"),
    "py": ("Python", "python"),
    "rb": ("Ruby", "ruby"),
    "php": ("PHP", "php"),
    "lua": ("Lua", "lua"),
    "pl": ("Perl", "perl"),
    "r": ("R", "r"),
    # Web
    "html": ("HTML", "html"),
    "htm": ("HTML", "html"),
    "vue": ("Vue", "html"),
    "css": ("CSS", "css"),
    "scss": ("SCSS", "scss"),
    "js": ("JavaScript", "javascript"),
    "mjs": ("JavaScript (ESM)", "javascript"),
    "cjs": ("JavaScript (CommonJS)", "javascript"),
    "jsx": ("React JSX", "javascript"),
    "ts": ("TypeScript", "typescript"),
    "tsx": ("React TSX", "typescript"),
    # Data / config
    "json": ("JSON", "json"),
    "json5": ("JSON5", "json"),
    "xml": ("XML", "xml"),
    "svg": ("SVG", "xml"),
    "yml": ("YAML", "yaml"),
    "yaml": ("YAML", "yaml"),
    "toml": ("TOML", "ini"),
    "ini": ("INI", "ini"),
    "conf": ("Config", "ini"),
    "env": ("Env file", "ini"),
    "csv": ("CSV", "plaintext"),
    "md": ("Markdown", "markdown"),
    "txt": ("Text", "plaintext"),
    "log": ("Log", "plaintext"),
    "gitignore": (".gitignore", "plaintext"),
    "editorconfig": (".editorconfig", "ini"),
    # Scripts / databases
    "sh": ("Shell", "shell"),
    "bash": ("Bash", "shell"),
    "zsh": ("Zsh", "shell"),
    "bat": ("Batch", "bat"),
    "cmd": ("Batch", "bat"),
    "ps1": ("PowerShell", "powershell"),
    "sql": ("SQL", "sql"),
}

BINARY_EXT = {
    "png", "jpg", "jpeg", "webp", "gif", "ico", "bmp", "tif", "tiff",
    "ttf", "otf", "woff", "woff2", "eot",
    "mp3", "wav", "ogg", "m4a", "flac", "mp4", "webm", "mkv", "3gp",
    "pdf", "zip", "jar", "apk", "aab", "so", "bin", "keystore", "jks", "p12",
}

# Android source/asset ដែលអាចបញ្ចូលក្នុង project ដោយផ្ទាល់
NATIVE_EXT = ("c", "cc", "cpp", "cxx", "h", "hpp", "hxx")


def text_extensions() -> set[str]:
    return set(LANGUAGES)


def binary_extensions() -> set[str]:
    return set(BINARY_EXT)


def all_extensions() -> set[str]:
    return text_extensions() | binary_extensions()


def monaco_map() -> dict[str, str]:
    return {ext: monaco for ext, (_name, monaco) in LANGUAGES.items()}


def language_list() -> list[dict]:
    """Sorted list សម្រាប់ /api/ide/info → [{"ext","name","monaco"}]។"""
    return [
        {"ext": ext, "name": name, "monaco": monaco}
        for ext, (name, monaco) in sorted(LANGUAGES.items())
    ]
