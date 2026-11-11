# Pool Line Overlay (Android Java)

កម្មវិធីនេះជាឧបករណ៍គូសបន្ទាត់លើអេក្រង់ដោយដៃ។ វាមិនអានទិន្នន័យហ្គេម មិនចាប់យក memory និងមិនធ្វើ auto-aim ទេ។

## បង្កើត APK
1. បើក ZIP នេះក្នុង Android Studio ឬ AppForge IDE ដែលគាំទ្រ Android Gradle Project។
2. Sync Gradle។
3. Build > Build APK(s) ឬប្រើ `assembleDebug`។
4. ដំឡើង APK លើ Android។
5. បើកកម្មវិធី ហើយអនុញ្ញាត `Display over other apps`។
6. ចុច `Show Drawing Overlay`។ អូសចំណុចពណ៌លឿងទាំងពីរដើម្បីកែបន្ទាត់។
7. ដើម្បីបិទ Overlay ត្រឡប់ទៅកម្មវិធី ហើយចុច `Hide Overlay`។

## ចំណាំ
- Project source នេះមិនទាន់ជា APK ដែលបាន compile ទេ។
- Overlay permission ត្រូវបានផ្តល់ដោយអ្នកប្រើប្រាស់ផ្ទាល់។
- គំរូនេះប្រើបន្ទាត់ដៃតែមួយ និងមិនមានការវិភាគរូបភាពហ្គេម។
