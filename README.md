# Aurora Notes (C# · .NET for Android)

App Android native បង្កើតដោយ PyApp Builder — សរសេរដោយ **C#**។

- package: `com.appforge.aurora`
- minSdk 21 · targetSdk 36 (net10.0-android)
- កូដ app៖ `MainActivity.cs` (class `MainActivity`, `[Activity]` attribute បង្កើត manifest entry)

## Build APK ដោយខ្លួនឯង

```bash
# ត្រូវការ .NET 8 SDK + Android SDK + JDK 17
dotnet workload install android
dotnet publish -f net10.0-android -c Release -p:AndroidPackageFormat=apk
# APK: bin/Release/net10.0-android/publish/*-Signed.apk
```
