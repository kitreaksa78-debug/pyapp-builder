# Groq App Setup

## What this source now includes
- In-app Groq API key field.
- AES/GCM encryption using Android Keystore for the locally saved key.
- Model selector.
- Test Connection button and Connected/Failed status.
- Image picker and image upload to the HTTPS backend for general visual description.
- Backend receives the key per request and does not store it or print it to logs.

## Deploy backend
Deploy the `render-backend` directory as a Render Web Service:
- Build command: `npm install`
- Start command: `npm start`
- Health check: `/health`

The Android app currently points to `https://pyapp-builder.onrender.com` in
`GroqApiClient.java`. Change `BACKEND_BASE_URL` to the HTTPS URL of the service
that actually deploys this `render-backend/server.js`.

## App use
1. Install/build the app.
2. Enter your Groq API key in the app.
3. Tap Save Key Securely.
4. Select a vision-capable model available to your Groq account.
5. Tap Test Groq Connection.
6. Choose an image and tap Analyze.
7. Tap Start Floating Overlay if you want the existing floating controls.

## Security notes
- The key is encrypted at rest with an Android Keystore AES-256-GCM key.
- The key must be sent to the backend over HTTPS for requests. Use only a backend you control.
- Do not share APKs containing a pre-filled key.
- The backend does not persist the supplied key.
- If the phone is reset/uninstalled, encrypted app data may be lost.
- This image-analysis feature provides general scene descriptions and practice notes. It does not calculate or display competitive shot-aim trajectories or automate game controls.

## Build
Use JDK 17 and the included Gradle wrapper. This ZIP has not been built in this environment; run a build in Android Studio or your build service and inspect the build log.
