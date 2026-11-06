"""ZIP → APK — ពិនិត្យ Android project ZIP មុនបញ្ជូនទៅ cloud build។

គ្មាន simulation: server ពិនិត្យតែ structure (Gradle, Manifest, source)។
APK ពិតកើតចេញពី GitHub Actions ដែលរត់ JDK + Android SDK + Gradle
(`assembleDebug`) ហើយ build log ពិតត្រូវបានផ្សព្វផ្សាយជា release asset។
"""
from __future__ import annotations

import io
import re
import zipfile

MAX_ZIP_BYTES = 40 * 1024 * 1024          # file .zip ដែល upload
MAX_UNZIPPED_BYTES = 150 * 1024 * 1024    # ទំហំសរុបពេលពន្លា
MAX_FILES = 4000

# folder ទាំងនេះមិនចាំបាច់សម្រាប់ build — កាត់ចេញដើម្បីរហ័ស និងសុវត្ថិភាព
SKIP_DIRS = {".git", ".gradle", ".idea", "build", "out", "node_modules",
             ".cxx", ".kotlin", ".externalNativeBuild"}
SKIP_NAMES = {".DS_Store", "Thumbs.db"}

_SETTINGS = ("settings.gradle", "settings.gradle.kts")
_BUILD = ("build.gradle", "build.gradle.kts")
_SOURCE_EXTS = (".kt", ".java", ".c", ".cc", ".cpp", ".cxx")
_GRADLE_TEXTS = (".gradle", ".gradle.kts")


class ZipError(ValueError):
    """ZIP មិនត្រឹមត្រូវ — សារជាភាសាខ្មែរ សម្រាប់បង្ហាញដល់អ្នកប្រើ។"""


def _parts(name: str) -> list[str]:
    raw = name.replace("\\", "/")
    if raw.startswith("/") or re.match(r"^[A-Za-z]:", raw):
        raise ZipError(f"ZIP មាន path មិនត្រឹមត្រូវ: {name}")
    parts = [p for p in raw.split("/") if p not in ("", ".")]
    if ".." in parts:
        raise ZipError(f"ZIP មាន path មិនត្រឹមត្រូវ (..): {name}")
    return parts


