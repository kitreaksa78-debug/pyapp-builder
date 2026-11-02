# PyApp Builder

សាយ **Python (Flask)** សម្រាប់បង្កើត **APK** ពី app **Kivy**៖ សរសេរ `main.py` ក្នុង browser → ចុច **Build APK** → ទាញយក `.apk`។

## ហេតុអ្វី build ក្នុង GitHub Actions?
`buildozer` (python-for-android) ត្រូវការ Android SDK + NDK ជាង 2 GB និង Java —
hosting ឥតគិតថ្លៃ (Render free) មិនអាចរត់វាបានទេ។ ដូច្នេះ៖

1. សាយ Flask បង្កើត project (main.py + buildozer.spec) និង push ទៅ branch `build/<job>` ក្នុង repo ខ្លួនឯង
   (តាម GitHub API — មិនត្រូវការ `git` ក្នុង server)
2. Workflow [`build-apk.yml`](.github/workflows/build-apk.yml) រត់ក្នុង container `kivy/buildozer` រួច `buildozer android debug`
3. APK ចេញជា **release** `apk-<job>` → សាយផ្តល់ link ទាញយក

ការ build ដំបូងប្រហែល **១០–២៥ នាទី** (compile Python សម្រាប់ Android)។ លើកក្រោយលឿនជាង ព្រោះមាន cache។

## រត់ក្នុងម៉ាស៊ីនរបស់អ្នក
```bash
pip install -r requirements.txt
export GH_REPO=owner/repo        # repo ដែលមាន .github/workflows/build-apk.yml
export GH_TOKEN=ghp_xxx          # token មានសិទ្ធិ repo + workflow
python app.py                    # http://localhost:8000
```
បើគ្មាន `GH_REPO`/`GH_TOKEN` សាយនៅតែដំណើរការ — អ្នកអាច **ទាញយក project ជា ZIP** ទៅ build ខ្លួនឯង។
បើម៉ាស៊ីនមាន `buildozer` ក្នុង PATH សាយនឹង build ក្នុងម៉ាស៊ីនផ្ទាល់ (មិនត្រូវការ GitHub)។

## Deploy លើ Render
`render.yaml` មានរួច៖ runtime python, start `gunicorn app:app`។
កំណត់ env `GH_REPO` និង `GH_TOKEN` ក្នុង Dashboard → Environment។

> Free plan មាន disk បណ្ដោះអាសន្ន — job metadata បាត់ពេល restart។ APK នៅលើ GitHub release ដូច្នេះ link នៅតែប្រើបាន។

## API
| Method | Path | ការងារ |
|---|---|---|
| GET | `/` | ទំព័រសរសេរកូដ |
| POST | `/build` | បង្កើត job រួចបញ្ជូនទៅ build |
| POST | `/project.zip` | ទាញយក project ជា ZIP |
| GET | `/j/<id>` | ទំព័រស្ថានភាព |
| GET | `/api/jobs/<id>` | JSON ស្ថានភាព |
| GET | `/dl/<id>` | ទាញយក APK |
| GET | `/api/health` | health check |

## ចំណាំអំពីអក្សរខ្មែរ
Kivy ត្រូវការ file font (`.ttf`) ដើម្បីបង្ហាញអក្សរខ្មែរ។ បើចង់ប្រើខ្មែរ សូមបន្ថែម
`NotoSansKhmer-Regular.ttf` ក្នុង project និងកំណត់ `Label(font_name="NotoSansKhmer-Regular.ttf")`។

## សុវត្ថិភាព
- សាយរត់កូដរបស់អ្នក **តែក្នុង Android app** ដែលអ្នកដំឡើងខ្លួនឯង — មិនរត់នៅលើ server ទេ។
- `GH_TOKEN` គឺជា secret របស់ server — កុំដាក់ក្នុង code ឬ commit។
