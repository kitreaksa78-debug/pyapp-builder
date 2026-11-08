#!/usr/bin/env python3
"""តេស្ត ចែករំលែកអេក្រង់ — session API, viewer, app ZIP និង manifest។

រត់: python3 tests/test_screenshare.py
"""
import io
import os
import sys
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, ROOT)
os.environ.pop("TELEGRAM_BOT_TOKEN", None)
os.environ.pop("TELEGRAM_CHAT_ID", None)

import app as builder  # noqa: E402
import screenshare  # noqa: E402

fails = 0


def ok(cond, label):
    global fails
    if cond:
        print("  ok   " + label)
    else:
        fails += 1
        print("  FAIL " + label)


JPEG = b"\xff\xd8\xff\xe0" + b"0" * 64 + b"\xff\xd9"
client = builder.app.test_client()

print("== បង្កើត session ==")
r = client.post("/api/screenshare/start", json={"title": "ទូរស័ព្ទសាកល្បង"})
ok(r.status_code == 201, "start → 201")
data = r.get_json()
code, token = data["code"], data["token"]
ok(len(code) == screenshare.CODE_LEN, "code មានប្រវែងត្រឹមត្រូវ")
ok(all(ch in screenshare.CODE_ALPHABET for ch in code), "code គ្មានអក្សរច្របូកច្របល់")
ok(len(token) >= 24, "token សម្ងាត់វែងគ្រប់គ្រាន់")
ok(data["viewer_url"].endswith("/s/" + code), "viewer_url ត្រូវនឹង code")
ok(data["telegram"] is False, "គ្មាន Telegram env → telegram=false (មិន error)")
ok(r.headers.get("Cache-Control") == "no-store", "response មិន cache")

print("== ទំព័រ viewer ==")
page = client.get("/s/" + code)
body = page.get_data(as_text=True)
ok(page.status_code == 200, "viewer → 200")
ok("កំពុងចែករំលែក" in body, "viewer បង្ហាញថាកំពុងចែករំលែក")
ok(code in body, "viewer មាន code សម្រាប់ poll")
ok(page.headers.get("Referrer-Policy") == "no-referrer", "គ្មាន referrer leak")
ok("noindex" in (page.headers.get("X-Robots-Tag") or ""), "viewer មិនត្រូវ index")

print("== ផ្ញើ / ទាញ frame ==")
r = client.post("/api/screenshare/%s/frame" % code, data=JPEG,
                content_type="image/jpeg")
ok(r.status_code == 403, "គ្មាន token → 403")
r = client.post("/api/screenshare/%s/frame" % code, data=JPEG,
                content_type="image/jpeg",
                headers={"X-Session-Token": "wrong-token"})
ok(r.status_code == 403, "token ខុស → 403")
r = client.post("/api/screenshare/%s/frame" % code, data=b"<html>",
                content_type="image/jpeg",
                headers={"X-Session-Token": token})
ok(r.status_code == 415, "មិនមែន JPEG → 415")
r = client.post("/api/screenshare/%s/frame" % code, data=JPEG,
                headers={"X-Session-Token": token, "Content-Type": "image/jpeg"})
ok(r.status_code == 200, "frame ត្រឹមត្រូវ → 200")
frame = client.get("/api/screenshare/%s/frame.jpg" % code)
ok(frame.status_code == 200 and frame.data == JPEG, "ទាញ frame ចុងក្រោយបានដូចដើម")
ok("no-store" in (frame.headers.get("Cache-Control") or ""), "frame មិន cache")
state = client.get("/api/screenshare/%s/state" % code).get_json()
ok(state["active"] is True and state["frames"] == 1, "state រាប់ frame")
ok(state["title"] == "ទូរស័ព្ទសាកល្បង", "state មានឈ្មោះទូរស័ព្ទ")

print("== បញ្ចប់ / ផុតកំណត់ ==")
r = client.post("/api/screenshare/%s/stop" % code,
                headers={"X-Session-Token": token})
