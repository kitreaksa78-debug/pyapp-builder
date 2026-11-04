# PyApp Builder

សាយ **Python (Flask)** សម្រាប់បង្កើត **APK** ដំឡើងលើ Android — សរសេរ UI ដោយ **JavaScript + CSS**
ក្នុង browser (ផ្ទាំងមើល render ពីកូដផ្ទាល់) រួចចុច **Build APK**។

## របៀបដំណើរការ
1. សាយ Flask វេចខ្ចប់ UI (JavaScript + CSS) ជា `Assets/www/index.html` ក្នុង project
   **.NET for Android** (`net10.0-android`) រួច push ទៅ branch `build/<job>` តាម GitHub Git Data API
   (មិនត្រូវការ `git` ក្នុង server)។
2. Workflow [`.github/workflows/build-csharp-apk.yml`](.github/workflows/build-csharp-apk.yml) រត់លើ
   GitHub Actions៖ .NET 10 SDK + `android` workload + JDK 17 + Android SDK 36 + `dotnet publish` (~៤–៦ នាទី)។
3. APK ចេញជា **release** `apk-<job>` → សាយផ្តល់ link ទាញយក។
4. ទំព័រ job បង្ហាញ **% ពិតប្រាកដ** គណនាពីជំហានរបស់ GitHub (`conclusion` + `started_at`)។

## Project ដែលបង្កើត
| ឯកសារ | តួនាទី |
|---|---|
| `Assets/www/index.html` | **app របស់អ្នក** — HTML ដែលបង្កើតពី `app.js` + `style.css` |
| `MainActivity.cs` | WebView បើក `file:///android_asset/www/index.html` (offline) |
| `App.csproj` | .NET 8 Android · ApplicationId · signing key ថេរ (`signing/debug.p12`) |
| `AndroidManifest.xml` | permissions + `<application>` (activity បង្កើតដោយ `[Activity]`) |
| `Resources/values/strings.xml` | ឈ្មោះ app |
| `Resources/mipmap-*/ic_launcher.png` | icon គ្រប់ density (បើបាន upload logo) |

> UI សង់ដោយ **JavaScript** ក្នុង WebView — ដំណើរការ offline (asset ក្នុង APK)
> ហើយ .NET runtime ត្រូវបាន package ចូល APK ស្រាប់ (មិនត្រូវដំឡើងបន្ថែម)។

## រត់ក្នុងម៉ាស៊ីនរបស់អ្នក
```bash
pip install -r requirements.txt
export GH_REPO=owner/repo     # repo មាន .github/workflows/build-csharp-apk.yml
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
- `/` → **AppForge** dashboard (បង្ហាញ preview + file tree + Monaco) — built assets នៅ `static/dashboard/`
- `/builder` → JS app builder (សរសេរ `app.js` + CSS, preview render ពីកូដ, upload icon)
- `POST /api/build` (JSON: `title`, `package`, `cs_code`) → `{id, status_url, download_url}` — បៃតុង
  **Build Project** ក្នុង dashboard ហៅ endpoint នេះ រួច poll `/api/jobs/<id>` រហូត APK រួចរាល់
- Source របស់ dashboard (React + Vite + Tailwind + Monaco) នៅ `appforge/` —
  rebuild: `cd appforge && npm install && npm run build && cp -r dist/. ../pyapp-builder/static/dashboard/`
