#!/usr/bin/env python3
"""PyApp Builder — សាយសរសេរដោយ Python សម្រាប់បង្កើត APK ពី app **C# (.NET for Android)**។

អ្នកសរសេរ (ឬកែ) `MainActivity.cs` ក្នុងទំព័រ រួចចុច «Build APK»។
ការ build ពិតប្រាកដធ្វើឡើងដោយ GitHub Actions (.NET SDK + Android SDK)
ព្រោះ hosting ឥតគិតថ្លៃមិនអាចដំឡើង .NET/Android SDK បានទេ។
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

from flask import (Flask, abort, jsonify, redirect, render_template, request,
                   send_file, url_for)
from werkzeug.exceptions import HTTPException

from csapp import DEFAULT_CS_SOURCE, cs_project_files, validate_cs_code
from javaapp import MAX_ICON_BYTES

ROOT = Path(__file__).resolve().parent
JOBS = ROOT / "jobs"
JOBS.mkdir(exist_ok=True)
FILES = ROOT / "files"
FILES.mkdir(exist_ok=True)

GH_REPO = os.environ.get("GH_REPO", "").strip()
GH_TOKEN = os.environ.get("GH_TOKEN", "").strip()
GH_WORKFLOW = (os.environ.get("GH_WORKFLOW", "build-csharp-apk.yml")
               or "build-csharp-apk.yml").strip()
GH_BRANCH = (os.environ.get("GH_BRANCH", "main") or "main").strip()

# អក្សរខ្មែរមានសញ្ញាស្រៈ (combining marks) ដែល \w មិនទទួល — ដូច្នេះបិទតែអក្សរដែលធ្វើឲ្យ file/XML ខូច
NAME_RE = re.compile(r"^[^<>:\"/\\|?*\x00-\x1f]{1,48}$", re.UNICODE)
PKG_RE = re.compile(r"^[a-z][a-z0-9_]{1,30}(\.[a-z][a-z0-9_]{1,30}){1,3}$")
ID_RE = re.compile(r"^[a-f0-9]{12}$")
# ឈ្មោះ file សម្រាប់និពន្ធកូដ (nano) — គ្មាន / ឬ ..
FILE_RE = re.compile(r"^[A-Za-z0-9_.\-]{1,64}$")
MAX_FILE_BYTES = 400_000

app = Flask(__name__)


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
    "Set up .NET 10": 18,
    "Set up JDK 17": 14,
    "Install Android workload": 75,
    "Android SDK": 30,
    "Signature key": 4,
    "Build APK (C#)": 150,
    "Collect APK": 4,
    "Upload artifact": 8,
    "Publish release": 9,
}
STEP_KH = {
    "Set up job": "រៀបចំ runner",
    "Checkout": "ទាញកូដ",
    "Set up .NET 10": "ដំឡើង .NET 10 SDK",
    "Set up JDK 17": "ដំឡើង JDK 17",
    "Install Android workload": "ដំឡើង .NET Android workload",
    "Android SDK": "ដំឡើង Android SDK",
    "Signature key": "ពិនិត្យ key សម្រាប់ sign",
    "Build APK (C#)": "Build APK (compile C# → Android)",
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

    def push_branch(self, branch: str, files: dict, message: str) -> str:
        base = self.call(f"/repos/{self.repo}/git/ref/heads/{GH_BRANCH}")["object"]["sha"]
        base_commit = self.call(f"/repos/{self.repo}/git/commits/{base}")
        entries = []
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
            "base_tree": base_commit["tree"]["sha"],
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

    def dispatch(self, branch: str, tag: str) -> None:
        self.call(f"/repos/{self.repo}/actions/workflows/{GH_WORKFLOW}/dispatches", "POST",
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
    """(title, package, files) ពី form របស់ទំព័រដើម (បូក logo បើបាន upload)។"""
    title, package = validate_common(form.get("title", ""), form.get("package", ""))
    code = validate_cs_code(form.get("cs_code", ""))
    icon = None
    upload = uploads.get("icon") if uploads else None
    if upload is not None and (upload.filename or "").strip():
        icon = upload.read(MAX_ICON_BYTES + 1)
        if not icon:
            icon = None
        elif len(icon) > MAX_ICON_BYTES:
            raise ValueError("រូប logo ធំពេក (អតិបរមា 5 MB)")
    return title, package, cs_project_files(title, package, code, icon)


def create_job(title: str, package: str, files: dict) -> dict:
    job_id = secrets.token_hex(6)
    tag = "apk-" + job_id
    meta = {
        "id": job_id,
        "title": title,
        "package": package,
        "tag": tag,
        "status": "queued",
        "status_note": "",
        "error": "",
        "apk_url": "",
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
        client.push_branch(meta["branch"], files, f"Build {title} ({tag})")
        client.dispatch(meta["branch"], tag)
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
            meta["apk_url"] = (f"https://github.com/{GH_REPO}/releases/download/"
                               f"{meta['tag']}/app.apk")
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
@app.get("/")
def home():
    return render_template(
        "index.html",
        default_cs=DEFAULT_CS_SOURCE,
        default_title="My App",
        default_package="org.pyapp.demo",
        gh_ready=bool(GH_REPO and GH_TOKEN),
        repo=GH_REPO,
    )


@app.post("/build")
def build():
    title, package, files = files_from_form(request.form, request.files)
    meta = create_job(title, package, files)
    return redirect(url_for("job_page", job_id=meta["id"]))


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
        "error": meta["error"], "apk_url": meta["apk_url"], "run_url": meta["run_url"],
        "title": meta["title"],
        "progress": meta.get("progress") or {"percent": 0, "step": "", "steps": [], "eta": 0},
    })


@app.get("/dl/<job_id>")
def download(job_id: str):
    meta = refresh_job(load_job(job_id))
    if meta["status"] != "done":
        abort(409, description="APK មិនទាន់រួចរាល់")
    return redirect(meta["apk_url"])


@app.errorhandler(ValueError)
def on_value_error(exc):
    return render_template("error.html", code=400, msg=str(exc)), 400


@app.errorhandler(HTTPException)
def on_http_error(exc):
    return render_template("error.html", code=exc.code, msg=exc.description), exc.code


@app.get("/api/health")
def health():
    return jsonify({"ok": True, "gh_ready": bool(GH_REPO and GH_TOKEN),
                    "workflow": GH_WORKFLOW})


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=int(os.environ.get("PORT", "8000")), debug=False)
