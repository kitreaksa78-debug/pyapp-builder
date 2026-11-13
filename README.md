# 8 Ball Pool Overlay Menu (Android Java)

This project displays a separate Android floating overlay above other apps. It does not modify the 8 Ball Pool APK or inject code into the game.

## Included
- NORMAL, TRICKSHOT, 9 BALL and HIDE controls from the supplied project.
- A persistent circular **8** floating handle. Tap it to show or hide the panel while 8 Ball Pool is open.
- Overlay permission flow.
- Existing manually positioned visual guide lines from the original source.

## Build requirements
- Android Studio with JDK 17
- Android SDK Platform 36
- Gradle wrapper 8.14.3 (included)
- Android Gradle Plugin 8.13.0

Open this folder as a Gradle project, sync, then choose **Build > Build APK(s)**.

## Device setup
1. Install the APK.
2. Open the app and allow **Display over other apps**.
3. The game is launched and the overlay service starts.
4. Tap the circular **8** handle to show/hide the control panel. HIDE hides the panel; the handle remains available.
5. Use the system notification to stop the overlay service.

## Important
- The guide positions are manually adjustable and are not detected from live game frames.
- They are not guaranteed to match every screen size or table layout; the dimensions in `app/src/main/res/values/dimens.xml` may need adjustment.
- This project does not automate shots, access game memory, bypass game protections, or alter the original game APK.


## Groq Vision practice backend
See `GROQ_VISION_RENDER_SETUP.md`. The backend supports user-submitted image analysis for offline practice. The Android helper still needs to be wired to an explicit image-selection flow and tested on-device.
