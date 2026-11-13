# Groq Vision backend setup (offline practice image analysis)

This project includes a Render Node.js backend and an Android HTTP client helper. It is a source-code starter, **not a verified APK**. It analyzes a screenshot that the user deliberately selects for offline practice; it does not capture or draw aiming guides over a live competitive game.

## 1. Deploy a dedicated Render service

Do not assume `https://pyapp-builder.onrender.com` already runs this API: that URL was supplied as a PyApp IDE site, and its API routes have not been verified. Create a separate Render Web Service from this repository. Set **Root Directory** to `render-backend`, **Build Command** to `npm install`, and **Start Command** to `npm start`.

Set these Render environment variables:
- `GROQ_API_KEY`: your private Groq key (never put it in Android or chat).
- `GROQ_MODEL`: `qwen/qwen3.8-27b` (Groq documents this model as vision-capable).

After deployment, test `https://YOUR-SERVICE.onrender.com/health` and `/groq/status`. The status endpoint reports whether a key is configured, but does not validate the secret by exposing it.

## 2. Android client endpoint

In `GroqVisionClient.java`, replace `YOUR-DEDICATED-RENDER-SERVICE` with the dedicated service hostname. The helper sends a user-approved JPEG byte array to `/api/vision/analyze`.

## 3. Current integration limits

The original project does not include a completed MediaProjection screenshot-consent flow or a user-selected image practice screen wired to `GroqVisionClient.analyzeJpeg`. Therefore do not claim the app is fully integrated until that UI is implemented and tested on a real device. Do not send continuous screen frames.

## 4. Test with curl

```sh
curl https://YOUR-SERVICE.onrender.com/health
curl https://YOUR-SERVICE.onrender.com/groq/status
```

To test `/api/vision/analyze`, send JSON `{"image":"data:image/jpeg;base64,..."}` with a real JPEG encoded as base64.

## 5. Build

Open the project in Android Studio or AndroidIDE, sync Gradle, then build the debug APK. This environment has not built or run the APK; verify Gradle sync and test on your device.
