# Kotlin Wrapper (JavaScript · Kotlin WebView)

App Android បង្កើតដោយ PyApp Builder — UI សរសេរដោយ **JavaScript + CSS**។

- package: `com.appforge.kotlinwrap`
- minSdk 21 · targetSdk 34 · compileSdk 34 (Kotlin 1.9 · AGP 8.5)
- UI៖ `app/src/main/assets/www/index.html` (បង្កើតដោយស្វ័យប្រវត្តិពី `app.js` + `style.css`)
- `app/src/main/java/com/appforge/kotlinwrap/MainActivity.kt`៖ WebView បើក `file:///android_asset/www/index.html` — offline

## កែ UI

កែកូដ JavaScript/CSS ក្នុងទំព័រ builder ហើយ Build ម្តងទៀត។

## Build APK ដោយខ្លួនឯង

```bash
# ត្រូវការ JDK 17 និង Android SDK (ANDROID_HOME)
gradle assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```
