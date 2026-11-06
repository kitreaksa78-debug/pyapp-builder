#!/usr/bin/env python3
"""តេស្ត R2 storage — ប្រើ S3-compatible server ក្នុងស្រុក (fake) ដើម្បីផ្ទៀងផ្ទាត់
put / head / presigned-get / delete ពិតតាម boto3 ដោយមិនប៉ះ bucket ពិត។

bucket ពិត (Cloudflare) ត្រូវសាកលើ deploy ដោយ POST /api/r2/selftest។

រត់: python3 tests/test_r2.py
"""
from __future__ import annotations

import json
import os
import sys
import threading
import urllib.parse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))

fails = 0


def check(cond, label, extra=""):
    global fails
    if cond:
        print("  ok   %s" % label)
    else:
        fails += 1
        print("  FAIL %s %s" % (label, extra))


class FakeS3(BaseHTTPRequestHandler):
    store: dict = {}
    puts = 0
    lock = threading.Lock()

    def log_message(self, *a):  # ស្ងាត់
        pass

    def _key(self):
        path = urllib.parse.urlsplit(self.path).path
        parts = path.lstrip("/").split("/", 1)
        return parts[1] if len(parts) > 1 else ""

    def do_PUT(self):
        n = int(self.headers.get("Content-Length") or 0)
        data = self.rfile.read(n) if n else b""
        with self.lock:
            FakeS3.store[self._key()] = (data, self.headers.get("Content-Type", "binary/octet-stream"))
            FakeS3.puts += 1
        self.send_response(200)
        self.send_header("ETag", '"fake"')
        self.send_header("Content-Length", "0")
        self.end_headers()

    def do_HEAD(self):
        item = FakeS3.store.get(self._key())
        if not item:
            self.send_response(404)
            self.send_header("Content-Length", "0")
            self.end_headers()
            return
        self.send_response(200)
        self.send_header("Content-Type", item[1])
        self.send_header("Content-Length", str(len(item[0])))
        self.send_header("ETag", '"fake"')
        self.end_headers()

    def do_GET(self):
        item = FakeS3.store.get(self._key())
        if not item:
            self.send_response(404)
            self.send_header("Content-Length", "0")
            self.end_headers()
            return
        self.send_response(200)
        self.send_header("Content-Type", item[1])
        self.send_header("Content-Length", str(len(item[0])))
        self.end_headers()
        self.wfile.write(item[0])

    def do_DELETE(self):
        with self.lock:
            FakeS3.store.pop(self._key(), None)
        self.send_response(204)
        self.send_header("Content-Length", "0")
        self.end_headers()


R2_ENV = ("R2_ENDPOINT", "R2_ACCOUNT_ID", "R2_BUCKET_NAME",
          "R2_ACCESS_KEY_ID", "R2_SECRET_ACCESS_KEY", "R2_REGION")


def clear_r2_env():
    for k in R2_ENV:
        os.environ.pop(k, None)


