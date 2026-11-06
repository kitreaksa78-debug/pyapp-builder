"""Cloudflare R2 (S3-compatible) — ផ្ទុក artifact APK/AAB។

**គ្មាន key/token ក្នុង file នេះទេ។** តម្លៃទាំងអស់មកពី environment:

    R2_ENDPOINT=https://<account_id>.r2.cloudflarestorage.com   (ឬ R2_ACCOUNT_ID)
    R2_BUCKET_NAME=...
    R2_ACCESS_KEY_ID=...
    R2_SECRET_ACCESS_KEY=...
    R2_REGION=auto                                              (ស្រេចចិត្ត)

បើអវត្តមាន → `configured()` = False ហើយ app ត្រឡប់ទៅ GitHub release ដូចដើម។

ការផ្ទុក/ទាញយកប្រើ presigned URL (SigV4, stdlib សុទ្ធ) — គ្មាន dependency បន្ថែម។
"""
from __future__ import annotations

import hashlib
import hmac
import os
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from datetime import datetime, timezone
from pathlib import Path

DEFAULT_REGION = "auto"
SELFTEST_PREFIX = "_selftest/"
ALGO = "AWS4-HMAC-SHA256"
UNSIGNED = "UNSIGNED-PAYLOAD"
MAX_EXPIRES = 604800  # 7 ថ្ងៃ (ដែនកំណត់ S3)


class R2Error(RuntimeError):
    """កំហុស R2 ជាមួយ HTTP status (បើមាន) — គ្មាន secret ក្នុងសារ។"""

    def __init__(self, message: str, status: int = 0):
        super().__init__(message)
        self.status = status


def settings() -> dict:
    endpoint = (os.environ.get("R2_ENDPOINT") or "").strip().rstrip("/")
    account = (os.environ.get("R2_ACCOUNT_ID") or "").strip()
    if not endpoint and account:
        endpoint = "https://%s.r2.cloudflarestorage.com" % account
    return {
        "endpoint": endpoint,
        "bucket": (os.environ.get("R2_BUCKET_NAME") or "").strip(),
        "access_key": (os.environ.get("R2_ACCESS_KEY_ID") or "").strip(),
        "secret_key": (os.environ.get("R2_SECRET_ACCESS_KEY") or "").strip(),
        "region": (os.environ.get("R2_REGION") or DEFAULT_REGION).strip() or DEFAULT_REGION,
    }


def missing_env() -> list[str]:
    """ឈ្មោះ env var ដែលខ្វះ (គ្មានតម្លៃ) — សម្រាប់សារ error ច្បាស់លាស់។"""
    s = settings()
    gaps = []
    if not s["endpoint"]:
        gaps.append("R2_ENDPOINT (ឬ R2_ACCOUNT_ID)")
    if not s["bucket"]:
        gaps.append("R2_BUCKET_NAME")
    if not s["access_key"]:
        gaps.append("R2_ACCESS_KEY_ID")
    if not s["secret_key"]:
        gaps.append("R2_SECRET_ACCESS_KEY")
    return gaps


def configured() -> bool:
    return not missing_env()


EXPECTED_ENV = ("R2_ENDPOINT", "R2_ACCOUNT_ID", "R2_BUCKET_NAME",
                "R2_ACCESS_KEY_ID", "R2_SECRET_ACCESS_KEY", "R2_REGION")


def env_report() -> dict:
    """ឈ្មោះ env var ដែលមាន និងតម្លៃទទេ/ពេញ — **គ្មានតម្លៃត្រឡប់ឡើយ**។

    ជួយដានពេល deploy៖ បើ Render មិនបញ្ជូនវាមក `present` ទាំងអស់ជា false
    ហើយ `other_r2_keys` បង្ហាញឈ្មោះដែលវាយខុស (ឧ. R2_BUCKET ខ្វះ _NAME)។
    """
    present = {k: bool((os.environ.get(k) or "").strip()) for k in EXPECTED_ENV}
    other = sorted(k for k in os.environ
                   if k.upper().startswith("R2") and k not in EXPECTED_ENV)
    return {"present": present, "other_r2_keys": other}


