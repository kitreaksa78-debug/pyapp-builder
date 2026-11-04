# PyApp Builder

សាយ **Python (Flask)** សម្រាប់បង្កើត **APK** ដំឡើងលើ Android — សរសេរ UI ដោយ **JavaScript + CSS**
ក្នុង browser (ផ្ទាំងមើល render ពីកូដផ្ទាល់) រួចចុច **Build APK**។

## របៀបដំណើរការ
1. សាយ Flask វេចខ្ចប់ UI (JavaScript + CSS) ជា `app/src/main/assets/www/index.html` ក្នុង project
   **Kotlin + Gradle** រួច push ទៅ branch `build/<job>` តាម GitHub Git Data API
   (មិនត្រូវការ `git` ក្នុង server)។
2. Workflow [`.github/workflows/build-kotlin-apk.yml`](.github/workflows/build-kotlin-apk.yml) រត់លើ
   GitHub Actions៖ JDK 17 + Android SDK 34 + Gradle 8.7 + Kotlin 1.9 + `gradle assembleDebug` (~៣–៥ នាទី)។
3. APK ចេញជា **release** `apk-<job>` → សាយផ្តល់ link ទាញយក។
4. ទំព័រ job បង្ហាញ **% ពិតប្រាកដ** គណនាពីជំហានរបស់ GitHub (`conclusion` + `started_at`)។

## Project ដែលបង្កើត
| ឯកសារ | តួនាទី |
|---|---|
| `app/src/main/assets/www/index.html` | **app របស់អ្នក** — HTML ដែលបង្កើតពី `app.js` + `style.css` |
| `app/src/main/java/<pkg>/MainActivity.kt` | WebView (Kotlin) បើក `file:///android_asset/www/index.html` (offline) |
| `app/build.gradle.kts` | AGP 8.5 · Kotlin 1.9 · applicationId · signing key ថេរ (`signing/debug.p12`) |
| `build.gradle.kts`, `settings.gradle.kts` | Gradle project + plugin repositories |
| `app/src/main/AndroidManifest.xml` | permissions + `<activity .MainActivity>` (LAUNCHER) |
| `app/src/main/res/values/strings.xml` | ឈ្មោះ app |
| `app/src/main/res/mipmap-*/ic_launcher.png` | icon គ្រប់ density (បើបាន upload logo) |

> UI សង់ដោយ **JavaScript** ក្នុង WebView — ដំណើរការ offline (asset ក្នុង APK)។
> Kotlin compiler បញ្ចូល runtime ទៅ APK ស្រាប់ (មិនត្រូវដំឡើងបន្ថែម)។

## រត់ក្នុងម៉ាស៊ីនរបស់អ្នក
```bash
pip install -r requirements.txt
export GH_REPO=owner/repo     # repo មាន .github/workflows/build-kotlin-apk.yml
export GH_TOKEN=ghp_xxx       # token មានសិទ្ធិ repo + workflow
python app.py                 # http://localhost:8000
```

## Deploy លើ Render
`render.yaml` មានរួច — start `gunicorn app:app`។ កំណត់ env `GH_REPO`, `GH_TOKEN`។

## API
| Method | Path | ការងារ |
|---|---|---|
| GET | `/builder` | ទំព័រសរសេរកូដ JavaScript + ផ្ទាំងមើល app ពីកូដ + build APK |
| POST | `/api/preview` | HTML សម្រាប់ផ្ទាំងមើល (ដូច asset ក្នុង APK) |
| GET | `/nano?f=<file>` | និពន្ធកូដបែប nano (គ្រប់ភាសា) |
| GET | `/api/files` | បញ្ជី file ដែលបានរក្សាទុក |
| GET / POST | `/api/files/<name>` | អាន / រក្សាទុក file |
| GET | `/files/<name>/download` | ទាញយក file |
| POST | `/build` | បង្កើត job រួច dispatch workflow |
| POST | `/project.zip` | ទាញយក project ជា ZIP |
| GET | `/j/<id>` | ទំព័រស្ថានភាព + % |
| GET | `/api/jobs/<id>` | JSON ស្ថានភាព + `progress` (percent / step / eta / steps) |
| GET | `/dl/<id>` | ទាញយក APK |
| GET | `/api/health` | health check |

## សុវត្ថិភាព
- `GH_TOKEN` ជា secret របស់ server — កុំដាក់ក្នុង code ឬ commit។
- កូដរបស់អ្នក build ក្នុង GitHub Actions និងរត់តែលើទូរស័ព្ទរបស់អ្នក។

## AppForge dashboard (React SPA)
- `/` → **AppForge** dashboard (preview + file tree + Monaco) — built assets នៅ `static/dashboard/`
- `/builder` → JS app builder (សរសេរ `app.js` + CSS, preview render ពីកូដ, upload icon)
- `POST /api/build` (JSON: `title`, `package`, `app_js`, `app_css`) → `{id, status_url, download_url}` — ប៊ូតុង
  **Build Project** ក្នុង dashboard ហៅ endpoint នេះ រួច poll `/api/jobs/<id>` រហូត APK រួចរាល់
- Source របស់ dashboard (React + Vite + Tailwind + Monaco) នៅ `appforge/` —
  rebuild: `cd appforge && npm install && npm run build && cp -r dist/. ../pyapp-builder/static/dashboard/`
