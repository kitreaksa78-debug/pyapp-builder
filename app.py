#!/usr/bin/env python3
"""PyApp Builder — សាយសរសេរដោយ Python សម្រាប់បង្កើត APK ពី app Kivy។

អ្នកសរសេរ (ឬកែ) main.py ក្នុងទំព័រ រួចចុច «Build APK»។
ការ build ពិតប្រាកដធ្វើឡើងដោយ GitHub Actions (buildozer + Android SDK)
ព្រោះ buildozer ត្រូវការ SDK/NDK ជាង 2GB ដែលមិនអាចរត់លើ hosting ឥតគិតថ្លៃបាន។
"""
from __future__ import annotations

import io
import json
import os
import re
import secrets
import shutil
import subprocess
import threading
import time
import urllib.error
import urllib.request
import zipfile
from datetime import datetime, timezone
from pathlib import Path

from flask import (Flask, abort, jsonify, redirect, render_template, request,
                   send_file, url_for)
from werkzeug.exceptions import HTTPException

from javaapp import java_project_files, validate_java_fields

ROOT = Path(__file__).resolve().parent
JOBS = ROOT / "jobs"
JOBS.mkdir(exist_ok=True)

GH_REPO = os.environ.get("GH_REPO", "").strip()
GH_TOKEN = os.environ.get("GH_TOKEN", "").strip()
# workflow សម្រាប់ Python (Kivy) និង Java (native Gradle)
PY_WORKFLOW = (os.environ.get("GH_WORKFLOW", "build-apk.yml") or "build-apk.yml").strip()
JAVA_WORKFLOW = "build-java-apk.yml"
GH_BRANCH = (os.environ.get("GH_BRANCH", "main") or "main").strip()

DEFAULT_APP = (ROOT / "templates" / "app_template.py").read_text(encoding="utf-8")
DEFAULT_JAVA_MESSAGE = "app នេះបង្កើតដោយ PyApp Builder — សរសេរដោយ Java ដំណើរការលើ Android ដោយផ្ទាល់។"

# អក្សរខ្មែរមានសញ្ញាស្រៈ (combining marks) ដែល \w មិនទទួល — ដូច្នេះបិទតែអក្សរដែលធ្វើឲ្យ file/XML ខូច
NAME_RE = re.compile(r"^[^<>:\"/\\|?*\x00-\x1f]{1,48}$", re.UNICODE)
PKG_RE = re.compile(r"^[a-z][a-z0-9_]{1,30}(\.[a-z][a-z0-9_]{1,30}){1,3}$")
ID_RE = re.compile(r"^[a-f0-9]{12}$")
MAX_CODE = 60_000

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


def validate(title: str, package: str, code: str):
    title, package = validate_common(title, package)
    code = (code or "").replace("\r\n", "\n").strip()
    if not code:
        raise ValueError("main.py ទទេ")
    if len(code) > MAX_CODE:
        raise ValueError("កូដវែងពេក (អតិបរមា 60,000 តួ)")
    return title, package, code + "\n"


def buildozer_spec(title: str, package: str, version: str = "1.0") -> str:
    domain, _, name = package.rpartition(".")
    return (
        "[app]\n"
        f"title = {title}\n"
        f"package.name = {name}\n"
        f"package.domain = {domain}\n"
        "source.dir = .\n"
        "source.include_exts = py,png,jpg,kv,atlas,json,ttf\n"
        # ការពាយឲ្យឯកសាររបស់សាយ (Flask) ចូលទៅក្នុង APK
        "source.exclude_dirs = .git,.github,.buildozer,bin,out,jobs,templates,static,__pycache__,venv\n"
        "source.exclude_patterns = README.md,*.yml,*.yaml,requirements.txt,render.yaml,.gitignore\n"
        f"version = {version}\n"
        "requirements = python3,kivy,requests\n"
        "orientation = portrait\n"
        "fullscreen = 0\n"
        "android.permissions = INTERNET\n"
        "android.accept_sdk_license = True\n"
        "android.archs = arm64-v8a\n"
        "android.api = 31\n"
        "android.minapi = 21\n"
        "android.ndk = 25b\n"
        "p4a.branch = v2024.01.21\n"
        "\n"
        "[buildozer]\n"
        "log_level = 2\n"
        "warn_on_root = 0\n"
    )


