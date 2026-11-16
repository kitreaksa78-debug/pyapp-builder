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
| រត់ (sprint) | Shift | 🏃 |
| ហក់ (jump) | Space | ⤒ |
| យប់ / ថ្ងៃ | N | 🌙 |
| ផែនទីកម្ពុជា | M | ចុច radar ជ្រុងស្តាំ |
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

`CityDrive.apk` នៅក្នុង zip នេះ **រួចរាល់ដំឡើង** (signed debug APK, Android 6.0+):

1. ចម្លង `CityDrive.apk` ទៅទូរស័ព្ទ
2. បើកឯកសារ → អនុញ្ញាត "Install unknown apps"
3. ដំឡើង → បើក "City Drive"

ត្រូវការអ៊ីនធឺណិតពេលបើកដំបូង (three.js ពី CDN)។
គម្រោង build ពេញលេញ៖ [`android/README-APK.md`](android/README-APK.md)

## Live API (ស្រេចចិត្ត)

`server.js` ផ្តល់ `/api/live`, `/api/cmd`, `/api/shot` ដើម្បីមើល/បញ្ជាហ្គេមពីខាងក្រៅ។
មើល [`LIVE-API.md`](LIVE-API.md)។ token ស្ថិតក្នុង `.live-token` (មិនដាក់ក្នុង zip)។


## City Drive Balanced Realism Upgrade (2026-11-16)

This revision keeps the existing Three.js world, HUD, controls, map, traffic,
vehicle entry/exit, night mode, audio, and live-view API. Improvements:

- Balanced Android rendering defaults: capped pixel ratio, smaller shadow map,
  reduced bloom, and SD mode as the startup preset.
- Crowd budget reduced from 6,000 to 1,400 instanced pedestrians; detailed
  pedestrian animation is limited to the near-camera area.
- Vehicle speed capped at 180 km/h with gentler acceleration and braking.
- Four wheels now roll from vehicle speed and the front wheels steer.
- Vehicle body has subtle cornering roll and acceleration/braking pitch.
- Player walk/run gait is speed-driven with opposing arm/leg swing, foot motion,
  body bob, and smoother body lean.

The player and pedestrians are still procedural low-poly rigs, not motion-captured
GTA V character models. Vehicle handling is an arcade-style approximation, not a
full tire/suspension simulation. These are deliberate scope limits for a balanced
mobile build.
