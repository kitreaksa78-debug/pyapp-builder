# Groq Vision + Render integration (starter)

## What this update adds
- Render Node.js backend at `render-backend/` with `GET /health` and `POST /api/vision/analyze`.
- Groq Vision model configurable by `GROQ_MODEL` (default `qwen/qwen3.8-27b`).
- `GroqVisionClient.java` helper for sending a JPEG screenshot to your Render endpoint.
- Android INTERNET permission.

## Deploy the backend to Render
1. Push this project folder/repository to GitHub.
2. In Render, create **New + > Web Service**, select the repository.
3. Set **Root Directory** to `render-backend`.
4. Build Command: `npm install`; Start Command: `npm start`.
5. Add Environment Variables:
   - `GROQ_API_KEY` = your private Groq key (paste it only into Render; never into Android source or chat).
   - `GROQ_MODEL` = `qwen/qwen3.8-27b`.
6. Deploy. Test `https://YOUR-SERVICE.onrender.com/health`.

## Connect Android client
1. Open `app/src/main/java/app/hack/eightballpool/GroqVisionClient.java`.
2. Replace `https://YOUR-RENDER-SERVICE.onrender.com/api/vision/analyze` with your actual Render endpoint.
3. After the user has explicitly approved screen capture and you have converted one screenshot to JPEG bytes, call:

```java
GroqVisionClient.analyzeJpeg(jpegBytes, new GroqVisionClient.Callback() {
    @Override public void onSuccess(org.json.JSONObject result) {
        // result.optJSONObject("analysis") contains normalized 0..1000 coordinates.
        // Validate confidence and table bounds before drawing any guide.
    }
    @Override public void onError(String message) {
        // Show an error/status message in the overlay.
    }
});
```

## Important current limitation
This ZIP adds the backend and Android networking client, but does **not yet wire the screenshot-capture button / MediaProjection frame pipeline into the existing overlay UI**. It is therefore not a finished live AI overlay. You must connect an explicit user-triggered screenshot capture to `analyzeJpeg`, then map returned normalized coordinates to the table view. Do not send every video frame; analyze only on user request or a modest interval to control latency and API usage.

The vision model may misidentify balls, especially when the table is tilted, obscured, low-resolution, or contains UI graphics. Use confidence thresholds and allow manual correction. This is a practice/visual-assistance tool; it does not modify the 8 Ball Pool APK or automate shots.
