# PyApp Builder

សាយ **Python (Flask)** សម្រាប់បង្កើត **APK** ដំឡើងលើ Android។ មានពីរផ្លូវ៖

| ផ្លូវ | ភាសា | រយៈពេល build | ស្ថានភាព |
|---|---|---|---|
| **Java (native)** | Java + Gradle + Android SDK | ~១.៥–៣ នាទី | ✅ ណែនាំ — app បើកដើរភ្លាម |
| Python (Kivy) | Python 3.11 + buildozer + p4a | ~៧–១១ នាទី | ⚠ ធ្ងន់ ហើយខ្លះ crash ពេលបើក |

## របៀបដំណើរការ
1. សាយ Flask បង្កើត project (Java/Gradle ឬ main.py + buildozer.spec) រួច push ទៅ branch `build/<job>`
   តាម GitHub Git Data API (មិនត្រូវការ `git` ក្នុង server)។
2. Workflow រត់លើ GitHub Actions៖
   - `.github/workflows/build-java-apk.yml` → `gradle assembleDebug`
   - `.github/workflows/build-apk.yml` → `buildozer -v android debug`
3. APK ចេញជា **release** `apk-<job>` → សាយផ្តល់ link ទាញយក។
4. ទំព័រ job បង្ហាញ **% ពិតប្រាកដ** គណនាពីជំហានរបស់ GitHub (`conclusion` + `started_at`)។

## Java APK មានអ្វី
- `android.app.Activity` សង់ UI ដោយកូដ (គ្មាន XML layout, គ្មាន AndroidX → build មិនស្ទះ)
- compileSdk 34 · targetSdk 34 · minSdk 21 · JDK 17 · AGP 8.5.2 · Gradle 8.7
- `INTERNET` permission + `usesCleartextTraffic` ⇒ ហៅ API (http/https) បាន
- អក្សរខ្មែរត្រូវ escape ជា `\uXXXX` ក្នុង Java source ⇒ មិនខូច encoding

### Version ដែលបានតេស្ត (Python)
| ធាតុ | Version |
|---|---|
| buildozer | 1.5.0 |
| python-for-android | `v2024.01.21` |
| NDK / API | 25b / 31 (min 21) |
| ABI | `arm64-v8a` |

> p4a master compile hostpython 3.14 ហើយ pip ក្នុង venv ខូច ⇒ ត្រូវ pin p4a 2024។

## រត់ក្នុងម៉ាស៊ីនរបស់អ្នក
```bash
pip install -r requirements.txt
export GH_REPO=owner/repo     # repo មាន .github/workflows/
export GH_TOKEN=ghp_xxx       # token មានសិទ្ធិ repo + workflow
python app.py                 # http://localhost:8000
```

## Deploy លើ Render
`render.yaml` មានរួច — start `gunicorn app:app`។ កំណត់ env `GH_REPO`, `GH_TOKEN`។

## API
| Method | Path | ការងារ |
|---|---|---|
| GET | `/` | ទំព័របង្កើត app (Java / Python) |
| POST | `/build` | បង្កើត job រួច dispatch workflow |
| POST | `/project.zip` | ទាញយក project ជា ZIP |
| GET | `/j/<id>` | ទំព័រស្ថានភាព + % |
| GET | `/api/jobs/<id>` | JSON ស្ថានភាព + `progress` (percent / step / eta / steps) |
| GET | `/dl/<id>` | ទាញយក APK |
| GET | `/api/health` | health check |

## សុវត្ថិភាព
- `GH_TOKEN` ជា secret របស់ server — កុំដាក់ក្នុង code ឬ commit។
- កូដរបស់អ្នក build ក្នុង GitHub Actions និងរត់តែលើទូរស័ព្ទរបស់អ្នក។
