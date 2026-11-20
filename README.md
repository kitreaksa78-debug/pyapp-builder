# Pool Overlay Full App v3

Configured API Endpoint: `https://pool-027f.onrender.com/api/calculate`

## What's Fixed in v3
- Resolves `415 Unsupported Media Type: Content-Type was not 'application/json'` by sending `application/json` (with base64 image + width/height) first, with automatic fallback to `multipart/form-data` if needed.
- Added `ApiClient.java` with response parsing helpers for `shots` and `lines` (top-level or nested inside `data`).