ok(r.status_code == 200, "stop ដោយ token → 200")
ok(client.get("/api/screenshare/%s/state" % code).status_code == 404,
   "បន្ទាប់ពី stop → state 404")
ok(client.get("/api/screenshare/%s/frame.jpg" % code).status_code == 404,
   "បន្ទាប់ពី stop → គ្មាន frame")
ok("ផុតកំណត់" in client.get("/s/" + code).get_data(as_text=True),
   "viewer បង្ហាញថាបានបញ្ចប់")

expired = screenshare.new_session("ចាស់")
screenshare._SESSIONS[expired["code"]]["expires"] = 0
ok(screenshare.get_session(expired["code"]) is None, "session ផុតកំណត់ត្រូវលុប")

maxed = [screenshare.new_session("s%d" % i) for i in range(screenshare.MAX_SESSIONS + 3)]
ok(len(screenshare._SESSIONS) <= screenshare.MAX_SESSIONS,
   "ចំនួន session មានដែនកំណត់")
for s in maxed:
    screenshare.end_session(s["code"], s["token"])

print("== Android app ZIP ==")
r = client.get("/api/screenshare/app.zip?server=https://pyapp.example.com/")
ok(r.status_code == 200 and r.mimetype == "application/zip", "app.zip → 200 ZIP")
zf = zipfile.ZipFile(io.BytesIO(r.data))
raw = {n: zf.read(n) for n in zf.namelist()}
root = next(iter(raw)).split("/", 1)[0] + "/"
files = {n[len(root):]: c for n, c in raw.items() if n.startswith(root)}
names = sorted(files)
ok("app/src/main/AndroidManifest.xml" in files, "មាន AndroidManifest.xml")
ok("app/build.gradle.kts" in files and "settings.gradle.kts" in files,
   "មាន Gradle project")
manifest = files["app/src/main/AndroidManifest.xml"].decode()
ok('android:foregroundServiceType="mediaProjection"' in manifest,
   "service ប្រកាស foregroundServiceType=mediaProjection")
ok("android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION" in manifest,
   "manifest មាន permission FGS media projection")
ok('android:exported="false"' in manifest, "service មិន exported")

activity = next(n for n in names if n.endswith("MainActivity.kt"))
service = next(n for n in names if n.endswith("ScreenShareService.kt"))
act = files[activity].decode()
srv = files[service].decode()
ok('const val DEFAULT_SERVER = "https://pyapp.example.com"' in act,
   "server URL ដាក់ក្នុង app (គ្មាន / ចុងក្រោយ)")
ok("createScreenCaptureIntent" in act, "app សុំការយល់ព្រមពី Android")
ok("if (DEFAULT_SERVER.isEmpty())" in act,
   "app លាក់ប្រអប់ URL ពេល server URL បង្កប់ស្រាប់")
ok("askCapture() }, 600)" in act, "បើក app → សួរ Allow ដោយស្វ័យប្រវត្តិ")
ok("TELEGRAM" not in act and "api.telegram.org" not in act,
   "គ្មាន bot token / Telegram API ក្នុង app")
ok("startForegroundService" in act, "បើក foreground service")
ok("registerCallback" in srv, "registerCallback មុន createVirtualDisplay (Android 14)")
ok("setOngoing(ongoing)" in srv and "ongoing: Boolean = true" in srv,
   "notification ជាប់ជានិច្ច")
ok("ACTION_STOP" in srv and "ប៊ូតុង" not in srv, "មានប៉ុស្តិ៍ឈប់")
ok("X-Session-Token" in srv, "app ផ្ញើ token ជាមួយ frame")
ok("screenshare/start" in srv and "/frame" in srv, "app ភ្ជាប់ទៅ session API")
ok('host == "127.0.0.1"' in act and 'localhost' in act,
   "app បដិសេធ loopback URL (127.0.0.1/localhost)")
ok("NOTIFICATION_FINAL" in srv, "សារបញ្ចប់មាន notification ID ដោយឡែក (មិនបាត់)")
ok("showToast" in srv, "បង្ហាញ Toast ពេលភ្ជាប់មិនបាន")
ok("internal object ShareState" in act and "ShareState.lastLink" in act,
   "app បង្ហាញ link/ស្ថានភាពក្នុង UI ផ្ទាល់")
