#!/usr/bin/env python3
"""PyApp Builder — សាយសរសេរដោយ Python សម្រាប់បង្កើត APK ពី app **JavaScript**។

អ្នកសរសេរ UI ដោយ JavaScript + CSS ក្នុងទំព័រ (ផ្ទាំងមើល render ពីកូដផ្ទាល់)
រួចចុច «Build APK»។ ការ build ពិតប្រាកដធ្វើឡើងដោយ GitHub Actions
(Kotlin WebView wrapper + Android SDK) ព្រោះ hosting ឥតគិតថ្លៃ build APK ខ្លួនឯងមិនបានទេ។
"""
from __future__ import annotations

import base64
import io
import json
import os
import re
import secrets
import time
import urllib.error
import urllib.request
import zipfile
from datetime import datetime, timezone
from pathlib import Path

import r2

from flask import (Flask, abort, jsonify, make_response, redirect,
                   render_template, request, send_file, send_from_directory,
                   url_for)
from werkzeug.exceptions import HTTPException

from androidutil import MAX_ICON_BYTES
import zipbuild
from ide import create_blueprint as ide_blueprint
from webapp import (DEFAULT_CSS_SOURCE, DEFAULT_JS_SOURCE, build_app_html,
                    validate_css, validate_js, web_project_files)

ROOT = Path(__file__).resolve().parent
JOBS = ROOT / "jobs"
JOBS.mkdir(exist_ok=True)
FILES = ROOT / "files"
FILES.mkdir(exist_ok=True)
SPA_DIR = ROOT / "static" / "dashboard"

GH_REPO = os.environ.get("GH_REPO", "").strip()
GH_TOKEN = os.environ.get("GH_TOKEN", "").strip()
GH_WORKFLOW = (os.environ.get("GH_WORKFLOW", "build-kotlin-apk.yml")
               or "build-kotlin-apk.yml").strip()
GH_WORKFLOW_ZIP = (os.environ.get("GH_WORKFLOW_ZIP", "build-zip-apk.yml")
                   or "build-zip-apk.yml").strip()
GH_BRANCH = (os.environ.get("GH_BRANCH", "main") or "main").strip()

# អក្សរខ្មែរមានសញ្ញាស្រៈ (combining marks) ដែល \w មិនទទួល — ដូច្នេះបិទតែអក្សរដែលធ្វើឲ្យ file/XML ខូច
NAME_RE = re.compile(r"^[^<>:\"/\\|?*\x00-\x1f]{1,48}$", re.UNICODE)
PKG_RE = re.compile(r"^[a-z][a-z0-9_]{1,30}(\.[a-z][a-z0-9_]{1,30}){1,3}$")
ID_RE = re.compile(r"^[a-f0-9]{12}$")
# ឈ្មោះ file សម្រាប់និពន្ធកូដ (nano) — គ្មាន / ឬ ..
FILE_RE = re.compile(r"^[A-Za-z0-9_.\-]{1,64}$")
MAX_FILE_BYTES = 400_000

app = Flask(__name__)
# ទទួល ZIP ធំ (ZIP → APK) — លើសពីនេះ Flask ត្រឡប់ 413
app.config["MAX_CONTENT_LENGTH"] = zipbuild.MAX_ZIP_BYTES + 2 * 1024 * 1024


# ---------------------------------------------------------------- helpers
def validate_common(title: str, package: str):
    title = (title or "").strip()
    package = (package or "").strip()
    if not NAME_RE.match(title):
        raise ValueError("ឈ្មោះ app មិនត្រឹមត្រូវ (១–៤៨ តួអក្សរ)")
    if not PKG_RE.match(package):
        raise ValueError("package មិនត្រឹមត្រូវ — ឧ. org.pyapp.demo")
    return title, package


def file_path(name: str) -> Path:
    if not FILE_RE.match(name or "") or name in (".", ".."):
        raise ValueError("ឈ្មោះ file មិនត្រឹមត្រូវ — ប្រើ a-z, 0-9, _, -, .")
    return FILES / name


def slugify(text: str) -> str:
    s = re.sub(r"[^a-z0-9]+", "-", text.lower()).strip("-")
    return s or "android-app"


def job_dir(job_id: str) -> Path:
    if not ID_RE.match(job_id or ""):
        abort(404)
    d = JOBS / job_id
    if not d.is_dir():
        abort(404)
    return d


