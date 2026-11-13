# Groq Pool Practice Overlay (Direct API)

## កំណែនេះមិនត្រូវការ Render Backend URL ទេ
App ភ្ជាប់ទៅ Groq API ដោយផ្ទាល់តាម HTTPS:
`https://api.groq.com/openai/v1/chat/completions`

## Build
- JDK 17
- Android Gradle Plugin / Gradle wrapper compatible with project
- Open project folder `GroqPoolLiveAssistant` in Android Studio or AndroidIDE.
- Build APK (`assembleDebug`).

## ប្រើប្រាស់
1. ដំឡើង APK ហើយបើក App។
2. បញ្ចូល Groq API Key (មិនត្រូវបង្ហោះ Key ទៅ GitHub)។
3. Model លំនាំដើម `qwen/qwen3.8-27b` គាំទ្រ image input តាម Groq Vision docs។
4. ចុច SAVE KEY SECURELY។
5. ចុច TEST GROQ CONNECTION។
6. អនុញ្ញាត Display over other apps និង Screen capture នៅពេល App ស្នើសុំ។
7. ចុច START SCREEN CAPTURE + OVERLAY ហើយប្ដូរទៅអេក្រង់ហាត់។

Key ត្រូវបាន encrypt ដោយ Android Keystore AES-GCM នៅលើឧបករណ៍។ ការហៅ API ផ្ញើ Key ទៅ Groq តាម HTTPS។ កុំចែករំលែក APK ដែលមាន Key របស់អ្នក; ការដាក់ API key ក្នុង client app មិនសមស្របសម្រាប់ app សាធារណៈទេ។

## សំខាន់
- ការវិភាគ Vision អាចខុស ឬមិនស្គាល់ទីតាំងបាល់; បន្ទាត់គ្រាន់តែជាការប៉ាន់ស្មានសម្រាប់ហាត់ មិនមែន physics engine ពេញលេញ។
- Overlay មិនប៉ះបញ្ជា ឬបាញ់ជំនួសអ្នកទេ។
- ត្រូវការអ៊ីនធឺណិត និង Groq API key ដែលមានសិទ្ធិប្រើ model។
