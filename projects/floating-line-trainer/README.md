# Floating Line Trainer

បន្ទាត់ហាត់ (aim/alignment line) អណ្តែតលើ app ផ្សេង — browser, social, game — ជាមួយផ្ទាំងបញ្ជាតូចមួយ។
Project នេះ build ដោយ PyApp IDE (`mode: kotlin`, package `com.kitreaksa.floatingline`)។

## File

| File | តួនាទី |
| --- | --- |
| `AndroidManifest.xml` | permission និង `<service>` — builder បញ្ចូលទៅ manifest ដែលវាបង្កើត |
| `MainActivity.kt` | ផ្ទាំងគ្រប់គ្រង, ស្នើសុំ «Display over other apps», notification permission |
| `OverlayService.kt` | foreground service ដែលគូរបន្ទាត់ + panel ដោយ `WindowManager` |

## Permission ដែលប្រើ (តែប៉ុណ្ណោះ)

- `SYSTEM_ALERT_WINDOW` — គូរលើ app ផ្សេង។ **មិនអាចអនុញ្ញាតដោយ app ខ្លួនឯងបានទេ**;
  អ្នកប្រើត្រូវបើកក្នុង Settings។
- `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE` — ឲ្យ overlay នៅរស់ពេលប្តូរ app
  (Android 14+ ត្រូវការ service type `specialUse` + `<property>` subtype)។
- `POST_NOTIFICATIONS` — notification របស់ foreground service (Android 13+ ជា runtime permission)។

គ្មាន `INTERNET`, គ្មាន Accessibility Service, គ្មាន battery-optimization exemption request ទេ។

## របៀបប្រើលើទូរស័ព្ទ

1. ដំឡើង APK → បើក app → ចុច **«បើក overlay»**។
2. Settings នឹងបើកទំព័រ **Display over other apps** របស់ app នេះ → បើក **Allow**។
3. ត្រឡប់មក app → overlay ចាប់ផ្តើម (បើមិនចាប់ផ្តើម ចុចម្តងទៀត)។
4. បើក app ណាមួយ → បន្ទាត់ cyan និង panel នៅពីលើ។

Panel: `⠿` អូស · `⇅` បង្វិលផ្ដេក/បញ្ឈរ · `＋`/`－` កម្រាស់ · `👁` បង្ហាញ/លាក់ · `✕` បិទ។
អូសបន្ទាត់ដោយរង្វង់ `⠿` នៅកណ្តាលបន្ទាត់។ ផ្ទៃបន្ទាត់ **មិនចាប់ touch** ដូច្នេះចុចលេង
app ក្រោមបានធម្មតា។

## ដែនកំណត់របស់ Android ដែលត្រូវដឹង

- បើអ្នកប្រើបដិសេធ «Display over other apps» overlay មិនបង្ហាញទេ — app បង្ហាញសារច្បាស់
  ហើយមិនព្យាយាមអនុញ្ញាតដោយសម្ងាត់។
- កំណែ Android ខ្លះ (ជាពិសេស Huawei/Honor, Xiaomi) សម្លាប់ foreground service ពេល app
  ចេញពី recent apps។ ត្រូវបើក Battery → **Unrestricted**, និង Huawei **App launch → Manual
  manage** (Auto-launch, Secondary launch, Run in background)។
- Notification channel ជា `IMPORTANCE_LOW` — notification អាចលាក់បាន តែ service នៅដំណើរការ។
- មិនមានការធានាថា overlay នៅលើគ្រប់ app: កម្មវិធីដែលប្រើ `HIDE_OVERLAY_WINDOWS` (ឧ. ធនាគារ)
  នឹងលាក់ overlay ដោយចេតនា។
- គ្មាន emulator/ឧបករណ៍ Android ក្នុង build server ⇒ APK ត្រូវបានត្រួតពិនិត្យ manifest/dex
  ប៉ុណ្ណោះ; ការផ្ទៀងផ្ទាត់ overlay ពិតត្រូវធ្វើលើទូរស័ព្ទ។