def load_job(job_id: str) -> dict:
    d = job_dir(job_id)
    return json.loads((d / "meta.json").read_text(encoding="utf-8"))


def save_job(meta: dict) -> None:
    d = JOBS / meta["id"]
    d.mkdir(parents=True, exist_ok=True)
    tmp = d / "meta.json.tmp"
    tmp.write_text(json.dumps(meta, ensure_ascii=False, indent=2), encoding="utf-8")
    tmp.replace(d / "meta.json")


# រយៈពេលជាមធ្យម (វិនាទី) វាស់ពី run ពិត — ប្រើគណនា % ពេលជំហានមួយកំពុងរត់
STEP_SECONDS = {
    "Set up job": 5,
    "Checkout": 6,
    "Set up JDK 17": 14,
    "Android SDK": 30,
    "Set up Gradle": 20,
    "Signature key": 4,
    "Build APK (Kotlin)": 130,
    "Collect APK": 4,
    "Upload artifact": 8,
    "Publish release": 9,
}
STEP_KH = {
    "Set up job": "រៀបចំ runner",
    "Checkout": "ទាញកូដ",
    "Set up JDK 17": "ដំឡើង JDK 17",
    "Android SDK": "ដំឡើង Android SDK",
    "Set up Gradle": "ដំឡើង Gradle",
    "Signature key": "ពិនិត្យ key សម្រាប់ sign",
    "Build APK (Kotlin)": "Build APK (compile Kotlin → Android)",
    "Collect APK": "ប្រមូល APK",
    "Upload artifact": "ផ្ទុកឡើង artifact",
    "Publish release": "បង្កើត release",
}


def _parse_ts(value: str | None) -> float:
    """'2026-11-02T09:14:08Z' → epoch វិនាទី (0 បើខូច)។"""
    if not value:
        return 0.0
    try:
        return datetime.strptime(value, "%Y-%m-%dT%H:%M:%SZ").replace(
            tzinfo=timezone.utc).timestamp()
    except ValueError:
        return 0.0


