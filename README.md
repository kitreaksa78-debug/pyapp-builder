# City Drive 🌴🚗 — 3D city driving game (Three.js, single file)

ហ្គេមបើកបរ 3D ក្នុងទីក្រុងខ្មែរ — ឯកសារតែមួយ (`index.html`)។

## លេងភ្លាម

បើក `index.html` ក្នុង browser ណាមួយ (ត្រូវការអ៊ីនធឺណិត ព្រោះ three.js ទាញពី CDN)។

ឬដំណើរការ server មូលដ្ឋាន (បន្ថែម live API):

```bash
node server.js      # http://localhost:3000
```

## ការគ្រប់គ្រង

| សកម្មភាព | Keyboard | ទូរស័ព្ទ |
|---|---|---|
| ហ្គាស / ហ្វ្រាំង | W / S (↑ ↓) | ▲ / ▼ |
| ចង្កូត | A / D (← →) | ◀ / ▶ |
| ចុះ/ឡើងឡាន | E | ប៊ូតុង 🚶 |
| យប់ / ថ្ងៃ | N | 🌙 |
| ផែនទីកម្ពុជា | M | 🗺️ |
| សំឡេងម៉ាស៊ីន | 🔊 | 🔊 |
| គុណភាព HD | H | HD |

## អ្វីខ្លះនៅក្នុងហ្គេម

- ទីក្រុង **30 × 30 ប្លុក** (2 គ.ម × 2 គ.ម), អគារ premium ខ្ពស់ ១៥០ ម៉ែត្រ មានកំពូលបំភ្លឺ
- **៦,០០០ មនុស្ស** ដើរតាមថ្មបារាំង — ខ្លះរត់, ខ្លះឈរ, ខ្លះលោត, ខ្លះឆ្លងផ្លូវ
  (ក្បាល + ដងខ្លួន + សក់ + ដៃ ២ + ជើង ២, ពណ៌សម្លៀកបំពាក់/ស្បែក/សក់ ខុសៗគ្នា)
- **៧២ ឡាន** ចរាចរណ៍តាម lane ពិត + បុកឡាន/អគារ (ញ័រ + លោតត្រឡប់)
- ជនបទកម្ពុជាជុំវិញ: ស្រែ ៧០០, ផ្ទះ ១៩០, ដើមឈើ ៦២០, ភ្នំ, ទន្លេមេគង្គ, ទន្លេសាប, សមុទ្រ
- ផែនទីកម្ពុជាពិត (ចុច M) + minimap ជានិច្ច
- សំឡេងម៉ាស៊ីនឡានធ្វើដោយ WebAudio (គ្មានឯកសារសំឡេង)

## APK

មើល [`android/README-APK.md`](android/README-APK.md) — គម្រោង Android WebView ពេញលេញនៅក្នុង
`android/`។ ត្រូវការ Android Studio ដើម្បី build (ខ្ញុំគ្មាន SDK ក្នុងបរិស្ថាននេះ)។

## Live API (ស្រេចចិត្ត)

`server.js` ផ្តល់ `/api/live`, `/api/cmd`, `/api/shot` ដើម្បីមើល/បញ្ជាហ្គេមពីខាងក្រៅ។
មើល [`LIVE-API.md`](LIVE-API.md)។ token ស្ថិតក្នុង `.live-token` (មិនដាក់ក្នុង zip)។


## GTA-style Balanced update
- Added asphalt road slabs and concrete edge curbs aligned with the traffic grid.
- Corrected lane marking positions to match the road centres.
- Tuned bloom, shadows and render resolution for balanced Android performance.
- Reduced animated pedestrians from 6,000 to 1,500 while retaining pedestrian AI.
- Included `android/app/src/main/assets/index.html` for the existing Android WebView launcher.
