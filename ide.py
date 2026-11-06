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
from languages import (all_extensions, binary_extensions, language_list,
                       monaco_map, text_extensions)

MAX_FILES = 300
MAX_FILE_BYTES = 5 * 1024 * 1024          # 5 MB / file
MAX_TOTAL_BYTES = 30 * 1024 * 1024        # 30 MB / project
MAX_PATH_LEN = 180

# បញ្ជី extension មកពី languages.py — ប្រភពតែមួយសម្រាប់ backend + editor។
# បន្ថែមភាសាថ្មីនៅ languages.py ហើយ API/editor ទទួលបានដោយស្វ័យប្រវត្តិ។
TEXT_EXT = text_extensions()
BINARY_EXT = binary_extensions()
ALLOWED_EXT = all_extensions()

DATA_URL_RE = re.compile(r"^data:([\w.+-]+/[\w.+-]+)?;base64,(.*)$", re.DOTALL)


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
            stripped_root = roots.pop()
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
    return out


def create_blueprint(create_job, validate_common, repo: str, token: str):
    bp = Blueprint("ide", __name__)

    @bp.get("/api/ide/info")
    def ide_info():
        return jsonify({
            "modes": list(MODES),
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
            title, package = validate_common(data.get("title", ""), data.get("package", ""))
            files = decode_files(data.get("files") or {})
            mode = str(data.get("mode") or "webview").lower()
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
            project = android_project_files(
                title, package, mode, files, icon=icon, version=version,
                build=build_no, permissions=[str(p) for p in perms],
                orientation=str(data.get("orientation") or "unspecified"),
                online=bool(data.get("online", True)))
            meta = create_job(title, package, project, artifact=artifact)
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
