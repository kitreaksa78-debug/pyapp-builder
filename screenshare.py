#!/usr/bin/env python3
"""ចែករំលែកអេក្រង់ទូរស័ព្ទទៅ browser — ដោយមានការយល់ព្រមពីម្ចាស់ទូរស័ព្ទ។

លំហូរ៖
1. ម្ចាស់ទូរស័ព្ទចុច «ចាប់ផ្តើមចែករំលែក» ក្នុង app → Android បង្ហាញ dialog យល់ព្រម
2. app បង្កើត session នៅទីនេះ → link ខ្លី (ផុតកំណត់) ផ្ញើទៅ bot Telegram របស់ម្ចាស់
3. app ផ្ញើ frame JPEG រៀងរាល់ ~0.5s; ទីនេះរក្សាតែ frame ចុងក្រោយ (គ្មានប្រវត្តិ)
4. browser បើក link → មើលអេក្រង់ live; ម្ចាស់ទូរស័ព្ទឈប់បានពេលណាក៏បាន

គ្មានការចាប់ផ្តើមដោយសម្ងាត់ទេ៖ service ត្រូវមាន notification ជាប់, អ្នកកាន់
ទូរស័ព្ទជាអ្នកចុចយល់ព្រម, ហើយ session ត្រូវការ token សម្រាប់ app ផ្ញើ frame។
Frame មិនត្រូវសរសេរចុះ disk ឡើយ — រក្សាក្នុង memory តែប៉ុណ្ណោះ។
"""
from __future__ import annotations

import json
import logging
import os
import secrets
import threading
import time
import urllib.error
import urllib.request

from flask import (Blueprint, Response, jsonify, make_response, render_template,
                   request)

LOG = logging.getLogger(__name__)

# គ្មាន I/O/0/1 — ងាយអាន និងវាយតាមទូរស័ព្ទ
CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
CODE_LEN = 10
MAX_FRAME_BYTES = 3 * 1024 * 1024
MAX_SESSIONS = 50
DEFAULT_TTL = 1800
JPEG_MAGIC = b"\xff\xd8\xff"

_LOCK = threading.Lock()
_SESSIONS: dict[str, dict] = {}
# chat ចុងក្រោយដែលបានផ្ញើសារទៅ bot — បើគ្មាន TELEGRAM_CHAT_ID
_CHAT_CACHE: dict = {"chat": "", "at": 0.0}
CHAT_CACHE_SECONDS = 600
_BOT_CACHE: dict = {"username": "", "at": 0.0}
# មូលហេតុដែលរក chat មិនឃើញ — បង្ហាញលើទំព័រ setup
_DISCOVER: dict = {"reason": ""}
_TEST_SENT: dict[str, float] = {}


# ---------------------------------------------------------------- session store
def _ttl() -> int:
    """អាយុកាល session (វិនាទី) — `SCREENSHARE_TTL`, លំនាំដើម 30 នាទី។"""
    try:
        value = int(os.environ.get("SCREENSHARE_TTL", "") or DEFAULT_TTL)
    except ValueError:
        return DEFAULT_TTL
    return max(60, min(value, 6 * 3600))


def _new_code() -> str:
    return "".join(secrets.choice(CODE_ALPHABET) for _ in range(CODE_LEN))


def _prune(now: float) -> None:
    """លុប session ដែលផុតកំណត់ ឬបានបញ្ចប់ (ហៅពេលកាន់ `_LOCK`)។"""
    for code in [c for c, s in _SESSIONS.items()
                 if s["ended"] or now > s["expires"]]:
        _SESSIONS.pop(code, None)


def new_session(title: str = "") -> dict:
    """បង្កើត session ថ្មី → dict ដែលមាន code + token (សម្ងាត់)។"""
    now = time.time()
    with _LOCK:
        _prune(now)
        while len(_SESSIONS) >= MAX_SESSIONS:
            oldest = min(_SESSIONS, key=lambda c: _SESSIONS[c]["created"])
            _SESSIONS.pop(oldest, None)
        session = {
            "code": _new_code(),
            "token": secrets.token_urlsafe(24),
            "title": title,
            "created": now,
            "seen": now,
            "expires": now + _ttl(),
            "frames": 0,
            "frame": None,
            "ended": False,
        }
        _SESSIONS[session["code"]] = session
        return session


