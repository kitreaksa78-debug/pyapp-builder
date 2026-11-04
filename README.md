# PyApp Builder

សាយ **Python (Flask)** សម្រាប់បង្កើត **APK** ដំឡើងលើ Android — សរសេរកូដ **C#** ក្នុង browser
រួចចុច **Build APK**។

## របៀបដំណើរការ
1. សាយ Flask បង្កើត project **.NET for Android** (`net10.0-android`) ពីកូដ `MainActivity.cs`
   រួច push ទៅ branch `build/<job>` តាម GitHub Git Data API (មិនត្រូវការ `git` ក្នុង server)។
2. Workflow [`.github/workflows/build-csharp-apk.yml`](.github/workflows/build-csharp-apk.yml) រត់លើ
   GitHub Actions៖ .NET 10 SDK + `android` workload + JDK 17 + Android SDK 36 + `dotnet publish` (~៤–៦ នាទី)។
3. APK ចេញជា **release** `apk-<job>` → សាយផ្តល់ link ទាញយក។
4. ទំព័រ job បង្ហាញ **% ពិតប្រាកដ** គណនាពីជំហានរបស់ GitHub (`conclusion` + `started_at`)។

## Project ដែលបង្កើត
| ឯកសារ | តួនាទី |
|---|---|
| `MainActivity.cs` | កូដរបស់អ្នក (`class MainActivity`, `[Activity]` បង្កើត manifest entry) |
| `App.csproj` | .NET 8 Android · ApplicationId · signing key ថេរ (`signing/debug.p12`) |
| `AndroidManifest.xml` | permissions + `<application>` (activity បង្កើតដោយ `[Activity]`) |
| `Resources/values/strings.xml` | ឈ្មោះ app |
| `Resources/mipmap-*/ic_launcher.png` | icon គ្រប់ density (បើបាន upload logo) |

> UI សង់ដោយកូដ C# (`Android.App.Activity`) — គ្មាន MAUI/Xamarin.Forms
> ដូច្នេះ build លឿន ហើយ .NET runtime ត្រូវបាន package ចូល APK ស្រាប់ (មិនត្រូវដំឡើងបន្ថែម)។

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
| GET | `/` | ទំព័រសរសេរកូដ C# + ផ្ទាំងមើល app + build APK |
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
- `/builder` → form C# ដើម (សរសេរ `MainActivity.cs` ដោយដៃ + upload icon)
- `POST /api/build` (JSON: `title`, `package`, `cs_code`) → `{id, status_url, download_url}` — បៃតុង
  **Build Project** ក្នុង dashboard ហៅ endpoint នេះ រួច poll `/api/jobs/<id>` រហូត APK រួចរាល់
- Source របស់ dashboard (React + Vite + Tailwind + Monaco) នៅ `appforge/` —
  rebuild: `cd appforge && npm install && npm run build && cp -r dist/. ../pyapp-builder/static/dashboard/`
