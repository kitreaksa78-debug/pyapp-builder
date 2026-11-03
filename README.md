# PyApp Builder

សាយ **Python (Flask)** សម្រាប់បង្កើត **APK** ដំឡើងលើ Android — សរសេរកូដ **Java** ក្នុង browser
រួចចុច **Build APK**។

## របៀបដំណើរការ
1. សាយ Flask បង្កើត project Android native (Gradle) ពីកូដ `MainActivity.java` របស់អ្នក
   រួច push ទៅ branch `build/<job>` តាម GitHub Git Data API (មិនត្រូវការ `git` ក្នុង server)។
2. Workflow [`.github/workflows/build-java-apk.yml`](.github/workflows/build-java-apk.yml) រត់លើ
   GitHub Actions៖ JDK 17 + Android SDK 34 + `gradle assembleDebug` (~១.៥–៣ នាទី)។
3. APK ចេញជា **release** `apk-<job>` → សាយផ្តល់ link ទាញយក។
4. ទំព័រ job បង្ហាញ **% ពិតប្រាកដ** គណនាពីជំហានរបស់ GitHub (`conclusion` + `started_at`)។

## Project ដែលបង្កើត
| ឯកសារ | តួនាទី |
|---|---|
| `app/src/main/java/<pkg>/MainActivity.java` | កូដរបស់អ្នក (package បញ្ចូលស្វ័យប្រវត្តិ) |
| `app/src/main/AndroidManifest.xml` | launcher activity + `INTERNET` permission |
| `app/src/main/res/values/strings.xml` | ឈ្មោះ app |
| `app/build.gradle` · `build.gradle` · `settings.gradle` | AGP 8.5.2 · Gradle 8.7 · JDK 17 · compileSdk 34 · minSdk 21 |

> UI សង់ដោយកូដ Java (`android.app.Activity`) — គ្មាន AndroidX/Python runtime
> ដូច្នេះ APK តូច (~10 KB) ហើយបើកដើរភ្លាម។

## រត់ក្នុងម៉ាស៊ីនរបស់អ្នក
```bash
pip install -r requirements.txt
export GH_REPO=owner/repo     # repo មាន .github/workflows/build-java-apk.yml
export GH_TOKEN=ghp_xxx       # token មានសិទ្ធិ repo + workflow
python app.py                 # http://localhost:8000
```

## Deploy លើ Render
`render.yaml` មានរួច — start `gunicorn app:app`។ កំណត់ env `GH_REPO`, `GH_TOKEN`។

## API
| Method | Path | ការងារ |
|---|---|---|
| GET | `/` | ទំព័រសរសេរកូដ Java |
| POST | `/build` | បង្កើត job រួច dispatch workflow |
| POST | `/project.zip` | ទាញយក project ជា ZIP |
| GET | `/j/<id>` | ទំព័រស្ថានភាព + % |
| GET | `/api/jobs/<id>` | JSON ស្ថានភាព + `progress` (percent / step / eta / steps) |
| GET | `/dl/<id>` | ទាញយក APK |
| GET | `/api/health` | health check |

## សុវត្ថិភាព
- `GH_TOKEN` ជា secret របស់ server — កុំដាក់ក្នុង code ឬ commit។
- កូដរបស់អ្នក build ក្នុង GitHub Actions និងរត់តែលើទូរស័ព្ទរបស់អ្នក។