def get_session(code: str) -> dict | None:
    """session ដែលនៅមានជីវិត — ផុតកំណត់/បញ្ចប់ → None។"""
    code = (code or "").strip().upper()
    if not code:
        return None
    now = time.time()
    with _LOCK:
        session = _SESSIONS.get(code)
        if session is None:
            return None
        if session["ended"] or now > session["expires"]:
            _SESSIONS.pop(code, None)
            return None
        return session


def _same_token(session: dict, token: str) -> bool:
    return bool(token) and secrets.compare_digest(session["token"], token)


def store_frame(code: str, token: str, data: bytes) -> str:
    """រក្សា frame ចុងក្រោយ → `ok` / `missing` / `forbidden`។"""
    session = get_session(code)
    if session is None:
        return "missing"
    if not _same_token(session, token):
        return "forbidden"
    now = time.time()
    with _LOCK:
        session["frame"] = data
        session["frames"] += 1
        session["seen"] = now
        session["expires"] = now + _ttl()  # នៅរស់ពេល app កំពុងផ្ញើ
    return "ok"


def end_session(code: str, token: str) -> str:
    """បញ្ចប់ session (ពី app) → `ok` / `missing` / `forbidden`។"""
    session = get_session(code)
    if session is None:
        return "missing"
    if not _same_token(session, token):
        return "forbidden"
    with _LOCK:
        session["ended"] = True
        session["frame"] = None
        _SESSIONS.pop(session["code"], None)
    return "ok"


# ---------------------------------------------------------------- Telegram + link
def _viewer_root() -> str:
    """origin សម្រាប់ link — `SCREENSHARE_BASE_URL` ឬ request បច្ចុប្បន្ន។"""
    base = (os.environ.get("SCREENSHARE_BASE_URL") or "").strip()
    if base:
        return base.rstrip("/") + "/"
    root = request.url_root
    forwarded = (request.headers.get("X-Forwarded-Proto") or "").lower()
    if forwarded == "https" and root.startswith("http://"):
        root = "https://" + root[len("http://"):]
    return root


def _recent_chat_id(token: str) -> str:
    """chat id ចុងក្រោយដែលបានផ្ញើសារទៅ bot (getUpdates) — cache ១០ នាទី។

    ជួយឲ្យអ្នកប្រើកំណត់តែ bot token; គាត់គ្រាន់តែចុច Start/ផ្ញើសារទៅ bot
    ម្តង ហើយ server ដឹងថាត្រូវផ្ញើទៅណា។ `TELEGRAM_CHAT_ID` នៅតែអាចកំណត់
    បាន ដើម្បីជ្រើស chat ជាក់លាក់ (ឧ. group)។
    """
    now = time.time()
    cached = str(_CHAT_CACHE.get("chat") or "")
    if cached and now - float(_CHAT_CACHE.get("at") or 0) < CHAT_CACHE_SECONDS:
        return cached
    url = (f"https://api.telegram.org/bot{token}/getUpdates"
           "?limit=20&timeout=0&allowed_updates=%5B%22message%22%5D")
    try:
        with urllib.request.urlopen(url, timeout=8) as resp:
            data = json.loads(resp.read() or b"{}")
    except urllib.error.HTTPError as exc:
        _DISCOVER["reason"] = ("webhook" if exc.code == 409 else
                              "token" if exc.code in (401, 403, 404) else
                              "http_%d" % exc.code)
        LOG.warning("Telegram getUpdates failed: HTTP %s", exc.code)
        return ""
    except (urllib.error.URLError, OSError, ValueError) as exc:
        _DISCOVER["reason"] = "network"
        LOG.warning("Telegram getUpdates failed: %s", type(exc).__name__)
        return ""
    chat = ""
    for update in data.get("result") or []:
        message = update.get("message") or update.get("edited_message") or {}
        chat_id = (message.get("chat") or {}).get("id")
        if chat_id is not None:
            chat = str(chat_id)
    if chat:
        _CHAT_CACHE.update({"chat": chat, "at": now})
        _DISCOVER["reason"] = ""
    else:
        _DISCOVER["reason"] = "no_chat"
    return chat