def extract(data: bytes) -> dict[str, bytes]:
    """ពន្លា ZIP ជា {path: bytes} ដោយការពារ zip-slip, zip-bomb និង file ច្រើនពេក។"""
    if not data:
        raise ZipError("File ZIP ទទេ")
    try:
        zf = zipfile.ZipFile(io.BytesIO(data))
    except zipfile.BadZipFile:
        raise ZipError("File នេះមិនមែន ZIP ត្រឹមត្រូវទេ")
    files: dict[str, bytes] = {}
    total = 0
    try:
        with zf:
            for info in zf.infolist():
                if info.is_dir():
                    continue
                parts = _parts(info.filename)
                if not parts:
                    continue
                if parts[-1] in SKIP_NAMES or parts[-1].endswith(".iml"):
                    continue
                if any(p in SKIP_DIRS for p in parts[:-1]):
                    continue
                total += max(0, info.file_size)
                if total > MAX_UNZIPPED_BYTES:
                    raise ZipError("ZIP ធំពេកពេលពន្លា (អតិបរមា %d MB)"
                                   % (MAX_UNZIPPED_BYTES // (1024 * 1024)))
                if len(files) >= MAX_FILES:
                    raise ZipError("ZIP មាន file ច្រើនពេក (អតិបរមា %d)" % MAX_FILES)
                try:
                    with zf.open(info) as fh:
                        blob = fh.read(info.file_size + 1)
                except RuntimeError:
                    raise ZipError("ZIP មាន file ដែលពាក់ password — ដោះសិនរួច upload ម្តងទៀត")
                if len(blob) > max(0, info.file_size):
                    raise ZipError("ZIP មានទិន្នន័យខុសប្រក្រតី (file ធំជាងដែលបានប្រកាស)")
                files["/".join(parts)] = blob
    except zipfile.BadZipFile:
        raise ZipError("ZIP ខូច — សាកបង្កើត ZIP ឡើងវិញ")
    if not files:
        raise ZipError("ZIP គ្មាន file ណាមួយ")
    return files


def _under(path: str, root: str) -> bool:
    return path == root or path.startswith(root + "/") if root else True


def find_root(files: dict) -> str:
    """Folder ជា root របស់ Gradle project (ជាធម្មតា "" ឬ top folder មួយ)។"""
    dirs = {}
    for path in files:
        d, _, base = path.rpartition("/")
        if base in _SETTINGS:
            dirs[d] = dirs.get(d, 0) + 2
        elif base in _BUILD:
            dirs[d] = dirs.get(d, 0) + 1
    if not dirs:
        return ""
    # settings.gradle ជាសញ្ញាខ្លាំងជាង; បន្ទាប់មក root ខ្លីបំផុត
    best = sorted(dirs, key=lambda d: (-dirs[d], d.count("/"), len(d)))[0]
    return best


def _text(files: dict) -> str:
    return "\n".join(blob.decode("utf-8", "replace")
                     for path, blob in files.items() if path.endswith(_GRADLE_TEXTS))


def _first(text: str, patterns) -> str:
    for pat in patterns:
        m = re.search(pat, text)
        if m:
            return m.group(1).strip()
    return ""


def inspect_zip(data: bytes) -> dict:
    """ពន្លា + ពិនិត្យ: ត្រឡប់ {files, package, summary, problems, root}។"""
    all_files = extract(data)
    root = find_root(all_files)
    files = {p[len(root) + 1:]: c for p, c in all_files.items()
             if root and p.startswith(root + "/")}
    if not files:
        files = all_files
    problems: list[str] = []
    names = set(files)
    if not any(n in names for n in _SETTINGS):
        problems.append("រកមិនឃើញ settings.gradle(.kts) នៅ root របស់ project")
    if not any(n in names for n in _BUILD):
        problems.append("រកមិនឃើញ build.gradle(.kts) នៅ root របស់ project")
    manifests = [n for n in files if n.rsplit("/", 1)[-1] == "AndroidManifest.xml"]
    if not manifests:
        problems.append("រកមិនឃើញ AndroidManifest.xml — មិនមែន Android project ទេ")
    sources = [n for n in files if n.endswith(_SOURCE_EXTS)]
    if not sources:
        problems.append("រកមិនឃើញ source .kt/.java/.cpp — គ្មានកូដឲ្យ compile")

    text = _text(files)
    app_id = _first(text, [
        r"""applicationId\s*=?\s*["']([A-Za-z0-9_.]+)""",
        r"""namespace\s*=?\s*["']([A-Za-z0-9_.]+)""",
    ])
    compile_sdk = _first(text, [r"compileSdk(?:Version)?\s*=?\s*(\d+)",
                                r"compileSdkVersion\s+(\d+)"])
    min_sdk = _first(text, [r"minSdk(?:Version)?\s*=?\s*(\d+)",
                            r"minSdkVersion\s+(\d+)"])
    wrapper = ""
    prop = files.get("gradle/wrapper/gradle-wrapper.properties")
    if prop:
        m = re.search(rb"gradle-([0-9.]+)-(?:all|bin)\.zip", prop or b"")
        wrapper = m.group(1).decode() if m else ""

    summary = {
        "root": root or "(zip root)",
        "files": len(files),
        "sources": len(sources),
        "manifests": len(manifests),
        "app_id": app_id or "—",
        "compile_sdk": compile_sdk or "—",
        "min_sdk": min_sdk or "—",
        "gradlew": "gradlew" in names,
        "wrapper": wrapper or "—",
    }
    package = app_id if re.match(r"^[a-z][a-z0-9_]{1,30}(\.[a-z][a-z0-9_]{1,30}){1,3}$",
                                 app_id or "") else "org.pyapp.zipbuild"
    return {"files": files, "package": package, "summary": summary,
            "problems": problems, "root": root}
