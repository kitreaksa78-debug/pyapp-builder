# Groq AI Pool Assistant (Android Project)

Project Android Kotlin សម្រាប់ Android 8.0+ (API 26+)។ មាន Groq API Key field, Model field, Screen Capture consent និង Floating Overlay។

## ត្រូវការ
- Android Studio (JDK 17)
- Android phone Android 8.0 ឬថ្មីជាងនេះ
- Groq API Key ដែលអ្នកបង្កើតដោយខ្លួនឯង

## របៀបបើក Project
1. Extract ZIP។
2. បើក Android Studio → Open → ជ្រើស folder `GroqAIPoolAssistant`។
3. រង់ចាំ Gradle Sync។
4. ភ្ជាប់ទូរស័ព្ទដោយ USB debugging ឬប្រើ Android Emulator។
5. ចុច Run ដើម្បីដំឡើង។

## របៀបប្រើ
1. បើក Groq AI Pool Assistant។
2. បញ្ចូល Groq API Key ក្នុងប្រអប់ Groq API Key។
3. Model default គឺ `meta-llama/llama-4-scout-17b-16e-instruct`។ អ្នកអាចកែទៅ model ដែល Groq account របស់អ្នកគាំទ្រ។
4. ចុច `Save API Key & Model`។
5. ចុច `Enable Floating Overlay`។
6. អនុញ្ញាត `Display over other apps` ប្រសិនបើ Android ស្នើ។
7. អនុញ្ញាត Screen Capture នៅក្នុង dialog របស់ Android។
8. បើកហ្គេម ហើយចុច Analyze នៅលើ Floating Overlay។

## សុវត្ថិភាព និងដែនកំណត់
- API Key ត្រូវបានរក្សាទុកក្នុង private SharedPreferences របស់ App។ កុំចែករំលែកទូរស័ព្ទ ឬ APK ដែលមាន Key របស់អ្នក។
- Screenshot ត្រូវបានផ្ញើទៅ Groq API ដើម្បីវិភាគ។ កុំចាប់យកអេក្រង់ដែលមានព័ត៌មានឯកជន។
- Screen Capture ត្រូវការការអនុញ្ញាតពី Android រាល់ពេលចាប់ផ្ដើម session ថ្មី។
- Overlay អាចត្រូវបាន Android ឬកម្មវិធីខ្លះរារាំង។ បិទដោយចុច Close ឬបិទ notification/service។
- លទ្ធផល Vision គឺជាការប៉ាន់ស្មានសម្រាប់ហ្វឹកហាត់ មិនមែនការវាស់មុំ/រូបវិទ្យាដែលធានាត្រឹមត្រូវទេ។
- Project នេះមិនបញ្ជាហ្គេមដោយស្វ័យប្រវត្តិ មិនប៉ះ memory របស់ហ្គេម និងមិនកែប្រែ game files។
- Groq អាចផ្លាស់ប្តូរ model availability ឬ API limits។ បើ Model មិនដំណើរការ សូមជ្រើស vision model ដែលបច្ចុប្បន្នមាននៅក្នុង Groq Console។

## បើមាន Error
- `401`: API Key មិនត្រឹមត្រូវ។
- `404` ឬ model error: ពិនិត្យ Model ID និងថាតើវានៅមានក្នុង Groq Console។
- `429`: Rate limit ឬ quota។
- `Screen capture unavailable`: បិទ service ហើយបើកឡើងវិញ ដើម្បីផ្ដល់ permission ថ្មី។