# ---------------------------------------------------------------- github
class GitHub:
    def __init__(self, repo: str, token: str):
        self.repo = repo
        self.token = token

    def call(self, path: str, method: str = "GET", payload=None):
        url = "https://api.github.com" + path
        body = json.dumps(payload).encode() if payload is not None else None
        req = urllib.request.Request(url, data=body, method=method)
        req.add_header("Authorization", "Bearer " + self.token)
        req.add_header("Accept", "application/vnd.github+json")
        req.add_header("User-Agent", "pyapp-builder")
        if body is not None:
            req.add_header("Content-Type", "application/json")
        with urllib.request.urlopen(req, timeout=45) as resp:
            raw = resp.read()
        return json.loads(raw) if raw else {}

    def push_branch(self, branch: str, files: dict, message: str,
                    *, clean: bool = False) -> str:
        """បង្កើត branch សម្រាប់ build។ `clean=True` → គ្មាន file របស់ repo មេ
        (លើកលែង `.github/` ដែល workflow ត្រូវការ) — project អ្នកប្រើស្អាត
        មិនលាយ settings.gradle/build.gradle ចាស់របស់ repo។"""
        base = self.call(f"/repos/{self.repo}/git/ref/heads/{GH_BRANCH}")["object"]["sha"]
        base_commit = self.call(f"/repos/{self.repo}/git/commits/{base}")
        entries = []
        if clean:
            tree = self.call(
                f"/repos/{self.repo}/git/trees/{base_commit['tree']['sha']}?recursive=1")
            for e in tree.get("tree", []):
                if e.get("type") == "blob" and e["path"].startswith(".github/"):
                    entries.append({"path": e["path"], "mode": e["mode"],
                                    "type": "blob", "sha": e["sha"]})
        for path, content in files.items():
            if isinstance(content, (bytes, bytearray)):
                # រូបភាព (logo) → ត្រូវបង្កើត blob ជា base64
                blob = self.call(f"/repos/{self.repo}/git/blobs", "POST", {
                    "content": base64.b64encode(content).decode("ascii"),
                    "encoding": "base64",
                })
                entries.append({"path": path, "mode": "100644", "type": "blob",
                                "sha": blob["sha"]})
            else:
                entries.append({"path": path, "mode": "100644", "type": "blob",
                                "content": content})
        tree = self.call(f"/repos/{self.repo}/git/trees", "POST", {
            **({"base_tree": base_commit["tree"]["sha"]} if not clean else {}),
            "tree": entries,
        })
        commit = self.call(f"/repos/{self.repo}/git/commits", "POST", {
            "message": message, "tree": tree["sha"], "parents": [base],
        })
        try:
            self.call(f"/repos/{self.repo}/git/refs", "POST",
                      {"ref": f"refs/heads/{branch}", "sha": commit["sha"]})
        except urllib.error.HTTPError as exc:
            if exc.code != 422:
                raise
            self.call(f"/repos/{self.repo}/git/refs/heads/{branch}", "PATCH",
                      {"sha": commit["sha"], "force": True})
        return commit["sha"]

    def dispatch(self, branch: str, tag: str, workflow: str = "") -> None:
        wf = (workflow or GH_WORKFLOW).strip()
        self.call(f"/repos/{self.repo}/actions/workflows/{wf}/dispatches", "POST",
                  {"ref": branch, "inputs": {"tag": tag}})

    def find_run(self, branch: str, not_before: float):
        data = self.call(f"/repos/{self.repo}/actions/runs?per_page=20")
        for run in data.get("workflow_runs", []):
            if run.get("head_branch") != branch:
                continue
            created = run.get("created_at", "")
            try:
                ts = time.mktime(time.strptime(created, "%Y-%m-%dT%H:%M:%SZ")) - time.timezone
            except ValueError:
                ts = not_before
            if ts + 5 >= not_before:
                return run
        return None

    def run_progress(self, run: dict) -> dict:
        """% ពិតប្រាកដពីជំហានរបស់ workflow (conclusion + started_at របស់ GitHub)។"""
        steps: list[dict] = []
        if run.get("id"):
            try:
                data = self.call(f"/repos/{self.repo}/actions/runs/{run['id']}/jobs")
            except Exception:  # noqa: BLE001 — % គ្រាន់ជាព័ត៌មានបន្ថែម
                data = {}
            for job in data.get("jobs") or []:
                steps.extend(job.get("steps") or [])
        now = time.time()
        total = done = remaining = 0.0
        current = ""
        shown: list[dict] = []
        for step in steps:
            name = step.get("name") or ""
            if name.startswith("Post ") or name == "Complete job":
                continue
            expected = STEP_SECONDS.get(name, 20)
            total += expected
            if step.get("conclusion"):
                done += expected
                shown.append({"name": STEP_KH.get(name, name), "state": "done"})
            elif step.get("status") == "in_progress":
                started = _parse_ts(step.get("started_at")) or now
                frac = min(0.95, max(0.0, (now - started) / expected))
                done += expected * frac
                remaining += expected * (1 - frac)
                current = name
                shown.append({"name": STEP_KH.get(name, name), "state": "now"})
            else:
                remaining += expected
                shown.append({"name": STEP_KH.get(name, name), "state": "wait"})
        if run.get("status") == "completed":
            return {"percent": 100, "step": "", "steps": shown, "eta": 0}
        percent = int(round(100 * done / total)) if total else 0
        return {"percent": max(0, min(99, percent)),
                "step": STEP_KH.get(current, current),
                "steps": shown, "eta": int(remaining)}


def gh() -> GitHub:
    return GitHub(GH_REPO, GH_TOKEN)


# ---------------------------------------------------------------- job flow
def files_from_form(form, uploads=None) -> tuple:
    """(title, package, files) ពី form របស់ទំព័រដើម (JavaScript + logo បើបាន upload)។"""
    title, package = validate_common(form.get("title", ""), form.get("package", ""))
    js = validate_js(form.get("app_js", ""))
    css = validate_css(form.get("app_css", ""))
    icon = None
    upload = uploads.get("icon") if uploads else None
    if upload is not None and (upload.filename or "").strip():
        icon = upload.read(MAX_ICON_BYTES + 1)
        if not icon:
            icon = None
        elif len(icon) > MAX_ICON_BYTES:
            raise ValueError("រូប logo ធំពេក (អតិបរមា 5 MB)")
    return title, package, web_project_files(title, package, js, css, icon)


