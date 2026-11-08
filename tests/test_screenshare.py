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
ok("startForegroundService" in act, "បើក foreground service")
ok("registerCallback" in srv, "registerCallback មុន createVirtualDisplay (Android 14)")
ok("setOngoing(true)" in srv, "notification ជាប់ជានិច្ច")
ok("ACTION_STOP" in srv and "ប៊ូតុង" not in srv, "មានប៉ុស្តិ៍ឈប់")
ok("X-Session-Token" in srv, "app ផ្ញើ token ជាមួយ frame")
ok("screenshare/start" in srv and "/frame" in srv, "app ភ្ជាប់ទៅ session API")
ok(act.count("{") == act.count("}"), "MainActivity.kt braces balance")
ok(srv.count("{") == srv.count("}"), "ScreenShareService.kt braces balance")
ok(srv.count("(") == srv.count(")"), "ScreenShareService.kt parens balance")

import ide  # noqa: E402
text = {n: c.decode(errors="ignore") for n, c in files.items()}
ok(ide.project_kind(text) == "gradle", "ZIP → build ជា Gradle (ដូចដើម)")

print()
if fails:
    print("%d CHECK(S) FAILED" % fails)
    sys.exit(1)
print("ALL SCREEN-SHARE CHECKS PASSED")
