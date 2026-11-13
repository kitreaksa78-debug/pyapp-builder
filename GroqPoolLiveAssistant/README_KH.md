# Groq Pool AI Assistant (Android Source)

នេះជា Android source project សាកល្បងសម្រាប់ Screen Capture ដែលអ្នកអនុញ្ញាត, ផ្ញើរូបភាពទៅ Groq Vision តាម Backend និងបង្ហាញបន្ទាត់ overlay ជាគន្លងណែនាំសម្រាប់ហាត់។

## 1) Deploy Backend ទៅ Render
- បង្កើត Web Service ថ្មីពី folder `render-backend` (ឬដាក់ folder នេះជា Root Directory)
- Build command: `npm install`
- Start command: `npm start`
- បន្ទាប់ពី deploy សាកល្បង `https://pyapp-builder.onrender.com/` ត្រូវបាន JSON `{"status":"ok"}`

## 2) Build Android
- បើក project នេះក្នុង Android Studio / IDE ដែលគាំទ្រ Gradle Kotlin DSL
- ប្រើ JDK 17, Android SDK 35 និង Gradle 8.7+
- Build > Build APK
- នេះជា source ZIP មិនមែន APK ដែលបាន build/sign រួចទេ។

## 3) ប្រើ App
- បញ្ចូល Groq API key និង backend URL HTTPS ពិតប្រាកដ
- Model: ជ្រើស model vision ដែលមានសិទ្ធិប្រើក្នុង Groq Console; model default ក្នុង app គ្រាន់តែជាតម្លៃដំបូង ហើយអាចត្រូវប្ដូរ
- Save Key Securely
- Allow Display over other apps
- Tap Start Screen Capture ហើយអនុញ្ញាត Android screen-capture dialog
- ប្ដូរទៅហ្គេម។ Overlay នឹងបង្ហាញបន្ទាត់នៅពេលម៉ូដែលរកឃើញ cue និង target ball។
- បញ្ឈប់ដោយ Stop Overlay ក្នុង app។

## សុវត្ថិភាព និងដែនកំណត់
- Screen capture ចាប់ផ្ដើមតែបន្ទាប់ពីអ្នកចុចអនុញ្ញាត Android dialog ហើយមាន foreground notification។
- API key រក្សាទុកក្នុង Android Keystore encrypted preferences។ Key ត្រូវបានផ្ញើតាម HTTPS ទៅ backend ក្នុង request ហើយ backend មិនរក្សាទុក/log key ទេ។
- កុំបង្ហោះ APK ដែលបានបញ្ចូល API key រួច។
- AI vision អាចសម្គាល់បាល់ខុស ឬបរាជ័យដោយសាររូបភាពតូច, animation, table perspective ឬ UI overlay។ បន្ទាត់នេះជាជំនួយហាត់ មិនធានា 100%។
- កំណែដំបូងគូរបន្ទាត់ Cue Ball ទៅ Target Ball ត្រង់ៗ។ មិនទាន់មានការគណនាធរណីមាត្រប៉ះបាល់, rail rebound, spin, power ឬ pocket trajectory ពេញលេញទេ។
- App មិនបញ្ជា touch ឬបាញ់ជំនួសអ្នកទេ។