def create_job(title: str, package: str, files: dict,
               artifact: str = "apk", *, kind: str = "app",
               workflow: str = "", clean: bool = False) -> dict:
    job_id = secrets.token_hex(6)
    tag = "apk-" + job_id
    artifact = (artifact or "apk").strip().lower()
    if artifact not in ("apk", "aab", "both"):
        artifact = "apk"
    meta = {
        "id": job_id,
        "title": title,
        "package": package,
        "tag": tag,
        "status": "queued",
        "status_note": "",
        "error": "",
        "artifact": artifact,
        "kind": kind,
        "workflow": (workflow or GH_WORKFLOW).strip(),
        "apk_url": "",
        "aab_url": "",
        "run_url": "",
        "progress": {"percent": 0, "step": "", "steps": [], "eta": 0},
        "created": time.time(),
        "checked": 0.0,
        "branch": f"build/{job_id}",
    }
    d = JOBS / job_id
    d.mkdir(parents=True, exist_ok=True)
    for path, content in files.items():
        target = d / path
        target.parent.mkdir(parents=True, exist_ok=True)
        if isinstance(content, (bytes, bytearray)):
            target.write_bytes(content)
        else:
            target.write_text(content, encoding="utf-8")
    save_job(meta)

    if not (GH_REPO and GH_TOKEN):
        meta["status"] = "error"
        meta["error"] = ("Server មិនបានកំណត់ GH_REPO / GH_TOKEN — "
                         "អ្នកអាចទាញយក project ជា ZIP ទៅ build ខ្លួនឯងបាន។")
        save_job(meta)
        return meta

    try:
        client = gh()
        client.push_branch(meta["branch"], files, f"Build {title} ({tag})", clean=clean)
        client.dispatch(meta["branch"], tag, meta["workflow"])
        meta["status"] = "building"
        meta["status_note"] = "បញ្ជូនទៅ GitHub Actions រួច — កំពុងតម្រៀបជួរ…"
    except Exception as exc:  # noqa: BLE001 — បង្ហាញកំហុសដល់អ្នកប្រើ
        meta["status"] = "error"
        meta["error"] = f"មិនអាចចាប់ផ្តើម build បាន: {exc}"
    save_job(meta)
    return meta


def refresh_job(meta: dict) -> dict:
    if meta["status"] not in ("building", "queued"):
        return meta
    if time.time() - meta.get("checked", 0) < 5:
        return meta
    meta["checked"] = time.time()
    try:
        run = gh().find_run(meta["branch"], meta["created"])
    except Exception as exc:  # noqa: BLE001
        meta["status_note"] = f"រង់ចាំ GitHub… ({exc})"
        save_job(meta)
        return meta
    if not run:
        waited = time.time() - meta["created"]
        if waited > 150:
            meta["status"] = "error"
            meta["error"] = ("រកមិនឃើញ workflow run — ពិនិត្យថា repo មាន "
                             f".github/workflows/{GH_WORKFLOW} និង GH_TOKEN "
                             "មានសិទ្ធិ workflow")
        else:
            meta["status_note"] = "រង់ចាំ workflow ចាប់ផ្តើម…"
        save_job(meta)
        return meta
    meta["run_url"] = run.get("html_url", "")
    meta["status_note"] = f"{run.get('status')} · {run.get('conclusion') or 'កំពុងរត់'}"
    if run.get("status") == "completed":
        if run.get("conclusion") == "success":
            meta["status"] = "done"
            meta["apk_url"] = release_asset_url(meta["id"], "apk")
            # ZIP→APK workflow ផ្សព្វផ្សាយតែ app.apk (គ្មាន AAB)
            meta["aab_url"] = ("" if meta.get("kind") == "zip"
                               else release_asset_url(meta["id"], "aab"))
            meta["progress"] = {"percent": 100, "step": "", "steps": [], "eta": 0}
        else:
            meta["status"] = "error"
            meta["error"] = f"Build បរាជ័យ ({run.get('conclusion')}) — មើល log ក្នុង GitHub"
    else:
        try:
            meta["progress"] = gh().run_progress(run)
        except Exception:  # noqa: BLE001
            pass
    save_job(meta)
    return meta


