# Pool Overlay Robot (Android Project - v3)

Updated `ScreenCaptureService.java` to handle both `"status": "success"` and `"status": "error"` responses from `https://pool-overlay.onrender.com/api/calculate`:
- Shows a `Toast` (`"API: " + errorMsg`) on the UI thread when `status` is `"error"`.
- Calls `drawLineOnOverlay(0, 0, 0, 0)` on the UI thread to clear stale overlay lines when an error occurs.