def project_files(title: str, package: str, code: str) -> dict:
    return {
        "main.py": code,
        "buildozer.spec": buildozer_spec(title, package),
        "README.md": (
            f"# {title}\n\n"
            "App Kivy នេះបង្កើតដោយ PyApp Builder។\n\n"
            "## Build ជា APK លើកុំព្យូទ័រ (Linux)\n"
            "```bash\nsudo apt install -y git zip unzip openjdk-17-jdk python3-pip\n"
            "pip install buildozer cython\nbuildozer android debug\n```\n"
            "APK នឹងនៅក្នុង `bin/`។\n"
        ),
    }


def slugify(text: str) -> str:
    s = re.sub(r"[^a-z0-9]+", "-", text.lower()).strip("-")
    return s or "kivy-app"


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


def local_build_available() -> bool:
    return bool(shutil.which("buildozer"))


# រយៈពេលជាមធ្យម (វិនាទី) វាស់ពី run ពិត — ប្រើគណនា % ពេលជំហានមួយកំពុងរត់
STEP_SECONDS = {        "Set up job": 5,
        "Checkout": 6,
        # workflow Java (Gradle)
        "Set up JDK 17": 14,
        "Android SDK": 30,
        "Set up Gradle": 18,
        "Debug keystore": 4,
        "Build APK (Java)": 130,
    "Python 3.11": 9,
    "Pin python to 3.11": 2,
    "System dependencies": 60,
    "Buildozer": 22,
    "Cache SDK/NDK and p4a builds": 15,
    "Build APK": 420,
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
    "Debug keystore": "បង្កើត key សម្រាប់ sign",
    "Build APK (Java)": "Build APK (compile Java → Android)",
    "Python 3.11": "ដំឡើង Python 3.11",
    "Pin python to 3.11": "កំណត់ Python 3.11",
    "System dependencies": "ដំឡើងកម្មវិធីប្រព័ន្ធ",
    "Buildozer": "ដំឡើង buildozer",
    "Cache SDK/NDK and p4a builds": "ស្តារ cache SDK/NDK",
    "Build APK": "Build APK (compile Python → Android)",
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
        tree = self.call(f"/repos/{self.repo}/git/trees", "POST", {
            "base_tree": base_commit["tree"]["sha"],
            "tree": [{"path": p, "mode": "100644", "type": "blob", "content": c}
                     for p, c in files.items()],
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
        wf = workflow or PY_WORKFLOW
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
def files_from_form(form) -> tuple:
    """(lang, title, package, files) ពី form របស់ទំព័រដើម។"""
    lang = (form.get("lang") or "java").strip().lower()
    if lang == "python":
        title, package, code = validate(
            form.get("title", ""), form.get("package", ""), form.get("code", ""))
        return "python", title, package, project_files(title, package, code)
    title, package = validate_common(form.get("title", ""), form.get("package", ""))
    fields = validate_java_fields(
        form.get("heading", ""), form.get("message", ""), form.get("api_url", ""),
        form.get("button_label", ""), form.get("button_url", ""))
    return "java", title, package, java_project_files(title, package, **fields)


def create_job(lang: str, title: str, package: str, files: dict) -> dict:
    job_id = secrets.token_hex(6)
    tag = "apk-" + job_id
    workflow = JAVA_WORKFLOW if lang == "java" else PY_WORKFLOW
    meta = {
        "id": job_id,
        "title": title,
        "package": package,
        "lang": lang,
        "workflow": workflow,
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
        # buildozer មានតែលើ Python — ផ្លូវ Java ត្រូវការ GitHub Actions ជានិច្ច
        "mode": ("local" if lang == "python" and local_build_available()
                 and not (GH_REPO and GH_TOKEN) else "github"),
    }
    d = JOBS / job_id
    d.mkdir(parents=True, exist_ok=True)
    for path, content in files.items():
        target = d / path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(content, encoding="utf-8")
    save_job(meta)

    if meta["mode"] == "local":
        threading.Thread(target=run_local_build, args=(meta,), daemon=True).start()
        return meta

    if not (GH_REPO and GH_TOKEN):
        meta["status"] = "error"
        meta["error"] = ("Server មិនបានកំណត់ GH_REPO / GH_TOKEN — "
                         "អ្នកអាចទាញយក project ជា ZIP ទៅ build ខ្លួនឯងបាន។")
        save_job(meta)
        return meta

    try:
        client = gh()
        client.push_branch(meta["branch"], files, f"Build {title} ({tag})")
        client.dispatch(meta["branch"], tag, workflow)
        meta["status"] = "building"
        meta["status_note"] = "បញ្ជូនទៅ GitHub Actions រួច — កំពុងតម្រៀបជួរ…"
    except Exception as exc:  # noqa: BLE001 — បង្ហាញកំហុសដល់អ្នកប្រើ
        meta["status"] = "error"
        meta["error"] = f"មិនអាចចាប់ផ្តើម build បាន: {exc}"
    save_job(meta)
    return meta


def refresh_job(meta: dict) -> dict:
    if meta["mode"] != "github" or meta["status"] not in ("building", "queued"):
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
                             f".github/workflows/{meta.get('workflow') or PY_WORKFLOW} "
                             "និង GH_TOKEN មានសិទ្ធិ workflow")
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
                               f"{meta['tag']}/app-debug.apk")
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


def run_local_build(meta: dict) -> None:
    d = JOBS / meta["id"]
    meta["status"] = "building"
    meta["status_note"] = "កំពុង build ក្នុងម៉ាស៊ីនមេ (buildozer)…"
    save_job(meta)
    try:
        proc = subprocess.run(["buildozer", "android", "debug"], cwd=d,
                              capture_output=True, text=True, timeout=3600)
        apks = sorted((d / "bin").glob("*.apk"))
        if proc.returncode == 0 and apks:
            meta["status"] = "done"
            meta["apk_url"] = url_for("download", job_id=meta["id"], _external=False)
            meta["status_note"] = apks[0].name
        else:
            meta["status"] = "error"
            tail = (proc.stderr or proc.stdout or "")[-1200:]
            meta["error"] = "buildozer បរាជ័យ: " + tail
    except Exception as exc:  # noqa: BLE001
        meta["status"] = "error"
        meta["error"] = f"buildozer error: {exc}"
    save_job(meta)


# ---------------------------------------------------------------- routes
@app.get("/")
def home():
    return render_template(
        "index.html",
        default_code=DEFAULT_APP,
        default_title="My App",
        default_package="org.pyapp.demo",
        default_heading="សួស្តី!",
        default_message=DEFAULT_JAVA_MESSAGE,
        default_api=url_for("health", _external=True),
        gh_ready=bool(GH_REPO and GH_TOKEN),
        repo=GH_REPO,
        local_build=local_build_available(),
    )


@app.post("/build")
def build():
    lang, title, package, files = files_from_form(request.form)
    meta = create_job(lang, title, package, files)
    return redirect(url_for("job_page", job_id=meta["id"]))


@app.post("/project.zip")
def project_zip():
    _lang, title, _package, files = files_from_form(request.form)
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
    if meta["mode"] == "local":
        d = job_dir(job_id)
        apks = sorted((d / "bin").glob("*.apk"))
        if not apks:
            abort(404)
        return send_file(apks[0], as_attachment=True, download_name="app-debug.apk")
    return redirect(meta["apk_url"])


@app.errorhandler(ValueError)
def on_value_error(exc):
    return render_template("error.html", code=400, msg=str(exc)), 400


@app.errorhandler(HTTPException)
def on_http_error(exc):
    return render_template("error.html", code=exc.code, msg=exc.description), exc.code


@app.get("/api/health")
def health():
    return jsonify({"ok": True, "mode": "local" if local_build_available() else "github",
                    "gh_ready": bool(GH_REPO and GH_TOKEN)})


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=int(os.environ.get("PORT", "8000")), debug=False)