# ---------------------------------------------------------------- routes
def _ide_asset_version():
    """mtime របស់ ide.css/js — បញ្ចូល ?v= ដើម្បីកុំឲ្យ browser ប្រើ CSS/JS ចាស់។"""
    latest = 0
    for name in ("ide.css", "ide.js"):
        try:
            latest = max(latest, int((ROOT / "static" / "ide" / name).stat().st_mtime))
        except OSError:
            pass
    return latest


@app.get("/")
@app.get("/ide")
def ide_page():
    """ទំព័រដើម = IDE ពេញលេញ — file explorer, Monaco, preview, APK build។"""
    resp = make_response(render_template(
        "ide.html",
        default_title="My App",
        default_package="org.pyapp.demo",
        gh_ready=bool(GH_REPO and GH_TOKEN),
        repo=GH_REPO,
        v=_ide_asset_version(),
    ))
    resp.headers["Cache-Control"] = "no-store"
    return resp


@app.get("/dashboard")
def dashboard():
    """Dashboard ចាស់ — បញ្ជូនទៅ builder ចាស់ (រក្សាតែ route ចាស់)។"""
    return redirect(url_for("home"))


@app.get("/builder")
def home():
    resp = make_response(render_template(
        "index.html",
        default_js=DEFAULT_JS_SOURCE,
        default_css=DEFAULT_CSS_SOURCE,
        default_title="My App",
        default_package="org.pyapp.demo",
        gh_ready=bool(GH_REPO and GH_TOKEN),
        repo=GH_REPO,
    ))
    # កុំឲ្យ browser cache ទំព័រចាស់ — កូដ builder ផ្លាស់ប្តូរញឹកញាប់
    resp.headers["Cache-Control"] = "no-store"
    return resp


@app.post("/api/preview")
def api_preview():
    """HTML សម្រាប់ផ្ទាំងមើល — ដូចគ្នាបេះបិទនឹង `app/src/main/assets/www/index.html` ក្នុង APK។"""
    data = request.get_json(silent=True) or {}
    title = str(data.get("title") or "App")[:48]
    try:
        js = validate_js(data.get("js", ""))
        css = validate_css(data.get("css", ""))
    except ValueError as exc:
        return jsonify({"error": str(exc)}), 400
    resp = make_response(build_app_html(title, js, css))
    resp.headers["Content-Type"] = "text/html; charset=utf-8"
    resp.headers["Cache-Control"] = "no-store"
    return resp


@app.post("/build")
def build():
    title, package, files = files_from_form(request.form, request.files)
    meta = create_job(title, package, files)
    return redirect(url_for("job_page", job_id=meta["id"]))


@app.post("/api/build")
def api_build():
    """JSON build endpoint សម្រាប់ dashboard (AppForge) — ត្រឡប់ job id + link។"""
    data = request.get_json(silent=True) or {}
    try:
        title, package = validate_common(data.get("title", ""), data.get("package", ""))
        version = str(data.get("version") or "").strip()
        if not re.match(r"^[0-9]{1,3}(\.[0-9]{1,3}){0,3}$", version):
            version = "1.0"
        build_no = str(data.get("build") or "").strip()
        if not (build_no.isdigit() and 0 < int(build_no) <= 999_999):
            build_no = "1"
        # UI ជា JavaScript (engine តែមួយនឹង /builder) — វេចខ្ចប់ដោយ WebView Kotlin
        if not str(data.get("app_js") or "").strip():
            raise ValueError("កូដ app ទទួល — សរសេរ app_js (JavaScript)")
        perms = data.get("permissions") or []
        if not isinstance(perms, list):
            perms = []
        files = web_project_files(title, package, validate_js(data.get("app_js")),
                                  validate_css(data.get("app_css") or ""),
                                  version=version, build=build_no,
                                  permissions=perms)
        meta = create_job(title, package, files)
    except ValueError as exc:
        return jsonify({"error": str(exc)}), 400
    return jsonify({
        "id": meta["id"],
        "status": meta["status"],
        "error": meta["error"],
        "title": meta["title"],
        "job_url": url_for("job_page", job_id=meta["id"]),
        "status_url": url_for("job_status", job_id=meta["id"]),
        "download_url": url_for("download", job_id=meta["id"]),
    }), 201


