# Kotlin Wrapper (JavaScript · WebView)

App Android បង្កើតដោយ PyApp Builder — UI សរសេរដោយ **JavaScript + CSS**។

- package: `com.appforge.kotlinwrap`
- UI៖ `Assets/www/index.html` (បង្កើតដោយស្វ័យប្រវត្តិពី `app.js` + `style.css`)
- `MainActivity.cs`៖ WebView បើក `file:///android_asset/www/index.html` — offline

## កែ UI

កែកូដ JavaScript/CSS ក្នុងទំព័រ builder ហើយ Build ម្តងទៀត។

## Build APK ដោយខ្លួនឯង

```bash
dotnet workload install android
dotnet publish -f net10.0-android -c Release -p:AndroidPackageFormat=apk
# APK: bin/Release/net10.0-android/publish/*-Signed.apk
```
