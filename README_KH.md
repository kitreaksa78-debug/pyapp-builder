# Groq Pool AI — Android Project (Direct Groq API)

កម្មវិធី Android សម្រាប់ជួយហាត់ 8 Ball Pool៖
- បញ្ចូល Groq API Key ក្នុងកម្មវិធី (រក្សាទុកដោយ Android Keystore AES-GCM)
- ជ្រើសរើស Groq Vision model
- ស្នើសុំការអនុញ្ញាត Android Screen Capture មុនចាប់ផ្តើម
- ផ្ញើរូបភាពអេក្រង់ទៅ Groq API ដោយផ្ទាល់តាម HTTPS (មិនប្រើ Render/backend ផ្ទាល់ខ្លួន)
- បង្ហាញបន្ទាត់ណែនាំជា Floating Overlay
- មិនមាន auto-play, auto-aim ឬការចុចជំនួសអ្នកលេងទេ

## Build

ត្រូវការ Android Studio, JDK 17, Android SDK Platform 35 និង Gradle ដែល Android Gradle Plugin អាចប្រើបាន។

1. Extract ZIP.
2. បើក folder `GroqPoolLiveAssistant` ក្នុង Android Studio.
3. អនុញ្ញាត Gradle Sync និងដំឡើង SDK 35 ប្រសិនបើ Android Studio ស្នើសុំ.
4. ជ្រើស `Build > Build Bundle(s) / APK(s) > Build APK(s)`.
5. APK ស្ថិតនៅ `app/build/outputs/apk/debug/app-debug.apk`.

## Use

1. បើកកម្មវិធី ហើយបញ្ចូល Groq API Key របស់អ្នក។
2. ជ្រើសរើស model ដែលគាំទ្រ image input នៅក្នុង Groq account របស់អ្នក។
3. ចុច SAVE API KEY រួច TEST GROQ CONNECTION.
4. ចុច START SCREEN ANALYSIS + FLOATING GUIDE.
5. អនុញ្ញាត Display over other apps ហើយចុច START ម្ដងទៀត។
6. អនុញ្ញាត Android screen-capture prompt ហើយប្ដូរទៅហ្គេម។
7. ចុច STOP FLOATING GUIDE ក្នុងកម្មវិធី ដើម្បីបញ្ឈប់។

## សុវត្ថិភាព និងដែនកំណត់

- API Key ត្រូវបានរក្សាទុក encrypted លើឧបករណ៍ ប៉ុន្តែវាត្រូវបានប្រើដើម្បីហៅ Groq ដោយផ្ទាល់។ កុំចែករំលែក APK ដែលមាន key របស់អ្នក។
- រូបភាពអេក្រង់ត្រូវបានផ្ញើទៅ Groq ដើម្បីវិភាគ។ ចាប់ផ្តើម capture តែពេលអ្នកយល់ព្រម។
- ត្រូវមានអ៊ីនធឺណិត និង Groq API access/model ដែលអនុញ្ញាត vision។ Quota និង model availability អាស្រ័យលើ Groq account និងបច្ចុប្បន្នភាពរបស់ Groq។
- AI អាចសម្គាល់បាល់ ឬទីតាំងខុស។ ប្រើជាគន្លឹះហាត់ប៉ុណ្ណោះ ហើយពិនិត្យដោយខ្លួនឯង។
- Project ZIP នេះជាកូដ source មិនមែន APK ដែលបាន build រួចទេ។ ត្រូវ Build នៅ Android Studio ឬ CI ដែលមាន Android SDK។