def info() -> dict:
    """ស្ថានភាពសម្រាប់ UI/health check — មិនដែលត្រឡប់ secret ឡើយ។"""
    s = settings()
    host = s["endpoint"].split("://", 1)[-1] if s["endpoint"] else ""
    ready = configured()
    return {
        "configured": ready,
        "bucket": s["bucket"],
        "endpoint": host,
        "region": s["region"],
        "missing": missing_env(),
        "env": env_report(),
        "hint": "" if ready else (
            "បើបានដាក់ក្នុង Render រួច៖ ពិនិត្យឈ្មោះ key ខាងលើ, ចុច Save Changes, "
            "រួច Manual Deploy → Restart service។"),
    }


# ------------------------------------------------------------- SigV4 (stdlib)
def _enc(value: str) -> str:
    """URI-encode បែប RFC3986 (ដូច S3)។"""
    return urllib.parse.quote(str(value), safe="-_.~")


def _sha256_hex(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def _signing_key(secret: str, datestamp: str, region: str) -> bytes:
    k = hmac.new(("AWS4" + secret).encode(), datestamp.encode(), hashlib.sha256).digest()
    k = hmac.new(k, region.encode(), hashlib.sha256).digest()
    k = hmac.new(k, b"s3", hashlib.sha256).digest()
    return hmac.new(k, b"aws4_request", hashlib.sha256).digest()


def _presigned_query(host: str, path: str, access_key: str, secret_key: str,
                     region: str, expires: int, method: str,
                     now: datetime) -> str:
    """query string (រួម X-Amz-Signature) សម្រាប់ presigned request។"""
    amzdate = now.strftime("%Y%m%dT%H%M%SZ")
    datestamp = now.strftime("%Y%m%d")
    scope = "%s/%s/s3/aws4_request" % (datestamp, region)
    params = {
        "X-Amz-Algorithm": ALGO,
        "X-Amz-Credential": "%s/%s" % (access_key, scope),
        "X-Amz-Date": amzdate,
        "X-Amz-Expires": str(int(expires)),
        "X-Amz-SignedHeaders": "host",
    }
    canonical_query = "&".join("%s=%s" % (_enc(k), _enc(v)) for k, v in sorted(params.items()))
    canonical_request = "\n".join([
        method.upper(),
        urllib.parse.quote(path, safe="/-_.~"),
        canonical_query,
        "host:" + host + "\n",
        "host",
        UNSIGNED,
    ])
    string_to_sign = "\n".join([ALGO, amzdate, scope, _sha256_hex(canonical_request.encode())])
    signature = hmac.new(_signing_key(secret_key, datestamp, region),
                         string_to_sign.encode(), hashlib.sha256).hexdigest()
    return canonical_query + "&X-Amz-Signature=" + signature


def _split_endpoint(endpoint: str) -> tuple:
    if "://" not in endpoint:
        endpoint = "https://" + endpoint
    scheme, host = endpoint.split("://", 1)
    return scheme, host.rstrip("/")


def _url(key: str, method: str = "GET", expires: int = 3600) -> str:
    s = settings()
    scheme, host = _split_endpoint(s["endpoint"])
    path = "/%s/%s" % (s["bucket"], key.lstrip("/"))
    query = _presigned_query(host, path, s["access_key"], s["secret_key"],
                             s["region"], min(MAX_EXPIRES, max(60, int(expires))),
                             method, datetime.now(timezone.utc))
    return "%s://%s%s?%s" % (scheme, host, urllib.parse.quote(path, safe="/-_.~"), query)


def _http(method: str, url: str, body: bytes | None = None,
          timeout: int = 60, headers: dict | None = None):
    req = urllib.request.Request(url, data=body, method=method)
    for name, value in (headers or {}).items():
        req.add_header(name, value)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            return resp.status, resp.headers, resp.read()
    except urllib.error.HTTPError as exc:
        detail = ""
        try:
            detail = exc.read()[:200].decode("utf-8", "replace").strip()
        except Exception:  # noqa: BLE001
            pass
        raise R2Error("R2 %s → HTTP %s %s" % (method, exc.code, detail), exc.code)
    except urllib.error.URLError as exc:
        raise R2Error("R2 %s — តភ្ជាប់មិនបាន: %s" % (method, exc.reason))
    except OSError as exc:
        raise R2Error("R2 %s — បណ្តាញខូច: %s" % (method, exc))


# ------------------------------------------------------------- operations
def put_bytes(key: str, data: bytes,
              content_type: str = "application/octet-stream") -> dict:
    _http("PUT", _url(key, "PUT"), body=data,
          headers={"Content-Type": content_type,
                   "Content-Length": str(len(data))})
    return {"key": key, "bytes": len(data), "content_type": content_type}


def put_file(key: str, path, content_type: str = "application/octet-stream") -> dict:
    return put_bytes(key, Path(path).read_bytes(), content_type)


def head(key: str) -> dict | None:
    """metadata បើ object មាន, បើអត់ (404) → None។ កំហុសផ្សេង → R2Error។"""
    try:
        _status, headers, _body = _http("HEAD", _url(key, "HEAD"), timeout=30)
    except R2Error as exc:
        if exc.status in (403, 404):  # គ្មាន object (R2 អាចឆ្លើយ 403 ពេលគ្មាន key)
            return None
        raise
    return {"key": key,
            "bytes": int(headers.get("Content-Length") or 0),
            "content_type": (headers.get("Content-Type") or "")}


def presigned_url(key: str, expires: int = 3600) -> str:
    return _url(key, "GET", expires)


def delete(key: str) -> None:
    _http("DELETE", _url(key, "DELETE"), timeout=30)


def fetch(key: str, timeout: int = 60) -> bytes:
    """ទាញ object តាម presigned URL (សម្រាប់តេស្ត/ផ្ទៀងផ្ទាត់)។"""
    _status, _headers, body = _http("GET", presigned_url(key, 300), timeout=timeout)
    return body


def selftest() -> dict:
    """PUT → HEAD → presigned GET → DELETE → បញ្ជាក់ការលុប លើ bucket ពិត។"""
    started = time.time()
    if not configured():
        return {"ok": False,
                "error": "R2 មិនបានកំណត់ — ខ្វះ: " + ", ".join(missing_env()),
                "checks": [], "ms": 0}
    key = SELFTEST_PREFIX + "probe-" + uuid.uuid4().hex + ".txt"
    payload = ("appforge r2 selftest %d" % int(started)).encode()
    checks: list[str] = []
    try:
        put_bytes(key, payload, "text/plain; charset=utf-8")
        checks.append("put")
        meta = head(key)
        if not meta or meta["bytes"] != len(payload):
            raise RuntimeError("HEAD មិនត្រូវនឹង object ដែលបានផ្ទុក")
        checks.append("head")
        if fetch(key) != payload:
            raise RuntimeError("អានពី presigned URL មិនត្រូវនឹងទិន្នន័យ")
        checks.append("presigned-get")
        delete(key)
        checks.append("delete")
        if head(key) is not None:
            raise RuntimeError("DELETE មិនបានលុប object")
        checks.append("confirmed-delete")
        return {"ok": True, "checks": checks, "bucket": settings()["bucket"],
                "key": key, "bytes": len(payload),
                "ms": int((time.time() - started) * 1000)}
    except Exception as exc:  # noqa: BLE001 — បង្ហាញកំហុសពិតដល់អ្នកប្រើ
        return {"ok": False, "error": str(exc)[:400], "checks": checks,
                "bucket": settings()["bucket"], "key": key,
                "ms": int((time.time() - started) * 1000)}
