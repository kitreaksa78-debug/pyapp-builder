# PyApp Builder

សាយ **Python (Flask)** សម្រាប់បង្កើត **APK** ដំឡើងលើ Android — សរសេរ UI ដោយ **JavaScript + CSS**
ក្នុង browser (ផ្ទាំងមើល render ពីកូដផ្ទាល់) រួចចុច **Build APK**។

## PyApp IDE (`/ide`)

IDE ពេញលេញក្នុង browser (VS Code style) សម្រាប់ project Android៖

- **File explorer** — បង្កើត file/folder, rename, delete, upload, import ZIP, ទាញយក file, export ZIP
  (folder ត្រូវបានរក្សាទុកក្នុង IndexedDB រួមនឹង file ⇒ refresh មិនបាត់)
- **Editor** — Monaco (CDN) មាន syntax highlighting, autocomplete, multi-tab, search/replace,
  line numbers; បើ CDN មិនអាចផ្ទុកបាន ប្រើ textarea fallback
- **Live preview** — បញ្ចូល `index.html`, CSS, JS, រូបភាពក្នុង project ចូល iframe sandbox,
  បង្ហាញ JavaScript error, មាន desktop/mobile mode និង refresh
- **Build APK** — ជ្រើស app type **WebView / Native Kotlin / Native Java**, ឈ្មោះ, package,
  version name/code, icon (upload ឬ icon generator), permissions, orientation, online/offline
- **Build progress** — % ពីជំហាន GitHub, log ពី workflow (បើបរាជ័យ), link APK និង GitHub run,
  ប្រវត្តិ build (local) និង `/api/ide/builds` (server 20 ចុងក្រោយ)
- **Themes** dark/light, **ភាសា** ខ្មែរ/English, font size, word wrap, autosave

> Project file រក្សាទុកក្នុង **browser របស់អ្នក** (IndexedDB) — server គ្រាន់តែ build/export
> ដូច្នេះ project មិនលាយជាមួយអ្នកប្រើផ្សេង ហើយ Render restart មិនបាត់កូដ។

### Native mode
- `MainActivity.kt` ឬ `MainActivity.java` ក្នុង project ត្រូវ compile ដោយ Gradle ពិត (Kotlin 1.9 / Java 17)
- package ក្នុង source ត្រូវផ្លាស់ទៅ package របស់ app ស្វ័យប្រវត្តិ ដើម្បីឲ្យ
  manifest `.MainActivity` ដំណើរការ
- បើគ្មាន source ទេ សាយបង្កើត `MainActivity` គំរូ (Android Views, គ្មាន WebView) ជូន

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
| GET | `/ide` | **PyApp IDE** — explorer, Monaco, preview, build panel |
| POST | `/api/ide/build` | JSON `{title, package, mode, version, build, permissions, orientation, online, icon, files}` → job |
| POST | `/api/ide/zip` | ZIP ពី project files (`{name, files}`) |
| POST | `/api/ide/import` | upload ZIP → files (safe extract, បដិសេធ traversal) |
| GET | `/api/ide/info` | modes / permissions / limits |
| GET | `/api/ide/builds` | build 20 ចុងក្រោយ |
| GET | `/api/jobs/<id>/logs` | log ពី GitHub Actions run |
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
- IDE API ពិនិត្យ path (គ្មាន `..`, គ្មាន absolute), ប្រភេទ file, ទំហំ (5 MB/file · 30 MB/project)
  និងបើក ZIP ដោយបដិសេធ member ដែលរត់ចេញក្រៅ folder។
- កូដរបស់អ្នក build ក្នុង GitHub Actions និងរត់តែលើទូរស័ព្ទរបស់អ្នក។

## AppForge dashboard (React SPA)
- `/` → **AppForge** dashboard (preview + file tree + Monaco) — built assets នៅ `static/dashboard/`
- `/builder` → JS app builder (សរសេរ `app.js` + CSS, preview render ពីកូដ, upload icon)
- `POST /api/build` (JSON: `title`, `package`, `app_js`, `app_css`) → `{id, status_url, download_url}` — ប៊ូតុង
  **Build Project** ក្នុង dashboard ហៅ endpoint នេះ រួច poll `/api/jobs/<id>` រហូត APK រួចរាល់
- Source របស់ dashboard (React + Vite + Tailwind + Monaco) នៅ `appforge/` —
  rebuild: `cd appforge && npm install && npm run build && cp -r dist/. ../pyapp-builder/static/dashboard/`