def bot_username() -> str:
    """@username របស់ bot (getMe) — សម្រាប់បង្ហាញ link ឲ្យអ្នកប្រើចុច Start។"""
    token = (os.environ.get("TELEGRAM_BOT_TOKEN") or "").strip()
    if not token:
        return ""
    now = time.time()
    cached = str(_BOT_CACHE.get("username") or "")
    if cached and now - float(_BOT_CACHE.get("at") or 0) < 3600:
        return cached
    try:
        with urllib.request.urlopen(
                f"https://api.telegram.org/bot{token}/getMe", timeout=8) as resp:
            data = json.loads(resp.read() or b"{}")
    except (urllib.error.URLError, OSError, ValueError) as exc:
        LOG.warning("Telegram getMe failed: %s", type(exc).__name__)
        return ""
    name = str(((data.get("result") or {}).get("username")) or "")
    if name:
        _BOT_CACHE.update({"username": name, "at": now})
    return name


def miniapp_name() -> str:
    """short name របស់ Telegram Mini App (env `TELEGRAM_MINIAPP_NAME`, ឧ. `share`)។"""
    return (os.environ.get("TELEGRAM_MINIAPP_NAME") or "").strip().lstrip("@")


def miniapp_link(code: str = "") -> str:
    """Link ទៅ Mini App — `t.me/<bot>/<app>?startapp=<code>`; គ្មាន env → ខ្សែទទេ។"""
    name = miniapp_name()
    bot = bot_username()
    if not name or not bot:
        return ""
    link = f"https://t.me/{bot}/{name}"
    if code:
        link += "?startapp=" + code.strip().upper()
    return link