@app.get("/nano")
def nano_page():
    """និពន្ធកូដបែប nano — សរសេរបានគ្រប់ភាសា។"""
    name = (request.args.get("f") or "main.java").strip()
    if not FILE_RE.match(name):
        name = "main.java"
    target = FILES / name
    content = ""
    if target.is_file():
        content = target.read_text(encoding="utf-8", errors="replace")
    files = sorted(p.name for p in FILES.iterdir() if p.is_file())
    return render_template("nano.html", files=files, name=name, content=content)


@app.get("/api/files")
def api_files():
    items = []
    for path in sorted(FILES.iterdir()):
        if path.is_file():
            stat = path.stat()
            items.append({"name": path.name, "size": stat.st_size,
                          "mtime": int(stat.st_mtime)})
    return jsonify({"files": items})


@app.get("/api/files/<name>")
def api_file_get(name: str):
    path = file_path(name)
    if not path.is_file():
        abort(404)
    return jsonify({"name": path.name,
                    "content": path.read_text(encoding="utf-8", errors="replace")})


@app.post("/api/files/<name>")
def api_file_save(name: str):
    path = file_path(name)
    data = request.get_json(silent=True) or {}
    content = data.get("content")
    if not isinstance(content, str):
        raise ValueError("content ទាត់")
    if len(content.encode("utf-8")) > MAX_FILE_BYTES:
        raise ValueError("file ធំពេក (អតិបរមា 400KB)")
    path.write_text(content, encoding="utf-8")
    lines = content.count("\n") + (1 if content and not content.endswith("\n") else 0)
    return jsonify({"ok": True, "name": path.name, "lines": lines,
                    "bytes": len(content.encode("utf-8"))})


@app.get("/files/<name>/download")
def file_download(name: str):
    path = file_path(name)
    if not path.is_file():
        abort(404)
    return send_file(path, as_attachment=True, download_name=path.name)


@app.post("/project.zip")
def project_zip():
    title, _package, files = files_from_form(request.form, request.files)
    buf = io.BytesIO()
    base = slugify(title)
    with zipfile.ZipFile(buf, "w", zipfile.ZIP_DEFLATED) as zf:
        for path, content in files.items():
            zf.writestr(f"{base}/{path}", content)
    buf.seek(0)
    return send_file(buf, mimetype="application/zip", as_attachment=True,
                     download_name=f"{base}.zip")


@app.get("/j/<job_id>")
def job_page(job_id: str):
    meta = refresh_job(load_job(job_id))
    return render_template("job.html", job=meta)


@app.get("/api/jobs/<job_id>")
def job_status(job_id: str):
    meta = refresh_job(load_job(job_id))
    return jsonify({
        "id": meta["id"], "status": meta["status"], "note": meta["status_note"],
        "error": meta["error"], "apk_url": meta["apk_url"],
        "aab_url": meta.get("aab_url", ""), "artifact": meta.get("artifact", "apk"),
        "run_url": meta["run_url"],
        "title": meta["title"],
        "kind": meta.get("kind", "app"),
        "progress": meta.get("progress") or {"percent": 0, "step": "", "steps": [], "eta": 0},
    })


