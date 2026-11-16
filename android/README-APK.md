# City Drive — បង្កើត APK (Android)

ឯកសារនេះពន្យល់របៀបបង្កើត **APK ពិត** ពីហ្គេម។
បរិស្ថានដែលបានសរសេរកូដនេះ **គ្មាន Android SDK** ដូច្នេះខ្ញុំមិនអាច compile APK
ជូនអ្នកផ្ទាល់បានទេ — ប៉ុន្តែគម្រោង Android ពេញលេញនៅក្នុង folder នេះរួចហើយ។

## វិធីលឿនបំផុត (១០ នាទី)

1. ដំឡើង **Android Studio** (ឥតគិតថ្លៃ): https://developer.android.com/studio
2. បើក Android Studio → **New Project** → *Empty Views Activity*
   - Name: `City Drive`
   - Package: `com.citydrive`
   - Language: Java
3. ចម្លងឯកសារពី folder នេះចូលគម្រោងថ្មី:
   - `app/src/main/java/com/citydrive/MainActivity.java` → ជំនួសឯកសារដូចឈ្មោះ
   - `app/src/main/AndroidManifest.xml` → ជំនួស
   - `index.html` (នៅក្រៅ folder `android/`) → ចម្លងទៅ `app/src/main/assets/index.html`
     (បង្កើត folder `assets` បើគ្មាន)
4. ចុច **Run ▶** ដើម្បីសាកលើទូរស័ព្ទ ឬ **Build → Build Bundle(s)/APK(s) → Build APK(s)**
5. APK នឹងនៅ `app/build/outputs/apk/debug/app-debug.apk` → ចម្លងទៅទូរស័ព្ទ រួចដំឡើង

## ចំណុចត្រូវដឹង

- ហ្គេមទាញ `three.js` ពី CDN (`unpkg.com`) → ត្រូវការ **អ៊ីនធឺណិត** ពេលបើកដំបូង។
  បើចង់ឲ្យដើរកាត់អ៊ីនធឺណិត៖ ទាញ `three.module.js` មកដាក់ក្នុង `assets/` រួចប្តូរ
  `import ... from 'https://unpkg.com/three@0.160.0/build/three.module.js'` ទៅ `'./three.module.js'`
- APK debug ដំឡើងបានធម្មតា (ត្រូវបើក "Unknown sources")។
  បើចង់ដាក់លើ Play Store ត្រូវធ្វើ **signed release APK/AAB** ក្នុង Android Studio។
- ហ្គេមរត់លើ WebView — គ្មាន server ត្រូវការ។ មុខងារ API (`/api/...`) នឹងបិទស្ងាត់។