def main() -> int:
    clear_r2_env()
    import r2

    print("== គ្មាន env (fallback ទៅ GitHub) ==")
    check(not r2.configured(), "configured() = False")
    info = r2.info()
    check(info["configured"] is False and len(info["missing"]) == 4,
          "info() រាយ env ដែលខ្វះ ៤", info)
    check(info["env"]["present"]["R2_BUCKET_NAME"] is False
          and info["env"]["other_r2_keys"] == [],
          "env diagnostics៖ ឈ្មោះតែប៉ុណ្ណោះ, present=false")
    check("hint" in info and info["hint"], "info() មាន hint ពេលមិនកំណត់")
    res = r2.selftest()
    check(res["ok"] is False and "R2_BUCKET_NAME" in res["error"],
          "selftest បរាជ័យជាមួយសារច្បាស់", res.get("error"))

    import app as web
    c = web.app.test_client()
    r = c.get("/api/r2/status")
    check(r.status_code == 200 and r.get_json()["configured"] is False,
          "GET /api/r2/status → 200 configured=false")
    r = c.post("/api/r2/selftest")
    check(r.status_code == 503 and r.get_json()["ok"] is False,
          "POST /api/r2/selftest → 503 ពេលគ្មាន env")
    check(c.get("/api/r2/selftest").status_code == 503,
          "GET /api/r2/selftest → 503 (សាកពី browser បាន)")
    health = c.get("/api/health").get_json()
    check(health.get("r2_ready") is False and health.get("ok") is True,
          "/api/health r2_ready=false")

    print("== SigV4 — ផ្ទៀងផ្ទាត់នឹង test vector ផ្លូវការរបស់ AWS ==")
    from datetime import datetime, timezone
    qs = r2._presigned_query(
        "examplebucket.s3.amazonaws.com", "/test.txt",
        "AKIAIOSFODNN7EXAMPLE", "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY",
        "us-east-1", 86400, "GET",
        datetime(2013, 5, 24, 0, 0, 0, tzinfo=timezone.utc))
    want_sig = "aeeed9bbccd4d02ee5c0109b86d86835f995330da4c265957d157751f604d404"
    check("X-Amz-Signature=" + want_sig in qs,
          "signature ត្រូវនឹង AWS SigV4 test vector", qs[-80:])
    check("X-Amz-Credential=AKIAIOSFODNN7EXAMPLE%2F20130524%2Fus-east-1%2Fs3%2Faws4_request" in qs,
          "credential scope ត្រឹមត្រូវ")

    print("== S3-compatible server ក្នុងស្រុក ==")
    srv = ThreadingHTTPServer(("127.0.0.1", 0), FakeS3)
    threading.Thread(target=srv.serve_forever, daemon=True).start()
    endpoint = "http://127.0.0.1:%d" % srv.server_address[1]
    os.environ.update({
        "R2_ENDPOINT": endpoint,
        "R2_BUCKET_NAME": "test-bucket",
        "R2_ACCESS_KEY_ID": "test-key-id",
        "R2_SECRET_ACCESS_KEY": "test-secret-value",
        "R2_REGION": "auto",
    })

    check(r2.configured(), "configured() = True ពេលមាន env គ្រប់")
    info = r2.info()
    check(info["bucket"] == "test-bucket" and info["missing"] == [],
          "info() ត្រឹមត្រូវ")
    blob = json.dumps(info)
    check("test-secret-value" not in blob and "test-key-id" not in blob,
          "info() មិនលាតត្រដាង credential")
    check(info["env"]["present"]["R2_BUCKET_NAME"] is True
          and info["env"]["present"]["R2_SECRET_ACCESS_KEY"] is True,
          "env diagnostics ឃើញ var ពេលកំណត់")
    os.environ["R2_BUCKETS"] = "typo"
    check(r2.env_report()["other_r2_keys"] == ["R2_BUCKETS"],
          "ឈ្មោះវាយខុសត្រូវរាយក្នុង other_r2_keys")
    os.environ.pop("R2_BUCKETS")

    print("== selftest (put → head → presigned get → delete) ==")
    res = r2.selftest()
    check(res["ok"] is True, "selftest ជោគជ័យ", res.get("error"))
    check(res.get("checks") == ["put", "head", "presigned-get", "delete",
                                "confirmed-delete"],
          "ជំហានទាំង ៥ រត់ពិត", res.get("checks"))
    check(not any(k.startswith("_selftest/") for k in FakeS3.store),
          "object សាកត្រូវលុបចេញ (គ្មានសំរាម)")
    r = c.get("/api/r2/selftest")
    check(r.status_code == 200 and r.get_json()["ok"] is True,
          "GET /api/r2/selftest → 200 ok ពេលកំណត់ត្រូវ", r.get_json())

    print("== put / head / presigned / delete ==")
    r2.put_bytes("artifacts/x/app.apk", b"APKBYTES", "application/vnd.android.package-archive")
    meta = r2.head("artifacts/x/app.apk")
    check(meta and meta["bytes"] == 8, "head ត្រូវនឹងទំហំ", meta)
    check(r2.head("artifacts/x/missing.apk") is None, "head គ្មាន → None")
    url = r2.presigned_url("artifacts/x/app.apk", 300)
    check("X-Amz-Signature=" in url and "/test-bucket/artifacts/x/app.apk" in url,
          "presigned URL មាន signature + path ត្រឹមត្រូវ")
    check(r2.fetch("artifacts/x/app.apk") == b"APKBYTES",
          "ទាញយកតាម presigned URL បានត្រឹមត្រូវ")
    r2.delete("artifacts/x/app.apk")
    check(r2.head("artifacts/x/app.apk") is None, "delete លុបពិត")

    print("== /dl/<job> ប្រើ R2 ជា artifact storage ==")
    jid = "a1b2c3d4e5f6"
    jobs = web.JOBS / jid
    jobs.mkdir(parents=True, exist_ok=True)
    (jobs / "meta.json").write_text(json.dumps({
        "id": jid, "title": "T", "package": "org.pyapp.demo", "tag": "apk-" + jid,
        "status": "done", "status_note": "", "error": "", "artifact": "both",
        "apk_url": "https://github.com/o/r/releases/download/apk-%s/app.apk" % jid,
        "aab_url": "https://github.com/o/r/releases/download/apk-%s/app.aab" % jid,
        "run_url": "", "progress": {"percent": 100, "step": "", "steps": [], "eta": 0},
        "created": 0, "checked": 0, "branch": "build/" + jid,
    }), encoding="utf-8")

    calls = []

    def fake_fetch(job_id, kind):
        calls.append(kind)
        return b"PK-APK-" + kind.encode()

    web._artifact_bytes = fake_fetch
    FakeS3.puts = 0
    r = c.get("/dl/%s?type=apk" % jid)
    loc = r.headers.get("Location", "")
    check(r.status_code == 302 and "X-Amz-Signature=" in loc,
          "APK → presigned R2 redirect", loc[:80])
    check(FakeS3.puts == 1 and calls == ["apk"], "upload ទៅ R2 ម្តង (ហើយ cache)")
    r = c.get("/dl/%s?type=apk" % jid)
    check(FakeS3.puts == 1 and r.status_code == 302,
          "ហៅម្តងទៀតមិន upload ស្ទួន")

    r = c.get("/dl/%s?type=aab" % jid)
    check(r.status_code == 302 and "app.aab" in r.headers.get("Location", ""),
          "AAB → presigned R2 redirect")
    check(calls == ["apk", "aab"], "ទាញ AAB ដាច់ដោយឡែក")

    print("== fallback ពេល R2/GitHub មានបញ្ហា ==")
    def boom(job_id, kind):
        raise OSError("network down")

    web._artifact_bytes = boom
    jid2 = "001122334455"
    d2 = web.JOBS / jid2
    d2.mkdir(parents=True, exist_ok=True)
    meta2 = json.loads((jobs / "meta.json").read_text(encoding="utf-8"))
    meta2.update({"id": jid2, "apk_url": "", "aab_url": ""})
    (d2 / "meta.json").write_text(json.dumps(meta2), encoding="utf-8")
    r = c.get("/dl/%s?type=apk" % jid2)
    check(r.status_code == 302 and "releases/download/apk-" + jid2 in r.headers.get("Location", ""),
          "R2 បរាជ័យ → ត្រឡប់ទៅ GitHub release", r.headers.get("Location", "")[:80])

    srv.shutdown()
    for d in (jobs, d2):
        for p in d.iterdir():
            p.unlink()
        d.rmdir()
    clear_r2_env()

    print("")
    if fails:
        print("FAILED %d checks" % fails)
        return 1
    print("ALL R2 CHECKS PASSED")
    return 0


if __name__ == "__main__":
    import urllib.request  # noqa: E402 — ត្រូវការក្នុងតេស្ត presigned GET
    sys.exit(main())