# ---------------------------------------------------------------- ZIP → APK
def _zip_upload():
    """យក ZIP ពី multipart ឬពី upload_id ដែលពិនិត្យរួច (គ្មាន upload ទីពីរ)។
    ត្រឡប់ (data, filename, error_response)។"""
    upload = request.files.get("zip")
    token = (request.form.get("upload_id") or "").strip()
    if token and upload is None:
        try:
            data, name = zipbuild.load_upload(token)
        except zipbuild.ZipError as exc:
            return None, "", (jsonify({"ok": False, "step": "upload", "expired": True,
                                       "error": str(exc)}), 410)
        return data, name, None
    if upload is None or not (upload.filename or "").strip():
        return None, "", (jsonify({"ok": False, "step": "upload",
                                   "error": "សូមជ្រើសរើស file .zip របស់ Android project"}), 400)
    data = upload.read(zipbuild.MAX_ZIP_BYTES + 1)
    if len(data) > zipbuild.MAX_ZIP_BYTES:
        return None, "", (jsonify({"ok": False, "step": "upload",
                                   "error": "ZIP ធំពេក (អតិបរមា %d MB)"
                                            % (zipbuild.MAX_ZIP_BYTES // (1024 * 1024))}), 400)
    return data, (upload.filename or "app.zip").rsplit("/", 1)[-1], None


@app.post("/api/zip/inspect")
def zip_inspect():
    """ពន្លា + ពិនិត្យ ZIP ហើយបង្ហាញបញ្ជី file ទាំងអស់ — អ្នកប្រើមើលរួចទើបចុច Build APK។"""
    data, name, err = _zip_upload()
    if err:
        return err
    try:
        info = zipbuild.inspect_zip(data)
    except zipbuild.ZipError as exc:
        return jsonify({"ok": False, "step": "validate", "error": str(exc)}), 400
    body = {"ok": not info["problems"], "step": "validate", "name": name,
            "problems": info["problems"], "info": info["summary"],
            "list": info["list"], "list_total": info["list_total"]}
    if info["problems"]:
        body["error"] = info["problems"][0]
        return jsonify(body), 400
    # ពិនិត្យរួច → រក្សា ZIP ទុក ដើម្បី Build ដោយមិន upload ម្តងទៀត
    body["upload_id"] = zipbuild.save_upload(data, name)
    return jsonify(body)


@app.post("/api/zip/build")
def zip_build():
    """Android project ZIP (ឬ upload_id) → បញ្ជូនទៅ GitHub Actions (JDK+SDK+Gradle)។"""
    data, name, err = _zip_upload()
    if err:
        return err
    try:
        info = zipbuild.inspect_zip(data)
    except zipbuild.ZipError as exc:
        return jsonify({"ok": False, "step": "validate", "error": str(exc)}), 400
    if info["problems"]:
        return jsonify({"ok": False, "step": "validate", "error": info["problems"][0],
                        "problems": info["problems"], "info": info["summary"]}), 400
    # ZIP ត្រឹមត្រូវហើយ — ឥឡូវទើបត្រូវការ GitHub Actions ដើម្បី build ពិត
    if not (GH_REPO and GH_TOKEN):
        return jsonify({"ok": False, "step": "build", "info": info["summary"],
                        "error": ("Server មិនបានកំណត់ GH_REPO / GH_TOKEN — "
                                  "build ក្នុង cloud មិនអាចធ្វើបានទេ។")}), 503
    title = re.sub(r"\.zip$", "", name, flags=re.I).strip() or "My App"
    if not NAME_RE.match(title):
        title = "Android App"
    meta = create_job(title[:48], info["package"], info["files"], artifact="apk",
                      kind="zip", workflow=GH_WORKFLOW_ZIP, clean=True)
    ok = meta["status"] != "error"
    return jsonify({"ok": ok, "step": "build", "id": meta["id"],
                    "status": meta["status"], "error": meta["error"],
                    "info": info["summary"]}), (201 if ok else 502)


@app.get("/api/jobs/<job_id>/log")
def job_log(job_id: str):
    """Build log ពិត (build.log ដែល workflow ផ្សព្វផ្សាយក្នុង release)។"""
    if not ID_RE.match(job_id or "") or not (JOBS / job_id / "meta.json").is_file():
        abort(404)
    req = urllib.request.Request(release_file_url(job_id, "build.log"))
    req.add_header("User-Agent", "pyapp-builder")
    if GH_TOKEN:
        req.add_header("Authorization", "Bearer " + GH_TOKEN)
    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            raw = resp.read(3_000_000)
    except urllib.error.HTTPError as exc:
        return jsonify({"ok": False, "error": f"មិនទាន់មាន build log (HTTP {exc.code})"}), 404
    except Exception as exc:  # noqa: BLE001
        return jsonify({"ok": False, "error": f"មិនអាចទាញ build log: {exc}"}), 502
    text = raw.decode("utf-8", "replace")
    limit = 200_000
    return jsonify({"ok": True, "log": text[-limit:], "truncated": len(text) > limit})


R2_ARTIFACT_PREFIX = "artifacts"


def _artifact_key(job_id: str, kind: str) -> str:
    ext = "aab" if (kind or "").lower() == "aab" else "apk"
    return f"{R2_ARTIFACT_PREFIX}/{job_id}/app.{ext}"


def _artifact_bytes(job_id: str, kind: str) -> bytes:
    """ទាញ APK/AAB ពី GitHub release (ប្រើ GH_TOKEN បើមាន — repo private)។"""
    req = urllib.request.Request(release_asset_url(job_id, kind))
    req.add_header("User-Agent", "pyapp-builder")
    req.add_header("Accept", "application/octet-stream")
    if GH_TOKEN:
        req.add_header("Authorization", "Bearer " + GH_TOKEN)
    with urllib.request.urlopen(req, timeout=90) as resp:
        return resp.read()


def r2_artifact_url(job_id: str, kind: str, expires: int = 3600) -> str:
    """R2 បើក: upload លើកដំបូង (បើខ្វះ) រួចត្រឡប់ presigned URL។ មិនអាច → ""។"""
    if not r2.configured():
        return ""
    key = _artifact_key(job_id, kind)
    try:
        if r2.head(key) is None:
            data = _artifact_bytes(job_id, kind)
            if not data:
                return ""
            ctype = ("application/vnd.android.package-archive"
                     if (kind or "").lower() != "aab" else "application/octet-stream")
            r2.put_bytes(key, data, ctype)
        return r2.presigned_url(key, expires)
    except Exception:  # noqa: BLE001 — R2 ខូច/គ្មានសិទ្ធិ → fallback ទៅ GitHub
        return ""


def release_file_url(job_id: str, name: str) -> str:
    return f"https://github.com/{GH_REPO}/releases/download/apk-{job_id}/{name}"


def release_asset_url(job_id: str, kind: str = "apk") -> str:
    """URL artifact ក្នុង GitHub release — កំណត់ដោយ job id ដូច្នេះមិនបាត់ពេល restart។

    `kind` = "apk" ឬ "aab" (workflow បង្កើតទំងពីរ)។
    """
    name = "app.aab" if (kind or "").lower() == "aab" else "app.apk"
    return release_file_url(job_id, name)


@app.get("/dl/<job_id>")
def download(job_id: str):
    kind = (request.args.get("type") or "").strip().lower()
    if kind not in ("apk", "aab"):
        kind = ""
    if not ID_RE.match(job_id or ""):
        abort(404)
    if not (JOBS / job_id / "meta.json").is_file():
        # Render restart/spin-down លុប jobs/ ⇒ ទាញពី release ដោយផ្ទាល់
        return redirect(release_asset_url(job_id, kind or "apk"))
    meta = refresh_job(load_job(job_id))
    if meta["status"] != "done":
        abort(409, description="Artifact មិនទាន់រួចរាល់")
    wanted = kind or meta.get("artifact") or "apk"
    if wanted == "aab":
        return redirect(r2_artifact_url(job_id, "aab")
                        or meta.get("aab_url") or release_asset_url(job_id, "aab"))
    return redirect(r2_artifact_url(job_id, "apk")
                    or meta.get("apk_url") or release_asset_url(job_id, "apk"))


app.register_blueprint(ide_blueprint(create_job, validate_common, GH_REPO, GH_TOKEN,
                                     GH_WORKFLOW_ZIP))


@app.errorhandler(ValueError)
def on_value_error(exc):
    return render_template("error.html", code=400, msg=str(exc)), 400


@app.errorhandler(HTTPException)
def on_http_error(exc):
    return render_template("error.html", code=exc.code, msg=exc.description), exc.code


@app.errorhandler(413)
def on_too_large(exc):  # noqa: ARG001
    return jsonify({"ok": False, "step": "upload",
                    "error": "File ធំពេក — ZIP អតិបរមា %d MB"
                             % (zipbuild.MAX_ZIP_BYTES // (1024 * 1024))}), 413

@app.get("/api/r2/status")
def r2_status():
    """ស្ថានភាព R2 — គ្មាន secret ត្រឡប់ឡើយ។"""
    return jsonify(r2.info())


@app.route("/api/r2/selftest", methods=["GET", "POST"])
def r2_selftest():
    """សាក R2 ពិត៖ PUT → HEAD → presigned GET → DELETE (GET សម្រាប់ browser/ទូរស័ព្ទ)។"""
    result = r2.selftest()
    return jsonify(result), (200 if result.get("ok") else 503)


@app.get("/api/health")
def health():
    return jsonify({"ok": True, "gh_ready": bool(GH_REPO and GH_TOKEN),
                    "r2_ready": r2.configured(), "workflow": GH_WORKFLOW,
                    "zip_workflow": GH_WORKFLOW_ZIP})


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=int(os.environ.get("PORT", "8000")), debug=False)