def notify_telegram(text: str, button_url: str = "") -> bool:
    """ផ្ញើសារទៅ bot Telegram (env `TELEGRAM_BOT_TOKEN` + `TELEGRAM_CHAT_ID` ឬ chat ចុងក្រោយ)។

    `button_url` (បើមាន) → បន្ថែមប៊ូតុងបើក Telegram Mini App នៅក្រោមសារ។
    """
    token = (os.environ.get("TELEGRAM_BOT_TOKEN") or "").strip()
    chat = (os.environ.get("TELEGRAM_CHAT_ID") or "").strip()
    if not token:
        _DISCOVER["reason"] = "no_token"
        return False
    if not chat:
        chat = _recent_chat_id(token)
    if not chat:
        return False
    message = {
        "chat_id": chat,
        "text": text,
        "disable_web_page_preview": False,
    }
    if button_url:
        message["reply_markup"] = {"inline_keyboard": [[{
            "text": "▶️ មើលអេក្រង់ (Mini App)",
            "url": button_url,
        }]]}
    payload = json.dumps(message).encode("utf-8")
    req = urllib.request.Request(
        f"https://api.telegram.org/bot{token}/sendMessage",
        data=payload, method="POST",
        headers={"Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=8) as resp:
            return 200 <= resp.status < 300
    except (urllib.error.URLError, OSError, ValueError) as exc:
        # កុំ log URL (មាន token) — log តែប្រភេទ error
        LOG.warning("Telegram notify failed: %s", type(exc).__name__)
        return False


def _telegram_text(title: str, viewer_url: str, minutes: int,
                   miniapp: bool = False) -> str:
    name = title or "ទូរស័ព្ទ"
    text = (f"📱 ចែករំលែកអេក្រង់បានចាប់ផ្តើម — {name}\n"
            f"មើល live ក្នុង browser៖ {viewer_url}\n"
            f"link ផុតកំណត់ក្នុង {minutes} នាទី (ឬពេលម្ចាស់ទូរស័ព្ទចុចឈប់)។")
    if miniapp:
        text += "\nឬចុចប៊ូតុងខាងក្រោម → មើលក្នុង Telegram"
    return text


# ---------------------------------------------------------------- routes
def create_blueprint() -> Blueprint:
    bp = Blueprint("screenshare", __name__)

    @bp.post("/api/screenshare/start")
    def share_start():
        data = request.get_json(silent=True) or {}
        title = str(data.get("title") or "").strip()[:40]
        session = new_session(title)
        viewer_url = _viewer_root() + "s/" + session["code"]
        ttl = _ttl()
        mini = miniapp_link(session["code"])
        telegram = notify_telegram(_telegram_text(
            title, viewer_url, max(1, ttl // 60), bool(mini)), mini)
        resp = jsonify({
            "code": session["code"],
            "token": session["token"],
            "viewer_url": viewer_url,
            "ttl": ttl,
            "telegram": telegram,
            "bot": bot_username(),
            "miniapp_url": mini,
        })
        resp.headers["Cache-Control"] = "no-store"
        return resp, 201

    @bp.post("/api/screenshare/<code>/frame")
    def share_frame(code: str):
        data = request.get_data(cache=False)
        if not data or len(data) > MAX_FRAME_BYTES:
            return jsonify({"error": "frame ធំពេក ឬទទេ"}), 413
        if not data.startswith(JPEG_MAGIC):
            return jsonify({"error": "ត្រូវការ frame JPEG"}), 415
        token = request.headers.get("X-Session-Token", "")
        result = store_frame(code, token, data)
        if result == "missing":
            return jsonify({"error": "session ផុតកំណត់ ឬបានបញ្ចប់"}), 404
        if result == "forbidden":
            return jsonify({"error": "token មិនត្រឹមត្រូវ"}), 403
        resp = jsonify({"ok": True})
        resp.headers["Cache-Control"] = "no-store"
        return resp

    @bp.post("/api/screenshare/<code>/stop")
    def share_stop(code: str):
        result = end_session(code, request.headers.get("X-Session-Token", ""))
        if result == "missing":
            return jsonify({"error": "session មិនមាន"}), 404
        if result == "forbidden":
            return jsonify({"error": "token មិនត្រឹមត្រូវ"}), 403
        return jsonify({"ok": True})

    @bp.get("/api/screenshare/<code>/state")
    def share_state(code: str):
        session = get_session(code)
        if session is None:
            return jsonify({"active": False}), 404
        resp = jsonify({
            "active": True,
            "title": session["title"],
            "frames": session["frames"],
            "age": int(time.time() - session["created"]),
            "last_frame": int(time.time() - session["seen"]),
            "expires_in": max(0, int(session["expires"] - time.time())),
        })
        resp.headers["Cache-Control"] = "no-store"
        return resp

    @bp.get("/api/screenshare/<code>/frame.jpg")
    def share_latest(code: str):
        session = get_session(code)
        if session is None:
            return Response(status=404)
        frame = session["frame"]
        if not frame:
            return Response(status=204)
        resp = Response(frame, mimetype="image/jpeg")
        resp.headers["Cache-Control"] = "no-store, max-age=0"
        return resp

    @bp.get("/s/app")
    def share_miniapp():
        """ច្រកចូល Telegram Mini App — អាន `startapp` ហើយបើក `/s/<code>`។"""
        page = make_response(render_template(
            "screenshare_miniapp.html", bot=bot_username()))
        page.headers["Cache-Control"] = "no-store"
        page.headers["Referrer-Policy"] = "no-referrer"
        page.headers["X-Robots-Tag"] = "noindex, nofollow"
        return page

    @bp.get("/s/<code>")
    def share_view(code: str):
        """ទំព័រមើល live — បើកបានតែអ្នកមាន link (code ជាសម្ងាត់)។"""
        session = get_session(code)
        page = make_response(render_template(
            "screenshare.html",
            code=(code or "").strip().upper(),
            title=(session or {}).get("title") or "",
            active=session is not None,
            ttl=_ttl()))
        page.headers["Cache-Control"] = "no-store"
        page.headers["Referrer-Policy"] = "no-referrer"
        page.headers["X-Robots-Tag"] = "noindex, nofollow"
        return page

    @bp.get("/screenshare")
    def share_home():
        """ទំព័រណែនាំ + ទាញ app សម្រាប់ចែករំលែកអេក្រង់។"""
        page = make_response(render_template(
            "screenshare_setup.html",
            server=_viewer_root().rstrip("/"),
            telegram_token=bool((os.environ.get("TELEGRAM_BOT_TOKEN") or "").strip()),
            telegram_chat=(os.environ.get("TELEGRAM_CHAT_ID") or "").strip(),
            bot=bot_username(),
            found_chat=str(_CHAT_CACHE.get("chat") or ""),
            reason=str(_DISCOVER.get("reason") or ""),
            miniapp=miniapp_name(),
            ttl=_ttl()))
        page.headers["Cache-Control"] = "no-store"
        return page

    @bp.post("/api/screenshare/telegram/test")
    def share_telegram_test():
        """សាកល្បងផ្ញើសារទៅ bot (មិនបង្ហាញ token ឡើងវិញឡើយ)។"""
        ip = request.remote_addr or "?"
        now = time.time()
        if now - _TEST_SENT.get(ip, 0.0) < 15:
            return jsonify({"error": "រង់ចាំ ១៥ វិនាទី រួចព្យាយាមម្តងទៀត"}), 429
        _TEST_SENT[ip] = now
        token = bool((os.environ.get("TELEGRAM_BOT_TOKEN") or "").strip())
        sent = notify_telegram(
            "✅ សាកល្បងពី PyApp Builder — bot នេះនឹងទទួល link ចែកអេក្រង់។")
        reason = "" if sent else str(_DISCOVER.get("reason") or "")
        hints = {
            "no_token": "គ្មាន TELEGRAM_BOT_TOKEN — ដាក់វានៅ Render → Environment",
            "no_chat": "bot មិនទាន់ទទួលសារពីអ្នក — បើក bot ហើយចុច Start ម្តង",
            "webhook": "bot មាន webhook — បើក https://api.telegram.org/bot<TOKEN>/deleteWebhook ក្នុង browser រួចសាកម្តងទៀត",
            "token": "TOKEN មិនត្រឹមត្រូវ/ត្រូវ revoke — យក token ថ្មីពី BotFather",
            "network": "server ភ្ជាប់ Telegram មិនបាន — សាកម្តងទៀតបន្តិចទៀត",
        }
        resp = jsonify({"ok": sent, "token_set": token, "reason": reason,
                        "bot": bot_username(),
                        "chat_id_set": bool((os.environ.get("TELEGRAM_CHAT_ID")
                                            or "").strip()),
                        "hint": "" if sent else hints.get(
                            reason, "មិនទាន់បានផ្ញើទេ — សាកម្តងទៀត")})
        resp.headers["Cache-Control"] = "no-store"
        return resp

    @bp.get("/api/screenshare/app.zip")
    def share_app_zip():
        """ZIP របស់ Android app (import ចូល IDE រួច Build APK បានភ្លាម)។"""
        from ide import build_zip  # noqa: WPS433 — ជៀសវាង circular import
        from screenshare_app import app_project_files

        server = str(request.args.get("server") or _viewer_root()).strip()
        if not server.lower().startswith(("http://", "https://")):
            server = _viewer_root()
        package = str(request.args.get("package") or "org.pyapp.screenshare").strip()
        title = str(request.args.get("title") or "Screen Share").strip()[:40]
        try:
            files = app_project_files(server.rstrip("/"), title, package)
        except ValueError as exc:
            return jsonify({"error": str(exc)}), 400
        buf = build_zip(files, "screenshare")
        return Response(buf.getvalue(), mimetype="application/zip",
                        headers={"Content-Disposition":
                                 'attachment; filename="screenshare.zip"',
                                 "Cache-Control": "no-store"})

    return bp