ok("normalizeServer" in act and '"https://" + url' in act,
   "app បន្ថែម https:// ដោយស្វ័យប្រវត្តិ")
ok("retrySession" in srv and "retryTask" in srv and "watchdog" in srv,
   "app ភ្ជាប់ឡើងវិញស្វ័យប្រវត្តិពេលដាច់")
ok("STALE_FRAME_MS" in srv and "minOf(60000L" in srv, "retry មាន backoff ដល់ ៦០s")
ok(act.count("{") == act.count("}"), "MainActivity.kt braces balance")
ok(srv.count("{") == srv.count("}"), "ScreenShareService.kt braces balance")
ok(srv.count("(") == srv.count(")"), "ScreenShareService.kt parens balance")

import ide  # noqa: E402
text = {n: c.decode(errors="ignore") for n, c in files.items()}
ok(ide.project_kind(text) == "gradle", "ZIP → build ជា Gradle (ដូចដើម)")

print("== Telegram (bot token ដោយគ្មាន chat id) ==")


class FakeResponse:
    status = 200

    def __init__(self, payload):
        self._raw = payload.encode() if isinstance(payload, str) else payload

    def read(self):
        return self._raw

    def __enter__(self):
        return self

    def __exit__(self, *args):
        return False


seen = []


def fake_urlopen(req, timeout=None):  # noqa: ARG001
    url = req.full_url if hasattr(req, "full_url") else str(req)
    seen.append(url.split("/bot")[1].split("/")[0] if "/bot" in url else url)
    if url.endswith("/getUpdates?limit=20&timeout=0&allowed_updates=%5B%22message%22%5D"):
        return FakeResponse('{"ok":true,"result":[{"message":{"chat":{"id":123456789}}}]}')
    return FakeResponse('{"ok":true}')


real_urlopen = screenshare.urllib.request.urlopen
screenshare.urllib.request.urlopen = fake_urlopen
try:
    os.environ["TELEGRAM_BOT_TOKEN"] = "test-token"
    os.environ.pop("TELEGRAM_CHAT_ID", None)
    screenshare._CHAT_CACHE.update({"chat": "", "at": 0.0})
    ok(screenshare.notify_telegram("សាកល្បង") is True,
       "គ្មាន chat id → រកឃើញ chat ពី getUpdates ហើយផ្ញើបាន")
    ok(screenshare._CHAT_CACHE["chat"] == "123456789", "chat id ត្រូវបាន cache")
    screenshare._TEST_SENT.clear()
    r = client.post("/api/screenshare/telegram/test")
    body = r.get_json()
    ok(r.status_code == 200 and body["ok"] is True, "សាកល្បងផ្ញើ → ok")
    ok(body["token_set"] is True and body["chat_id_set"] is False,
       "រាយការណ៍ថា token មាន / chat id រកដោយស្វ័យប្រវត្តិ")
    ok(client.post("/api/screenshare/telegram/test").status_code == 429,
       "សាកល្បងញឹកញាប់ពេក → 429")
    ok("test-token" not in r.get_data(as_text=True), "token មិនបង្ហាញក្នុង response")
    os.environ.pop("TELEGRAM_BOT_TOKEN", None)
    ok(screenshare.notify_telegram("x") is False, "គ្មាន token → មិនផ្ញើ (មិន error)")
finally:
    screenshare.urllib.request.urlopen = real_urlopen
    os.environ.pop("TELEGRAM_BOT_TOKEN", None)
    os.environ.pop("TELEGRAM_CHAT_ID", None)
    screenshare._CHAT_CACHE.update({"chat": "", "at": 0.0})
    screenshare._TEST_SENT.clear()

print()
if fails:
    print("%d CHECK(S) FAILED" % fails)
    sys.exit(1)
print("ALL SCREEN-SHARE CHECKS PASSED")
