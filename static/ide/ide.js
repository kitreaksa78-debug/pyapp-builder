/* PyApp IDE — browser Android IDE */
(function () {
  'use strict';

  var $ = function (id) { return document.getElementById(id); };
  var CFG = window.IDE_CONFIG || {};
  // បញ្ជីភាសាពេញលេញ — ត្រូវនឹង languages.py ខាង server
  var TEXT_EXT = ['html','htm','vue','css','scss','js','mjs','cjs','jsx','ts','tsx',
    'json','json5','md','txt','log','csv','xml','svg','yml','yaml','toml','ini','conf',
    'env','gitignore','editorconfig','properties','pro','gradle','groovy','kts','kt',
    'java','py','rb','php','lua','pl','r','go','rs','c','h','cc','cpp','cxx','hpp','hxx',
    'cs','swift','dart','sql','sh','bash','zsh','bat','cmd','ps1'];
  var BINARY_EXT = ['png','jpg','jpeg','webp','gif','ico','bmp','tif','tiff','ttf','otf',
    'woff','woff2','eot','mp3','wav','ogg','m4a','flac','mp4','webm','mkv','3gp','pdf',
    'zip','jar','apk','aab','so','bin','keystore','jks','p12'];
  var MONACO_LANG = {
    html:'html',htm:'html',vue:'html',css:'css',scss:'scss',
    js:'javascript',mjs:'javascript',cjs:'javascript',jsx:'javascript',
    ts:'typescript',tsx:'typescript',json:'json',json5:'json',
    md:'markdown',txt:'plaintext',log:'plaintext',csv:'plaintext',
    xml:'xml',svg:'xml',yml:'yaml',yaml:'yaml',toml:'ini',ini:'ini',conf:'ini',
    env:'ini',properties:'ini',gitignore:'plaintext',editorconfig:'ini',
    gradle:'groovy',groovy:'groovy',kts:'kotlin',kt:'kotlin',java:'java',
    py:'python',rb:'ruby',php:'php',lua:'lua',pl:'perl',r:'r',go:'go',rs:'rust',
    c:'c',h:'c',cc:'cpp',cpp:'cpp',cxx:'cpp',hpp:'cpp',hxx:'cpp',
    cs:'csharp',swift:'swift',dart:'dart',sql:'sql',
    sh:'shell',bash:'shell',zsh:'shell',bat:'bat',cmd:'bat',ps1:'powershell',
    pro:'plaintext'
  };
  var PERMS = [['INTERNET','Internet'],['ACCESS_NETWORK_STATE','Network state'],['ACCESS_WIFI_STATE','Wi-Fi state'],['CHANGE_WIFI_STATE','Change Wi-Fi'],['CHANGE_NETWORK_STATE','Change network'],['NEARBY_WIFI_DEVICES','Nearby Wi-Fi (API 33+)'],['BLUETOOTH','Bluetooth'],['BLUETOOTH_ADMIN','Bluetooth admin'],['BLUETOOTH_CONNECT','Connect Bluetooth'],['BLUETOOTH_SCAN','Scan Bluetooth'],['BLUETOOTH_ADVERTISE','Advertise Bluetooth'],['ACCESS_FINE_LOCATION','GPS location (fine)'],['ACCESS_COARSE_LOCATION','Coarse location'],['ACCESS_BACKGROUND_LOCATION','Background location'],['CAMERA','Camera'],['RECORD_AUDIO','Record audio'],['READ_EXTERNAL_STORAGE','Read storage'],['WRITE_EXTERNAL_STORAGE','Write storage'],['READ_MEDIA_IMAGES','Read images'],['READ_MEDIA_VIDEO','Read video'],['READ_MEDIA_AUDIO','Read audio'],['MANAGE_EXTERNAL_STORAGE','Manage all storage'],['POST_NOTIFICATIONS','Notifications (API 33+)'],['WAKE_LOCK','Wake lock'],['VIBRATE','Vibrate'],['FLASHLIGHT','Flashlight'],['READ_PHONE_STATE','Phone state'],['READ_PRIVILEGED_PHONE_STATE','Phone state (privileged)'],['CALL_PHONE','Call phone'],['ANSWER_PHONE_CALLS','Answer calls'],['READ_CALL_LOG','Read call log'],['WRITE_CALL_LOG','Write call log'],['ADD_VOICEMAIL','Add voicemail'],['USE_SIP','Use SIP'],['PROCESS_OUTGOING_CALLS','Process outgoing calls'],['READ_SMS','Read SMS'],['SEND_SMS','Send SMS'],['WRITE_SMS','Write SMS'],['READ_CONTACTS','Read contacts'],['WRITE_CONTACTS','Write contacts'],['GET_ACCOUNTS','Get accounts'],['READ_CALENDAR','Read calendar'],['WRITE_CALENDAR','Write calendar'],['SCHEDULE_EXACT_ALARM','Schedule exact alarm'],['USE_EXACT_ALARM','Use exact alarm'],['ACTIVITY_RECOGNITION','Activity recognition'],['BODY_SENSORS','Body sensors'],['BODY_SENSORS_BACKGROUND','Body sensors (bg)'],['NFC','NFC'],['NFC_TRANSACTION_ITEM','NFC transaction'],['NFC_HANDLES','NFC handles'],['REQUEST_IGNORE_BATTERY_OPTIMIZATIONS','Ignore battery optimization'],['FOREGROUND_SERVICE','Foreground service'],['FOREGROUND_SERVICE_CAMERA','FGS Camera'],['FOREGROUND_SERVICE_MICROPHONE','FGS Microphone'],['FOREGROUND_SERVICE_LOCATION','FGS Location'],['FOREGROUND_SERVICE_HEALTH','FGS Health'],['FOREGROUND_SERVICE_MEDIA_PLAYBACK','FGS Media'],['FOREGROUND_SERVICE_PHONE_CALL','FGS Phone call'],['FOREGROUND_SERVICE_SPECIAL_USE','FGS Special use'],['FOREGROUND_SERVICE_DATA_SYNC','FGS Data sync'],['FOREGROUND_SERVICE_CONNECTED_DEVICE','FGS Connected device'],['SYSTEM_ALERT_WINDOW','Show over other apps'],['PACKAGE_USAGE_STATS','Usage stats'],['QUERY_ALL_PACKAGES','Query all packages'],['READ_APP_SERVICES','Read app services'],['REQUEST_INSTALL_PACKAGES','Request install packages'],['BIND_NOTIFICATION_LISTENER_SERVICE','Bind notification listener'],['CaptivePortal','Captive portal'],['INSTALL_LOCATION','Install location'],['INSTALL_PACKAGES','Install packages'],['DELETE_PACKAGES','Delete packages'],['READ_PRIVILEGED_PHONE_STATE','Read privileged phone state']];

  var I18N = {
    km: {
      save: 'រក្សាទុក', preview: 'មើល', build: 'Build', explorer: 'ឯកសារ',
      exportZip: 'ទាញយក project (ZIP)', newProject: 'project ថ្មី',
      appName: 'ឈ្មោះ app', verName: 'Version name', verCode: 'Version code',
      appType: 'ប្រភេទ app', orientation: 'ទិសអេក្រង់', network: 'Network',
      icon: 'Icon', permissions: 'សិទ្ធិ Android', buildApk: 'បង្កើត APK',
      idle: 'មិនទាន់ build', logs: 'មើល log', theme: 'ពណ៌', language: 'ភាសា',
      fontSize: 'ទំហំអក្សរ', wordWrap: 'រុំបន្ទាត់', autoSave: 'រក្សាទុកស្វ័យប្រវត្តិ',
      resetProject: 'កំណត់ project ឡើងវិញ', settingsHint: 'ការកំណត់រក្សាទុកក្នុង browser នេះ។',
      history: 'ប្រវត្តិ', settings: 'ការកំណត់', clearHistory: 'លុបប្រវត្តិ',
      cancel: 'បោះបង់', ok: 'យល់ព្រម', menu: 'ឯកសារ',
      newFile: 'ឈ្មោះ file ថ្មី', newFolder: 'ឈ្មោះ folder ថ្មី',
      rename: 'ប្តូរឈ្មោះ', del: 'លុប', download: 'ទាញយក', open: 'បើក',
      saved: 'រក្សាទុករួច', saving: 'កំពុងរក្សាទុក…', built: 'រួចរាល់',
      failed: 'បរាជ័យ', running: 'កំពុង build…', noFile: 'គ្មាន file បើក',
      previewEmpty: 'រកមិនឃើញ index.html — បង្កើត index.html ដើម្បីមើល preview',
      replaceProject: 'ជំនួស project ទាំងមូលដោយ ZIP នេះ? (Cancel = បញ្ចូលបន្ថែម)',
      invalidName: 'ឈ្មោះមិនត្រឹមត្រូវ — ប្រើ a-z, 0-9, _, -, / និង .',
      confirmDel: 'លុបចោល?', copied: 'ចម្លងរួច',
      nativeNoPreview: 'Native Kotlin/Java គ្មាន preview — APK បង្ហាញ UI ពិត'
    },
    en: {
      save: 'Save', preview: 'Preview', build: 'Build', explorer: 'EXPLORER',
      nativeNoPreview: 'Native Kotlin/Java has no browser preview — the APK shows the real UI',
      exportZip: 'Export project (ZIP)', newProject: 'New project',
      appName: 'App name', verName: 'Version name', verCode: 'Version code',
      appType: 'App type', orientation: 'Orientation', network: 'Network',
      icon: 'Icon', permissions: 'Android permissions', buildApk: 'Build APK',
      idle: 'Not built yet', logs: 'View logs', theme: 'Theme', language: 'Language',
      fontSize: 'Font size', wordWrap: 'Word wrap', autoSave: 'Auto save',
      resetProject: 'Reset project', settingsHint: 'Settings are stored in this browser.',
      history: 'History', settings: 'Settings', clearHistory: 'Clear history',
      cancel: 'Cancel', ok: 'OK', menu: 'Explorer',
      newFile: 'New file path', newFolder: 'New folder path',
      rename: 'Rename', del: 'Delete', download: 'Download', open: 'Open',
      saved: 'Saved', saving: 'Saving…', built: 'Done', failed: 'Failed',
      running: 'Building…', noFile: 'No file open',
      previewEmpty: 'No index.html found — create index.html to preview',
      replaceProject: 'Replace the whole project with this ZIP? (Cancel = merge)',
      invalidName: 'Invalid name — use a-z, 0-9, _, -, / and .',
      confirmDel: 'Delete?', copied: 'Copied'
    }
  };
  function t(key) {
    var lang = state.settings.lang || 'km';
    return (I18N[lang] && I18N[lang][key]) || I18N.km[key] || key;
  }

  var readme = function (title) {
    return '# ' + title + '\n\nProject នេះបង្កើតដោយ PyApp IDE។\n\n' +
      '- `index.html` — ចំណុចចាប់ផ្តើម (WebView)\n' +
      '- `style.css` — រចនាប័ទ្ម\n' +
      '- `app.js` — logic\n\n' +
      'សម្រាប់ native: ប្រើ `MainActivity.kt` ឬ `MainActivity.java` ហើយជ្រើស app type\n' +
      'ជា Native Kotlin/Java ពេល Build។\n';
  };

  var TPL = {
    webview: {
      label: 'WebView App',
      desc: 'HTML + CSS + JS',
      files: function () {
        return {
          'index.html': '<!doctype html>\n<html lang="km">\n<head>\n' +
            '  <meta charset="utf-8">\n' +
            '  <meta name="viewport" content="width=device-width, initial-scale=1">\n' +
            '  <title>My App</title>\n' +
            '  <link rel="stylesheet" href="style.css">\n</head>\n<body>\n' +
            '  <header class="hero">\n    <h1>សួស្តី 👋</h1>\n' +
            '    <p>app នេះបង្កើតដោយ PyApp IDE</p>\n  </header>\n' +
            '  <main>\n    <div class="card">\n      <p>អ្នកបានចុច <b id="count">0</b> ដង</p>\n' +
            '      <button id="tap">ចុចទីនេះ</button>\n    </div>\n  </main>\n' +
            '  <script src="app.js"></script>\n</body>\n</html>\n',
          'style.css': ':root{--accent:#388bfd;--bg:#f4f6fb;--fg:#131a26}\n' +
            '*{box-sizing:border-box}\nbody{margin:0;font-family:system-ui,"Noto Sans Khmer",sans-serif;' +
            'background:var(--bg);color:var(--fg)}\n' +
            '.hero{background:linear-gradient(135deg,var(--accent),#6f9bff);color:#fff;padding:26px 20px}\n' +
            '.hero h1{margin:0 0 6px;font-size:28px}\n.hero p{margin:0;opacity:.9}\n' +
            'main{padding:16px}\n.card{background:#fff;border-radius:16px;padding:18px;' +
            'box-shadow:0 6px 18px #131a2612}\nbutton{width:100%;border:0;border-radius:12px;' +
            'padding:14px;background:var(--accent);color:#fff;font-size:16px;font-weight:600}\n',
          'app.js': 'var count = 0;\nvar out = document.getElementById("count");\n' +
            'document.getElementById("tap").addEventListener("click", function () {\n' +
            '  count += 1;\n  out.textContent = count;\n});\n',
          'README.md': readme('My App')
        };
      }
    },
    kotlin: {
      label: 'Kotlin Native',
      desc: 'MainActivity.kt + Android Views',
      files: function () {
        return {
          'MainActivity.kt': 'package org.pyapp.demo\n\nimport android.app.Activity\n' +
            'import android.graphics.Color\nimport android.os.Bundle\nimport android.view.Gravity\n' +
            'import android.widget.Button\nimport android.widget.LinearLayout\n' +
            'import android.widget.TextView\nimport android.widget.Toast\n\n' +
            'class MainActivity : Activity() {\n' +
            '    override fun onCreate(savedInstanceState: Bundle?) {\n' +
            '        super.onCreate(savedInstanceState)\n' +
            '        val pad = (20 * resources.displayMetrics.density).toInt()\n' +
            '        val root = LinearLayout(this).apply {\n' +
            '            orientation = LinearLayout.VERTICAL\n' +
            '            gravity = Gravity.CENTER\n' +
            '            setPadding(pad, pad, pad, pad)\n' +
            '            setBackgroundColor(Color.parseColor("#0D1117"))\n' +
            '        }\n' +
            '        val title = TextView(this).apply {\n' +
            '            text = "សួស្តី Kotlin 👋"\n' +
            '            textSize = 24f\n' +
            '            setTextColor(Color.WHITE)\n' +
            '            gravity = Gravity.CENTER\n' +
            '        }\n' +
            '        val button = Button(this).apply {\n' +
            '            text = "ចុចទីនេះ"\n' +
            '            setOnClickListener {\n' +
            '                title.text = "បានចុច 🎉"\n' +
            '                Toast.makeText(this@MainActivity, "Kotlin", Toast.LENGTH_SHORT).show()\n' +
            '            }\n' +
            '        }\n' +
            '        root.addView(title)\n        root.addView(button)\n' +
            '        setContentView(root)\n    }\n}\n',
          'README.md': readme('Kotlin App')
        };
      }
    },
    java: {
      label: 'Java Native',
      desc: 'MainActivity.java + Android Views',
      files: function () {
        return {
          'MainActivity.java': 'package org.pyapp.demo;\n\nimport android.app.Activity;\n' +
            'import android.graphics.Color;\nimport android.os.Bundle;\nimport android.view.Gravity;\n' +
            'import android.widget.Button;\nimport android.widget.LinearLayout;\n' +
            'import android.widget.TextView;\n\n' +
            'public class MainActivity extends Activity {\n' +
            '    @Override\n    protected void onCreate(Bundle state) {\n' +
            '        super.onCreate(state);\n' +
            '        float d = getResources().getDisplayMetrics().density;\n' +
            '        int pad = (int) (20 * d);\n' +
            '        LinearLayout root = new LinearLayout(this);\n' +
            '        root.setOrientation(LinearLayout.VERTICAL);\n' +
            '        root.setGravity(Gravity.CENTER);\n' +
            '        root.setPadding(pad, pad, pad, pad);\n' +
            '        root.setBackgroundColor(Color.parseColor("#0D1117"));\n' +
            '        final TextView title = new TextView(this);\n' +
            '        title.setText("សួស្តី Java 👋");\n' +
            '        title.setTextSize(24f);\n' +
            '        title.setTextColor(Color.WHITE);\n' +
            '        Button b = new Button(this);\n' +
            '        b.setText("ចុចទីនេះ");\n' +
            '        b.setOnClickListener(v -> title.setText("បានចុច 🎉"));\n' +
            '        root.addView(title);\n        root.addView(b);\n' +
            '        setContentView(root);\n    }\n}\n',
          'README.md': readme('Java App')
        };
      }
    },
    notes: {
      label: 'Notes App',
      desc: 'WebView + localStorage',
      files: function () {
        return {
          'index.html': '<!doctype html>\n<html lang="km">\n<head>\n' +
            '  <meta charset="utf-8">\n' +
            '  <meta name="viewport" content="width=device-width, initial-scale=1">\n' +
            '  <title>Notes</title>\n  <link rel="stylesheet" href="style.css">\n</head>\n' +
            '<body>\n  <header><h1>កត់ត្រា</h1></header>\n' +
            '  <main>\n    <textarea id="note" placeholder="សរសេរអ្វីមួយ…"></textarea>\n' +
            '    <p id="status" class="status"></p>\n  </main>\n' +
            '  <script src="app.js"></script>\n</body>\n</html>\n',
          'style.css': 'body{margin:0;background:#0d1117;color:#e6edf3;' +
            'font-family:system-ui,"Noto Sans Khmer",sans-serif}\n' +
            'header{padding:16px;border-bottom:1px solid #21262d}\nh1{margin:0;font-size:20px}\n' +
            'main{padding:16px}\ntextarea{width:100%;height:50vh;padding:12px;border-radius:10px;' +
            'border:1px solid #30363d;background:#161b22;color:#e6edf3;font-size:15px}\n' +
            '.status{color:#8b949e;font-size:12px}\n',
          'app.js': 'var box = document.getElementById("note");\n' +
            'var status = document.getElementById("status");\n' +
            'box.value = localStorage.getItem("note") || "";\n' +
            'box.addEventListener("input", function () {\n' +
            '  localStorage.setItem("note", box.value);\n' +
            '  status.textContent = "រក្សាទុករួច " + new Date().toLocaleTimeString();\n' +
            '});\n',
          'README.md': readme('Notes')
        };
      }
    }
,
    hello: {
      label: 'សួស្តី ពិភព',
      desc: 'Hello World native',
      files: function () {
        return {
          'MainActivity.kt': 'package org.pyapp.demo\n\nimport android.app.Activity\nimport android.os.Bundle\nimport android.widget.TextView\n\nclass MainActivity : Activity() {\n    override fun onCreate(savedInstanceState: Bundle?) {\n        super.onCreate(savedInstanceState)\n        val tv = TextView(this).apply {\n            text = \"សួស្តី ពិភព 👋\"\n            setTextColor(android.graphics.Color.WHITE)\n        }\n        setContentView(tv)\n    }\n}',
          'README.md': readme('សួស្តី ពិភព')
        };
      }
    },
    camera: {
      label: 'Camera App',
      desc: 'Camera + runtime perms',
      files: function () {
        return {
          'MainActivity.kt': 'package org.pyapp.demo\n\nimport android.Manifest\nimport android.app.Activity\nimport android.content.pm.PackageManager\nimport android.os.Bundle\nimport android.widget.Toast\nimport androidx.core.app.ActivityCompat\nimport androidx.core.content.ContextCompat\n\nclass MainActivity : Activity() {\n    private val REQUEST_CAMERA = 101\n    override fun onCreate(savedInstanceState: Bundle?) {\n        super.onCreate(savedInstanceState)\n        setContentView(android.view.View(this))\n        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)\n            != PackageManager.PERMISSION_GRANTED) {\n            ActivityCompat.requestPermissions(this,\n                arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA)\n        } else {\n            Toast.makeText(this, \"Camera ready\", Toast.LENGTH_SHORT).show()\n        }\n    }\n    override fun onRequestPermissionsResult(req: Int, perms: Array<out String>,\n                                            grants: IntArray) {\n        super.onRequestPermissionsResult(req, perms, grants)\n        if (req == REQUEST_CAMERA && grants.getOrNull(0) == PackageManager.PERMISSION_GRANTED) {\n            Toast.makeText(this, \"Camera granted\", Toast.LENGTH_SHORT).show()\n        }\n    }\n}',
          'README.md': readme('Camera App')
        };
      }
    },
    bluetooth: {
      label: 'Bluetooth Scanner',
      desc: 'Bluetooth scan',
      files: function () {
        return {
          'MainActivity.kt': 'package org.pyapp.demo\n\nimport android.app.Activity\nimport android.bluetooth.BluetoothAdapter\nimport android.bluetooth.BluetoothManager\nimport android.content.Context\nimport android.os.Bundle\nimport android.widget.Toast\n\nclass MainActivity : Activity() {\n    override fun onCreate(savedInstanceState: Bundle?) {\n        super.onCreate(savedInstanceState)\n        val bm = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager\n        val bt = bm.adapter\n        setContentView(android.view.View(this))\n        if (bt == null || !bt.isEnabled) {\n            Toast.makeText(this, \"Bluetooth off\", Toast.LENGTH_SHORT).show()\n        } else {\n            Toast.makeText(this, \"Bluetooth on: ${bt.name}\", Toast.LENGTH_SHORT).show()\n        }\n    }\n}',
          'README.md': readme('Bluetooth Scanner')
        };
      }
    },
    api_client: {
      label: 'API Client',
      desc: 'HTTP GET/POST',
      files: function () {
        return {
          'index.html': '<!doctype html>\n<html lang=\"km\">\n<head>\n  <meta charset=\"utf-8\">\n  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n  <title>API Client</title>\n  <style>\n  :root{--accent:#388bfd;--bg:#f4f6fb;--fg:#131a26}\n  body{margin:0;font-family:system-ui,sans-serif;background:var(--bg);color:var(--fg);padding:20px}\n  .card{background:#fff;border-radius:12px;padding:16px;margin-bottom:12px;box-shadow:0 2px 8px #131a2612}\n  textarea{width:100%;min-height:120px;font:14px/1.5 ui-monospace,Menlo,monospace;border:1px solid #d0d7de;border-radius:8px;padding:10px}\n  button{font:inherit;padding:12px 18px;border:0;border-radius:10px;background:var(--accent);color:#fff;font-weight:600}\n  h2{margin:0 0 8px}\n  </style>\n</head>\n<body>\n  <div class=\"card\"><h2>API Client</h2>\n    <textarea id=\"req\">GET https://jsonplaceholder.typicode.com/todos/1\nHeaders:\n  Accept: application/json\n</textarea>\n    <button id=\"go\">Run</button>\n  </div>\n  <div class=\"card\"><h2>Response</h2><pre id=\"resp\" style=\"white-space:pre-wrap;font:13px/1.5 ui-monospace,Menlo,monospace\">-</pre></div>\n  <script>\n  document.getElementById(\"go\").onclick = async () => {\n    var raw = document.getElementById(\"req\").value;\n    var lines = raw.split(\"\\\\n\");\n    var method = \"GET\", url = \"\", headers = {};\n    for (var i = 0; i < lines.length; i++) {\n      var ln = lines[i];\n      if (i === 0) { var p = ln.split(/\\s+/); method = p[0]; url = p[1] || \"\"; }\n      else if (ln.startsWith(\"Headers:\")) { /* skip */ }\n      else if (ln.includes(\":\")) { var k = ln.split(\":\")[0].trim(); var v = ln.slice(k.length+1).trim(); headers[k] = v; }\n    }\n    var out = document.getElementById(\"resp\");\n    try {\n      var r = await fetch(url, {method:method, headers: headers});\n      var txt = await r.text();\n      out.textContent = \"HTTP \" + r.status + \"\\\\n\" + txt.slice(0, 2000);\n    } catch (e) { out.textContent = \"Error: \" + e.message; }\n  };\n  </script>\n</body>\n</html>',
          'style.css': '',
          'app.js': '',
          'README.md': readme('API Client')
        };
      }
    },
    login: {
      label: 'Login App',
      desc: 'Login/register form',
      files: function () {
        return {
          'index.html': '<!doctype html>\n<html lang=\"km\">\n<head>\n  <meta charset=\"utf-8\">\n  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n  <title>Login</title>\n  <style>\n  :root{--accent:#388bfd;--bg:#f4f6fb;--fg:#131a26}\nbody{margin:0;font-family:system-ui,sans-serif;background:var(--bg);color:var(--fg);display:flex;align-items:center;justify-content:center;min-height:100%}\n.card{width:100%;max-width:360px;background:#fff;border-radius:16px;padding:22px;box-shadow:0 8px 24px #131a2612}\nh1{text-align:center;margin:0 0 4px;font-size:24px}small{text-align:center;color:#66718a;margin-bottom:16px;display:block}\ninput{width:100%;padding:12px;border:1px solid #d0d7de;border-radius:10px;font:15px/1.4 system-ui;margin-bottom:10px;background:#fff;color:#131a26}\nbutton{width:100%;padding:13px;border:0;border-radius:10px;background:var(--accent);color:#fff;font-weight:600;font-size:16px;margin-top:6px}\n.tab{display:flex;border-bottom:1px solid #d0d7de;margin-bottom:14px}\n.tab button{flex:1;border:0;background:#fff;font:inherit;padding:10px;border-radius:10px 10px 0 0;color:#66718a}\n.tab button.on{background:#fff;color:var(--accent);font-weight:700;border-bottom:2px solid var(--accent)}\n  </style>\n</head>\n<body>\n  <div class=\"card\">\n    <div class=\"tab\" id=\"tabbar\"><button data-tab=\"login\" class=\"on\">Login</button><button data-tab=\"register\">Register</button></div>\n    <h1 id=\"formTitle\">Login</h1><small id=\"formSub\">Email + password</small>\n    <form id=\"form\"></form>\n    <p id=\"msg\" style=\"color:#8b949e;font-size:13px;min-height:18px\"></p>\n  </div>\n  <script>\n(function(){\n  var tab = \"login\";\n  function show(t){\n    tab = t;\n    var bars = document.querySelectorAll(\".tab button\");\n    for (var i=0;i<bars.length;i++){\n      bars[i].classList.toggle(\"on\", bars[i].dataset.tab===t);\n    }\n    document.getElementById(\"formTitle\").textContent = t===\"login\"?\"Login\":\"Register new\";\n    document.getElementById(\"formSub\").textContent = t===\"login\"?\"Email + password\":\"Name + email + password\";\n    var form = document.getElementById(\"form\");\n    form.innerHTML = \"\";\n    var fields;\n    if (t===\"login\"){\n      fields = [\n        [\"input\",\"email\",\"email\",\"email\",\"required\"],\n        [\"input\",\"pass\",\"password\",\"password\",\"required\"],\n        [\"button\",\"submit\",\"Submit\"]\n      ];\n    } else {\n      fields = [\n        [\"input\",\"name\",\"text\",\"Name\",\"required\"],\n        [\"input\",\"email\",\"email\",\"email\",\"required\"],\n        [\"input\",\"pass\",\"password\",\"password\",\"required\"],\n        [\"button\",\"submit\",\"Submit\"]\n      ];\n    }\n    for (var i=0;i<fields.length;i++){\n      var f = fields[i];\n      var el = document.createElement(f[0]);\n      el.id = f[1];\n      el.type = f[0]===\"button\" ? \"submit\" : f[2];\n      if (f[1]!==\"submit\") el.placeholder = f[3];\n      if (f[4]) el.setAttribute(\"required\",\"\");\n      if (f[0]===\"button\") el.textContent = f[3];\n      form.appendChild(el);\n    }\n    document.getElementById(\"msg\").textContent = \"\";\n  }\n  document.querySelectorAll(\".tab button\").forEach(function(b){\n    b.addEventListener(\"click\", function(){show(b.dataset.tab);});\n  });\n  document.getElementById(\"form\").addEventListener(\"submit\", function(e){\n    e.preventDefault();\n    var email = document.getElementById(\"email\").value;\n    var msg = document.getElementById(\"msg\");\n    msg.textContent = \"You submitted \" + (tab===\"login\"?\"login\":\"register\") + \" with \" + email;\n    msg.style.color = \"#2f6df6\";\n  });\n  show(tab);\n})();\n  </script>\n</body>\n</html>',
          'style.css': '',
          'app.js': '',
          'README.md': readme('Login App')
        };
      }
    },
    database: {
      label: 'Database App',
      desc: 'SQLite on device',
      files: function () {
        return {
          'MainActivity.kt': 'package org.pyapp.demo\n\nimport android.app.Activity\nimport android.database.sqlite.SQLiteDatabase\nimport android.os.Bundle\nimport android.widget.TextView\n\nclass MainActivity : Activity() {\n    override fun onCreate(savedInstanceState: Bundle?) {\n        super.onCreate(savedInstanceState)\n        val open = SQLiteDatabase.openOrCreateDatabase(\n            java.io.File(filesDir, \"demo.db\"), null)\n        open.execSQL(\"CREATE TABLE IF NOT EXISTS notes (id INTEGER PRIMARY KEY, text TEXT)\")\n        open.execSQL(\"INSERT INTO notes (text) VALUES (\'Note #1\')\")\n        open.execSQL(\"INSERT INTO notes (text) VALUES (\'Note #2\')\")\n        val cur = open.rawQuery(\"SELECT id, text FROM notes ORDER BY id\", null)\n        val sb = StringBuilder()\n        sb.append(\"All notes (\").append(cur.count).append(\"):\n\")\n        while (cur.moveToNext()) {\n            sb.append(\"  #\").append(cur.getInt(0)).append(\" \").append(cur.getString(1)).append(\"\n\")\n        }\n        cur.close()\n        open.close()\n        val tv = TextView(this).apply { text = sb.toString() }\n        setContentView(tv)\n    }\n}',
          'README.md': readme('Database App')
        };
      }
    },
    notification: {
      label: 'Notification App',
      desc: 'Show notification',
      files: function () {
        return {
          'MainActivity.kt': 'package org.pyapp.demo\n\nimport android.app.NotificationChannel\nimport android.app.NotificationManager\nimport android.app.PendingIntent\nimport android.content.Intent\nimport android.os.Build\nimport android.os.Bundle\nimport android.widget.Button\nimport android.widget.LinearLayout\nimport androidx.core.app.NotificationCompat\n\nclass MainActivity : android.app.Activity() {\n    override fun onCreate(savedInstanceState: Bundle?) {\n        super.onCreate(savedInstanceState)\n        val lm = LinearLayout(this).apply {\n            orientation = LinearLayout.VERTICAL\n            setPadding(40, 120, 40, 40)\n        }\n        val btn = Button(this).apply {\n            text = \"Show notification\"\n            setOnClickListener {\n                val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager\n                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {\n                    val ch = NotificationChannel(\"demo\", \"Demo\", NotificationManager.IMPORTANCE_DEFAULT)\n                    nm.createNotificationChannel(ch)\n                }\n                val intent = Intent(this, MainActivity::class.java)\n                val pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)\n                val n = NotificationCompat.Builder(this, \"demo\").apply {\n                    setSmallIcon(android.R.drawable.ic_dialog_info)\n                    setContentTitle(\"Hello 👋\")\n                    setContentText(\"Notification from your app\")\n                    setContentIntent(pi)\n                    setAutoCancel(true)\n                }.build()\n                nm.notify(1, n)\n            }\n        }\n        lm.addView(btn)\n        setContentView(lm)\n    }\n}',
          'README.md': readme('Notification App')
        };
      }
    },
    multi_screen: {
      label: 'Multi-screen App',
      desc: '3 screens + navigation',
      files: function () {
        return {
          'MainActivity.kt': 'package org.pyapp.demo\n\nimport android.app.Activity\nimport android.content.Intent\nimport android.os.Bundle\nimport android.widget.Button\nimport android.widget.LinearLayout\nimport android.widget.TextView\n\nclass MainActivity : Activity() {\n    override fun onCreate(savedInstanceState: Bundle?) {\n        super.onCreate(savedInstanceState)\n        val root = LinearLayout(this).apply {\n            orientation = LinearLayout.VERTICAL\n            setPadding(24, 80, 24, 24)\n        }\n        root.addView(TextView(this).apply {\n            text = \"Hello 👋\"\n            setPadding(0, 0, 0, 24)\n        })\n        root.addView(Button(this).apply {\n            text = \"Go to Screen 2\"\n            setOnClickListener { startActivity(Intent(this@MainActivity, Screen2::class.java)) }\n        })\n        root.addView(Button(this).apply {\n            text = \"Go to Screen 3\"\n            setOnClickListener { startActivity(Intent(this@MainActivity, Screen3::class.java)) }\n        })\n        setContentView(root)\n    }\n}\n\nclass Screen2 : android.app.Activity() {\n    override fun onCreate(savedInstanceState: Bundle?) {\n        super.onCreate(savedInstanceState)\n        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }\n        root.addView(TextView(this).apply { text = \"Screen 2\"; setPadding(40, 120, 40, 40) })\n        root.addView(Button(this).apply { text = \"Back\"; setOnClickListener { finish() } })\n        setContentView(root)\n    }\n}\n\nclass Screen3 : android.app.Activity() {\n    override fun onCreate(savedInstanceState: Bundle?) {\n        super.onCreate(savedInstanceState)\n        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }\n        root.addView(TextView(this).apply { text = \"Screen 3\"; setPadding(40, 120, 40, 40) })\n        root.addView(Button(this).apply { text = \"Back\"; setOnClickListener { finish() } })\n        setContentView(root)\n    }\n}',
          'README.md': readme('Multi-screen App')
        };
      }
    },
    offline: {
      label: 'Offline App',
      desc: 'No internet',
      files: function () {
        return {
          'index.html': '<!doctype html>\n<html lang=\"km\">\n<head>\n  <meta charset=\"utf-8\">\n  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n  <title>Offline App</title>\n  <style>\n  body{margin:0;font-family:system-ui,sans-serif;background:#0d1117;color:#e6edf3;display:flex;align-items:center;justify-content:center;min-height:100%}\n.card{text-align:center;padding:24px;border-radius:16px;background:#161b22;width:100%;max-width:340px}\nh1{font-size:26px;margin:0 0 8px;color:#fff}\np{color:#8b949e}\n  </style>\n</head>\n<body>\n  <div class=\"card\">\n    <h1>Offline App</h1>\n    <p>This app works without internet — data stays on device.</p>\n    <p>You can use it anywhere.</p>\n  </div>\n</body>\n</html>',
          'style.css': '',
          'app.js': '',
          'README.md': readme('Offline App')
        };
      }
    },
  };

  /* ---- templates ថ្មី: C/C++ NDK និង Empty Android ---- */
  TPL.ndk = {
    label: 'C/C++ NDK',
    desc: 'JNI + CMake + Kotlin',
    files: function () {
      return {
        'MainActivity.kt': 'package org.pyapp.demo\n\nimport android.app.Activity\n' +
          'import android.os.Bundle\nimport android.widget.TextView\n\n' +
          'class MainActivity : Activity() {\n' +
          '    // C/C++ នៅ app/src/main/cpp/ → libnativeapp.so\n' +
          '    private external fun stringFromJNI(): String\n\n' +
          '    override fun onCreate(savedInstanceState: Bundle?) {\n' +
          '        super.onCreate(savedInstanceState)\n' +
          '        val tv = TextView(this).apply {\n' +
          '            textSize = 18f\n' +
          '            setPadding(48, 96, 48, 48)\n' +
          '            text = stringFromJNI()\n        }\n' +
          '        setContentView(tv)\n    }\n\n' +
          '    companion object {\n        init {\n' +
          '            System.loadLibrary("nativeapp")\n        }\n    }\n}\n',
        'native-lib.cpp': '#include <jni.h>\n#include <string>\n\n' +
          'extern "C" JNIEXPORT jstring JNICALL\n' +
          'Java_org_pyapp_demo_MainActivity_stringFromJNI(JNIEnv *env, jobject) {\n' +
          '    std::string hello = "Hello from C++ / NDK";\n' +
          '    return env->NewStringUTF(hello.c_str());\n}\n',
        'CMakeLists.txt': 'cmake_minimum_required(VERSION 3.22.1)\n' +
          'project("nativeapp")\n\nadd_library(nativeapp SHARED\n        native-lib.cpp)\n\n' +
          'target_compile_features(nativeapp PUBLIC cxx_std_17)\n\n' +
          'find_library(log-lib log)\ntarget_link_libraries(nativeapp ${log-lib})\n',
        'README.md': readme('C/C++ NDK')
      };
    }
  };
  TPL.empty_android = {
    label: 'Empty Android',
    desc: 'Kotlin អប្បបរមា',
    files: function () {
      return {
        'MainActivity.kt': 'package org.pyapp.demo\n\nimport android.app.Activity\n' +
          'import android.os.Bundle\n\nclass MainActivity : Activity() {\n' +
          '    override fun onCreate(savedInstanceState: Bundle?) {\n' +
          '        super.onCreate(savedInstanceState)\n    }\n}\n',
        'README.md': readme('Empty Android')
      };
    }

  };

  var state = {
    name: 'My App', pkg: 'org.pyapp.demo',
    files: {}, folders: [], open: [], active: '', selFolder: '',
    settings: {theme: 'dark', lang: 'km', font: 14, wrap: 'on', auto: true},
    icon: '',
    build: {mode: 'auto', ver: '1.0', code: 1, orient: 'unspecified', online: true,
            artifact: 'both', perms: ['INTERNET']},
    history: []
  };

  /* ---------------- storage ---------------- */
  var dbPromise = null;
  function db() {
    if (dbPromise) return dbPromise;
    dbPromise = new Promise(function (res, rej) {
      if (!window.indexedDB) return rej(new Error('no indexedDB'));
      var r = indexedDB.open('pyapp-ide', 1);
      r.onupgradeneeded = function () { r.result.createObjectStore('kv'); };
      r.onsuccess = function () { res(r.result); };
      r.onerror = function () { rej(r.error); };
    });
    return dbPromise;
  }
  function store(mode) {
    return db().then(function (d) { return d.transaction('kv', mode).objectStore('kv'); });
  }
  function idbGet(key) {
    return store('readonly').then(function (s) {
      return new Promise(function (res) {
        var q = s.get(key);
        q.onsuccess = function () { res(q.result); };
        q.onerror = function () { res(null); };
      });
    });
  }
  function idbSet(key, value) {
    return store('readwrite').then(function (s) {
      return new Promise(function (res) { s.put(value, key); s.transaction.oncomplete = res; });
    }).catch(function () {
      try { localStorage.setItem('pyapp-ide-' + key, JSON.stringify(value)); } catch (e) {}
    });
  }

  var saveTimer = null;
  function persist(now) {
    clearTimeout(saveTimer);
    saveTimer = setTimeout(function () {
      idbSet('project', state).then(function () { status(t('saved'), 'ok'); });
    }, now ? 0 : 500);
  }

  /* ---------------- helpers ---------------- */
  function ext(path) {
    var base = String(path).split('/').pop();
    if (base.charAt(0) === '.' && base.indexOf('.', 1) === -1) return '';
    var i = base.lastIndexOf('.');
    return i < 0 ? '' : base.slice(i + 1).toLowerCase();
  }
  function isBinary(path) { return BINARY_EXT.indexOf(ext(path)) >= 0; }
  function isText(path) { return TEXT_EXT.indexOf(ext(path)) >= 0 || ext(path) === ''; }
  function esc(s) {
    return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;')
      .replace(/>/g, '&gt;').replace(/"/g, '&quot;');
  }
  function toast(msg, bad) {
    var el = $('toast');
    el.textContent = msg;
    el.style.borderColor = bad ? 'var(--bad)' : 'var(--line-2)';
    el.hidden = false;
    clearTimeout(toast._t);
    toast._t = setTimeout(function () { el.hidden = true; }, 2600);
  }
  function status(msg, cls) {
    var el = $('stState');
    el.textContent = msg;
    el.className = cls || '';
  }
  /* #region folder-paths */
  // Helper សុទ្ធ (គ្មាន DOM) — តេស្តដោយ static/ide/test_folder_paths.cjs
  function validPath(path) {
    return /^[A-Za-z0-9_.\-][A-Za-z0-9_\-.\/]{0,179}$/.test(path) &&
      path.split('/').every(function (p) { return p && p !== '.' && p !== '..'; });
  }
  // Helper សុទ្ធ (គ្មាន DOM) — តេស្តដោយ static/ide/test_folder_paths.cjs
  // ឈ្មោះ file ពីការ upload: space/តួអក្សរហាម → '-' (រក្សាអក្សរខ្មែរ)
  function safeName(path) {
    var parts = normFolder(path).split('/'), out = [];
    var banned = ' \\/:*?"<>|';
    for (var i = 0; i < parts.length; i++) {
      var src = parts[i], buf = '';
      for (var j = 0; j < src.length; j++) {
        var ch = src.charAt(j);
        buf += (banned.indexOf(ch) >= 0 || src.charCodeAt(j) < 32) ? '-' : ch;
      }
      while (buf.indexOf('--') >= 0) buf = buf.split('--').join('-');
      while (buf.charAt(0) === '-') buf = buf.slice(1);
      while (buf.slice(-1) === '-') buf = buf.slice(0, -1);
      if (buf) out.push(buf);
    }
    return out.join('/');
  }
  // "a/b//" → "a/b" ; "a\b" → "a/b"
  function normFolder(path) {
    var s = String(path == null ? '' : path);
    s = s.split(String.fromCharCode(92)).join('/').trim();
    while (s.indexOf('//') >= 0) s = s.split('//').join('/');
    while (s.charAt(0) === '/') s = s.slice(1);
    while (s.slice(-1) === '/') s = s.slice(0, -1);
    return s;
  }
  // "app/src/A.kt" → ["app", "app/src"] (folder មេទាំងអស់)
  function parentFolders(path) {
    var parts = String(path == null ? '' : path).split('/'), out = [];
    for (var i = 1; i < parts.length; i++) out.push(parts.slice(0, i).join('/'));
    return out;
  }
  // folder មិនអាចមានឈ្មោះដូច file ដែលមានស្រាប់
  function folderIssue(files, path) {
    return (files && files[path] != null) ? 'file' : '';
  }
  // ឈ្មោះមិនប៉ះទង្គិច — "a.png" → "a (2).png" (មិនលុប data ចាស់)
  function uniquePath(files, path) {
    if (!files || files[path] == null) return path;
    var slash = path.lastIndexOf('/');
    var dir = slash >= 0 ? path.slice(0, slash + 1) : '';
    var base = slash >= 0 ? path.slice(slash + 1) : path;
    var dot = base.lastIndexOf('.');
    var stem = dot > 0 ? base.slice(0, dot) : base;
    var tail = dot > 0 ? base.slice(dot) : '';
    for (var i = 2; i < 1000; i++) {
      var cand = dir + stem + ' (' + i + ')' + tail;
      if (files[cand] == null) return cand;
    }
    return dir + stem + '-' + Date.now() + tail;
  }
  // ដើមឈើ folder/file សម្រាប់ Project Explorer (pure → តេស្តបាន)
  // folder ត្រូវបានបង្កើតពី file path ដែរ ដូច្នេះ project ចាស់/import បង្ហាញត្រឹមត្រូវ
  function buildTree(folders, files) {
    var root = { dirs: {}, files: [] };
    (folders || []).forEach(function (f) {
      var node = root, parts = String(f).split('/');
      parts.forEach(function (p) {
        if (!p) return;
        node.dirs[p] = node.dirs[p] || { dirs: {}, files: [] };
        node = node.dirs[p];
      });
    });
    Object.keys(files || {}).forEach(function (path) {
      var node = root, parts = String(path).split('/');
      parts.forEach(function (p, i) {
        if (i === parts.length - 1) node.files.push({ name: p, path: path });
        else {
          node.dirs[p] = node.dirs[p] || { dirs: {}, files: [] };
          node = node.dirs[p];
        }
      });
    });
    return root;
  }

  // App type ពី file ដែលមាន (mode "auto") — pure → តេស្តបាន
  function detectMode(files) {
    var keys = Object.keys(files || {});
    function has(re) { return keys.some(function (p) { return re.test(p); }); }
    if (has(/\.(c|cc|cpp|cxx|h|hh|hpp)$/i)) return 'ndk';
    if (has(/\.kt$/i)) return 'kotlin';
    if (has(/\.java$/i)) return 'java';
    return 'webview';
  }
  /* #endregion */

  // App type ដែលកំពុងប្រើ ("auto" → គណនាពី file)
  function currentMode() {
    return state.build.mode === 'auto' ? detectMode(state.files) : state.build.mode;
  }

  /* ---------------- modal ---------------- */
  var modalOk = $('modalOk'), modalCancel = $('modalCancel');
  function closeModal() { $('modal').hidden = true; modalOk.onclick = null; }
  modalCancel.addEventListener('click', closeModal);
  $('modal').addEventListener('click', function (e) { if (e.target === $('modal')) closeModal(); });
  function showModal(title, html, opts) {
    opts = opts || {};
    $('modalTitle').textContent = title;
    $('modalBody').innerHTML = html;
    modalOk.textContent = opts.okText || t('ok');
    modalOk.hidden = !!opts.noOk;
    $('modal').hidden = false;
    modalOk.onclick = function () { if (opts.onOk) opts.onOk(); };
    var inp = $('modalBody').querySelector('input,textarea,select');
    if (inp) setTimeout(function () { inp.focus(); inp.select && inp.select(); }, 30);
    return $('modalBody');
  }
  function ask(title, def, cb) {
    showModal(title, '<input type="text" id="askInput" value="' + esc(def || '') + '">',
      { onOk: function () { var v = ($('askInput').value || '').trim(); closeModal(); cb(v); } });
    $('askInput').addEventListener('keydown', function (e) {
      if (e.key === 'Enter') modalOk.click();
    });
  }

  /* ---------------- new project (ទទេ) ---------------- */
  // បញ្ជី template លុបចេញពី UI — ចុច ＋ បង្កើត project ទទេភ្លាម, app type កំណត់ដោយ detectMode()
  function emptyReadme(title) {
    return '# ' + title + '\n\n' +
      'Project ទទេ — បន្ថែម file ភាសាណាក៏បានពីប៊ូតុង 📄, upload ឬ drag & drop។\n\n' +
      'App type កំណត់ស្វ័យប្រវត្តិពី file ដែលមាន៖\n' +
      '- .c / .cpp / .h → Native C/C++ (NDK + CMake)\n' +
      '- .kt → Native Kotlin\n' +
      '- .java → Native Java\n' +
      '- .html / .css / .js → WebView\n\n' +
      '## មុខងារទូរស័ព្ទពី JavaScript (WebView app)\n' +
      'WebView app មាន bridge ទៅ Kotlin ស្វ័យប្រវត្តិ — ហៅ `Native.*` ក្នុង JS៖\n' +
      '```js\n' +
      'Native.toast("សួស្តី");\n' +
      'Native.vibrate(60);\n' +
      'await Native.copy("text");          // clipboard\n' +
      'await Native.share("text", "ចែករំលែក");\n' +
      'Native.notify("ចំណងជើង", "សារ");     // notification\n' +
      'const img = await Native.pickImage(); // data URL ឬ null\n' +
      'const ok = await Native.permission("android.permission.CAMERA");\n' +
      'Native.info(); Native.net(); Native.battery();\n' +
      '```\n' +
      'ឯកសារ Android ផ្សេងៗ (Camera2, Bluetooth, Sensors) ត្រូវសរសេរជា Kotlin/Java\n' +
      'ដោយប្តូរ app type ទៅ Native Kotlin — ឬបន្ថែម method ក្នុង `MainActivity.kt`។\n';
  }

  function newEmptyProject(name) {
    var title = String(name || '').trim() || 'My App';
    state.files = {
      'index.html': '<!doctype html>\n<html lang="km">\n<head>\n  <meta charset="utf-8">\n' +
        '  <meta name="viewport" content="width=device-width, initial-scale=1">\n' +
        '  <title>' + esc(title) + '</title>\n</head>\n<body>\n  <h1>' + esc(title) +
        '</h1>\n  <p>សរសេរកូដនៅទីនេះ ឬបន្ថែម file ភាសាផ្សេង (Kotlin/Java/C/C++...)។</p>\n</body>\n</html>\n',
      'README.md': emptyReadme(title)
    };
    state.folders = [];
    state.open = [];
    state.active = '';
    state.selFolder = '';
    state.name = title;
    state.build.mode = 'auto';
    if (monacoReady) {
      Object.keys(models).forEach(function (k) { try { models[k].dispose(); } catch (e) {} });
    }
    models = {};
    persist(true);
    refreshAll();
    openFile(state.files['index.html'] ? 'index.html' : 'README.md');
    renderPreview();
  }

  function newProject(key) {
    var tpl = TPL[key] || TPL.webview;
    state.files = tpl.files();
    state.folders = [];
    state.open = [];
    state.active = '';
    state.selFolder = '';
    state.name = key === 'webview' ? 'My App' : tpl.label;
    var nat = {kotlin:'kotlin', java:'java', hello:'kotlin', camera:'kotlin',
      bluetooth:'kotlin', database:'kotlin', notification:'kotlin', multi_screen:'kotlin',
      ndk:'ndk', empty_android:'kotlin'};
    state.build.mode = nat[key] ? nat[key] : 'webview';
    if (state.build.mode === 'kotlin' || state.build.mode === 'java') {
      var src = state.build.mode === 'kotlin' ? 'MainActivity.kt' : 'MainActivity.java';
      if (state.files[src]) state.files[src] = state.files[src].replace('org.pyapp.demo', state.pkg);
      ['Screen2.kt','Screen3.kt'].forEach(function (f) {
        if (state.files[f]) state.files[f] = state.files[f].replace('org.pyapp.demo', state.pkg);
      });
    }
    if (monacoReady) {
      Object.keys(models).forEach(function (k) { try { models[k].dispose(); } catch (e) {} });
    }
    models = {};
    persist(true);
    refreshAll();
    var firstFile = (state.build.mode === 'kotlin' || state.build.mode === 'ndk')
      ? 'MainActivity.kt' :
      (state.build.mode === 'java' ? 'MainActivity.java' : 'index.html');
    if (state.files[firstFile]) openFile(firstFile);
    else { var keys = Object.keys(state.files).sort(); if (keys[0]) openFile(keys[0]); }
    renderPreview();
  }  /* ---------------- tree ---------------- */
  var collapsed = {};
  function treeModel() { return buildTree(state.folders, state.files); }
  function renderTree() {
    var host = $('tree');
    host.innerHTML = '';
    var root = treeModel();
    var wrap = document.createElement('div');
    function walk(node, base, depth) {
      Object.keys(node.dirs).sort().forEach(function (name) {
        var path = base ? base + '/' + name : name;
        var row = document.createElement('div');
        row.className = 'row indent-' + Math.min(depth + 1, 6) +
          (state.selFolder === path ? ' active' : '');
        row.title = path;
        row.innerHTML = '<span class="ic chev">' + (collapsed[path] ? '▸' : '▾') + '</span>' +
          '<span class="ic">' + (collapsed[path] ? '📁' : '📂') + '</span><span class="nm">' +
          esc(name) + '</span><button class="more" title="menu">⋮</button>';
        row.onclick = function (e) {
          if (e.target.classList.contains('more')) {
            e.stopPropagation(); folderMenu(path); return;
          }
          if (e.target.classList.contains('chev')) {
            collapsed[path] = !collapsed[path];
            renderTree();
            return;
          }
          // ជ្រើស folder → file/folder ថ្មីនឹងចូលទីនេះ
          state.selFolder = (state.selFolder === path ? '' : path);
          collapsed[path] = false;
          renderTree();
        };
        row.oncontextmenu = function (e) { e.preventDefault(); folderMenu(path); };
        wrap.appendChild(row);
        if (!collapsed[path]) {
          walk(node.dirs[name], path, depth + 1);
          wrap.appendChild(addRow(path, depth + 1));
        }
      });
      node.files.sort(function (a, b) { return a.name.localeCompare(b.name); })
        .forEach(function (f) {
          var row = document.createElement('div');
          row.className = 'row indent-' + Math.min(depth + 1, 6) +
            (state.active === f.path ? ' active' : '');
          row.title = f.path;
          var icon = /\.(png|jpe?g|webp|gif|ico)$/i.test(f.name) ? '🖼' :
            /\.(kt)$/i.test(f.name) ? '🟣' : /\.(java)$/i.test(f.name) ? '☕' :
            /\.(html?)$/i.test(f.name) ? '🌐' : /\.(css)$/i.test(f.name) ? '🎨' :
            /\.(js|mjs)$/i.test(f.name) ? '🟨' : /\.(json)$/i.test(f.name) ? '{}' :
            /\.(md)$/i.test(f.name) ? '📘' : '📄';
          row.innerHTML = '<span class="ic">' + icon + '</span><span class="nm">' +
            esc(f.name) + '</span><button class="more" title="menu">⋮</button>';
          row.onclick = function (e) {
            if (e.target.classList.contains('more')) { e.stopPropagation(); fileMenu(f.path); return; }
            openFile(f.path);
            closeSidebar();
          };
          row.oncontextmenu = function (e) { e.preventDefault(); fileMenu(f.path); };
          wrap.appendChild(row);
        });
    }
    walk(root, '', 0);
    if (!wrap.children.length) {
      wrap.innerHTML = '<p class="hint" style="padding:10px">គ្មាន file — ចុច 📄 ដើម្បីបង្កើត។</p>';
    }
    host.appendChild(wrap);
  }

  // បន្ទាត់ "＋ file" នៅខាងក្នុង folder នីមួយៗ — ចុច → បង្កើត file ក្នុង folder នោះ
  function addRow(folder, depth) {
    var row = document.createElement('div');
    row.className = 'row addrow indent-' + Math.min(depth, 6);
    row.title = 'បង្កើត file ក្នុង ' + folder + '/';
    row.innerHTML = '<span class="ic">＋</span><span class="nm">file</span>';
    row.onclick = function (e) { e.stopPropagation(); createFileIn(folder); };
    row.oncontextmenu = function (e) { e.preventDefault(); folderMenu(folder); };
    return row;
  }

  function fileMenu(path) {
    var body = showModal(path,
      '<div style="display:flex;flex-direction:column;gap:6px">' +
      '<button class="btn ghost" data-act="open">📂 ' + t('open') + '</button>' +
      '<button class="btn ghost" data-act="rename">✏️ ' + t('rename') + '</button>' +
      '<button class="btn ghost" data-act="download">⬇ ' + t('download') + '</button>' +
      '<button class="btn ghost" data-act="delete">🗑 ' + t('del') + '</button></div>',
      { noOk: true });
    body.querySelectorAll('[data-act]').forEach(function (b) {
      b.onclick = function () {
        var act = b.getAttribute('data-act');
        closeModal();
        if (act === 'open') openFile(path);
        if (act === 'rename') renamePath(path);
        if (act === 'download') downloadFile(path);
        if (act === 'delete') deletePath(path);
      };
    });
  }
  function folderMenu(path) {
    var body = showModal(path,
      '<div style="display:flex;flex-direction:column;gap:6px">' +
      '<button class="btn ghost" data-act="file">📄 file ថ្មីក្នុង ' + esc(path) + '/</button>' +
      '<button class="btn ghost" data-act="sub">📁 folder ថ្មីក្នុង ' + esc(path) + '/</button>' +
      '<button class="btn ghost" data-act="upload">⬆ upload ចូល folder នេះ</button>' +
      '<button class="btn ghost" data-act="rename">✏️ ' + t('rename') + '</button>' +
      '<button class="btn ghost" data-act="delete">🗑 ' + t('del') + '</button></div>',
      { noOk: true });
    body.querySelectorAll('[data-act]').forEach(function (b) {
      b.onclick = function () {
        var act = b.getAttribute('data-act');
        closeModal();
        if (act === 'file') createFileIn(path);
        if (act === 'sub') createFolderIn(path);
        if (act === 'upload') { state.selFolder = path; renderTree(); $('uploadInput').click(); }
        if (act === 'rename') renamePath(path, true);
        if (act === 'delete') deletePath(path, true);
      };
    });
  }

  function renamePath(path, isFolder) {
    ask(t('rename'), path, function (next) {
      if (!next || next === path) return;
      if (!validPath(next)) return toast(t('invalidName'), true);
      if (isFolder) {
        var renamed = normFolder(next);
        if (!renamed || renamed === path) return;
        if (!validPath(renamed)) return toast(t('invalidName'), true);
        if (folderIssue(state.files, renamed)) return toast('File មានរួចហើយ', true);
        var prefix = path + '/';
        // ប្តូរឈ្មោះ folder + folder កូនទាំងអស់
        state.folders = state.folders.map(function (f) {
          if (f === path) return renamed;
          if (f.indexOf(prefix) === 0) return renamed + f.slice(path.length);
          return f;
        }).filter(function (f, i, arr) { return arr.indexOf(f) === i; });
        // ប្តូរ path file ទាំងអស់ក្នុង folder (រក្សាទិន្នន័យ)
        Object.keys(state.files).forEach(function (p) {
          if (p.indexOf(prefix) === 0) {
            state.files[renamed + p.slice(path.length)] = state.files[p];
            delete state.files[p];
          }
        });
        // tab ដែលបើក និង file សកម្ម
        state.open = state.open.map(function (p) {
          return p.indexOf(prefix) === 0 ? renamed + p.slice(path.length) : p;
        });
        if (state.active.indexOf(prefix) === 0) {
          state.active = renamed + state.active.slice(path.length);
        }
        if (state.selFolder === path) state.selFolder = renamed;
        else if (state.selFolder.indexOf(prefix) === 0) {
          state.selFolder = renamed + state.selFolder.slice(path.length);
        }
      } else {
        if (state.files[next] != null) return toast('File មានរួចហើយ', true);
        state.files[next] = state.files[path];
        delete state.files[path];
        state.open = state.open.map(function (p) { return p === path ? next : p; });
        if (state.active === path) state.active = next;
      }
      persist(true);
      refreshAll();
      renderEditor();
    });
  }
  function deletePath(path, isFolder) {
    showModal(t('del'), '<p>' + esc(t('confirmDel')) + '</p><p class="hint">' + esc(path) + '</p>',
      { okText: t('del'), onOk: function () {
        closeModal();
        if (isFolder) {
          state.folders = state.folders.filter(function (f) {
            return f !== path && f.indexOf(path + '/') !== 0;
          });
          Object.keys(state.files).forEach(function (p) {
            if (p.indexOf(path + '/') === 0) delete state.files[p];
          });
          if (state.selFolder === path || state.selFolder.indexOf(path + '/') === 0) {
            state.selFolder = '';
          }
        } else {
          delete state.files[path];
        }
        state.open = state.open.filter(function (p) { return state.files[p] != null; });
        if (!state.files[state.active]) state.active = state.open[0] || '';
        persist(true);
        refreshAll();
        renderEditor();
      } });
  }
  function downloadFile(path) {
    var content = state.files[path];
    var blob;
    var m = /^data:([^;]+);base64,(.*)$/.exec(content || '');
    if (m) {
      var bin = atob(m[2]), arr = new Uint8Array(bin.length);
      for (var i = 0; i < bin.length; i++) arr[i] = bin.charCodeAt(i);
      blob = new Blob([arr], {type: m[1]});
    } else {
      blob = new Blob([content || ''], {type: 'text/plain;charset=utf-8'});
    }
    var a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = path.split('/').pop();
    a.click();
    setTimeout(function () { URL.revokeObjectURL(a.href); }, 3000);
  }

  /* ---------------- tabs & editor ---------------- */
  var monacoReady = false, editor = null, models = {}, fallback = $('fallback');
  function langOf(path) { return MONACO_LANG[ext(path)] || 'plaintext'; }

  function renderTabs() {
    var host = $('tabs');
    host.innerHTML = '';
    state.open.forEach(function (path) {
      var tab = document.createElement('div');
      tab.className = 'tab' + (path === state.active ? ' on' : '');
      tab.innerHTML = '<span>' + esc(path.split('/').pop()) + '</span>' +
        '<button class="x" title="Close">✕</button>';
      tab.onclick = function (e) {
        if (e.target.classList.contains('x')) {
          state.open = state.open.filter(function (p) { return p !== path; });
          if (state.active === path) state.active = state.open[state.open.length - 1] || '';
          renderTabs(); renderTree(); renderEditor();
          return;
        }
        openFile(path);
      };
      host.appendChild(tab);
    });
  }

  function openFile(path) {
    if (state.files[path] == null) return;
    state.active = path;
    if (state.open.indexOf(path) < 0) state.open.push(path);
    renderTabs(); renderTree(); renderEditor();
  }

  function renderEditor() {
    var path = state.active;
    $('stFile').textContent = path || '—';
    $('stLang').textContent = path ? langOf(path) : '';
    $('binary').hidden = true;
    if (!path) {
      $('editor').style.display = 'none';
      fallback.hidden = true;
      return;
    }
    if (isBinary(path)) {
      $('editor').style.display = 'none';
      fallback.hidden = true;
      var host = $('binary');
      host.hidden = false;
      var content = state.files[path] || '';
      host.innerHTML = '<b>' + esc(path) + '</b>' +
        (ext(path) === 'png' || ext(path) === 'jpg' || ext(path) === 'jpeg' ||
         ext(path) === 'webp' || ext(path) === 'gif' || ext(path) === 'svg'
          ? '<img src="' + content + '" alt="">' : '<p class="hint">binary asset</p>') +
        '<button class="btn ghost" id="binDownload">⬇ ' + t('download') + '</button>';
      $('binDownload').onclick = function () { downloadFile(path); };
      return;
    }
    $('binary').hidden = true;
    var value = String(state.files[path] == null ? '' : state.files[path]);
    if (monacoReady) {
      $('editor').style.display = '';
      fallback.hidden = true;
      var uri = 'inmemory://model/' + path;
      if (!models[path]) {
        models[path] = monaco.editor.createModel(value, langOf(path), monaco.Uri.parse(uri));
        models[path].onDidChangeContent(function () {
          if (state.active !== path) return;
          state.files[path] = models[path].getValue();
          edited();
        });
      } else if (models[path].getValue() !== value) {
        models[path].setValue(value);
      }
      editor.setModel(models[path]);
      editor.updateOptions({fontSize: state.settings.font,
        wordWrap: state.settings.wrap === 'on' ? 'on' : 'off'});
    } else {
      $('editor').style.display = 'none';
      fallback.hidden = false;
      fallback.value = value;
      fallback.style.fontSize = state.settings.font + 'px';
    }
  }

  function onFallbackInput() {
    if (!state.active) return;
    state.files[state.active] = fallback.value;
    edited();
  }

  var previewTimer = null;
  function edited() {
    status(t('saving'));
    clearTimeout(previewTimer);
    previewTimer = setTimeout(function () {
      if (state.settings.auto) persist();
      renderPreview();
      status(t('saved'), 'ok');
    }, 550);
  }

  /* ---------------- monaco ---------------- */
  function loadMonaco() {
    return new Promise(function (resolve) {
      if (!window.require) return resolve(false);
      try {
        window.require.config({paths: {vs: 'https://cdn.jsdelivr.net/npm/monaco-editor@0.52.2/min/vs'}});
      } catch (e) { return resolve(false); }
      var done = false;
      var timer = setTimeout(function () { if (!done) { done = true; resolve(false); } }, 12000);
      window.require(['vs/editor/editor.main'], function () {
        if (done) return;
        done = true; clearTimeout(timer);
        try {
          monaco.editor.defineTheme('pyapp-dark', {
            base: 'vs-dark', inherit: true,
            rules: [], colors: {'editor.background': '#0d1117'}
          });
          resolve(true);
        } catch (e) { resolve(false); }
      }, function () { if (!done) { done = true; clearTimeout(timer); resolve(false); } });
    });
  }

  async function setupEditor() {
    monacoReady = await loadMonaco();
    if (!monacoReady) {
      fallback.hidden = false;
      fallback.addEventListener('input', onFallbackInput);
      status('textarea mode', 'ok');
      return;
    }
    editor = monaco.editor.create($('editor'), {
      value: '', language: 'plaintext', theme: state.settings.theme === 'light' ? 'vs' : 'pyapp-dark',
      fontSize: state.settings.font, automaticLayout: true, minimap: {enabled: false},
      wordWrap: state.settings.wrap === 'on' ? 'on' : 'off', tabSize: 2, insertSpaces: true,
      scrollBeyondLastLine: false, smoothScrolling: true, renderWhitespace: 'none',
      bracketPairColorization: {enabled: true}, padding: {top: 8}
    });
    editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.KeyS, function () {
      persist(true); renderPreview(); toast(t('saved'));
    });
  }

  /* ---------------- preview ---------------- */
  function findIndex() {
    if (state.files['index.html'] != null) return 'index.html';
    var keys = Object.keys(state.files);
    for (var i = 0; i < keys.length; i++) if (keys[i].endsWith('/index.html')) return keys[i];
    return null;
  }
  function attr(tag, name) {
    var m = new RegExp(name + '\\s*=\\s*("([^"]*)"|\'([^\']*)\'|([^\\s>]+))', 'i').exec(tag);
    return m ? (m[2] != null ? m[2] : (m[3] != null ? m[3] : m[4])) : '';
  }
  function resolveLocal(ref, base) {
    if (!ref || /^(https?:|\/\/|data:|blob:|mailto:|tel:|#)/i.test(ref)) return null;
    var clean = ref.split('?')[0].split('#')[0].replace(/^\.\//, '');
    if (clean.charAt(0) === '/') clean = clean.slice(1);
    var tries = [base + clean, clean, base + clean.replace(/^(\.\.\/)+/, '')];
    for (var i = 0; i < tries.length; i++) if (state.files[tries[i]] != null) return tries[i];
    return null;
  }
  function cssInline(css, base) {
    return String(css).replace(/url\((['"]?)([^'")]+)\1\)/g, function (all, q, ref) {
      var p = resolveLocal(ref, base);
      return p ? 'url("' + state.files[p] + '")' : all;
    });
  }
  function previewHtml() {
    var idx = findIndex();
    if (!idx) return {html: '', error: t('previewEmpty')};
    var base = idx.indexOf('/') >= 0 ? idx.slice(0, idx.lastIndexOf('/') + 1) : '';
    var html = String(state.files[idx]);

    html = html.replace(/<link\b[^>]*>/gi, function (tag) {
      if (!/stylesheet/i.test(tag)) return tag;
      var p = resolveLocal(attr(tag, 'href'), base);
      return p ? '<style>\n' + cssInline(state.files[p], base) + '\n</style>' : tag;
    });
    html = html.replace(/<script\b[^>]*>\s*<\/script>/gi, function (tag) {
      var p = resolveLocal(attr(tag, 'src'), base);
      if (!p) return tag;
      return '<script>\n' + String(state.files[p]).replace(/<\/script/gi, '<\\/script') + '\n<\/script>';
    });
    html = html.replace(/(src|href)\s*=\s*("([^"]*)"|'([^']*)')/gi, function (all, name, q, d, s) {
      var ref = d != null ? d : s;
      var p = resolveLocal(ref, base);
      return p && isBinary(p) ? name + '="' + state.files[p] + '"' : all;
    });
    var shim = '(function(){try{Object.defineProperty(window,"localStorage",{value:' +
      '(function(){var m={};return{getItem:function(k){return Object.prototype.hasOwnProperty.call(m,k)?m[k]:null},' +
      'setItem:function(k,v){m[k]=String(v)},removeItem:function(k){delete m[k]},clear:function(){m={}}}})()})}catch(e){}})();';
    var hook = '<scr' + 'ipt>' + shim + 'window.addEventListener("error",function(e){' +
      'parent.postMessage({idePreview:"error",msg:(e.message||"error")+" @ "+(e.lineno||0)},"*")});' +
      'window.addEventListener("unhandledrejection",function(e){' +
      'parent.postMessage({idePreview:"error",msg:String((e.reason&&e.reason.message)||e.reason)},"*")});' +
      '</scr' + 'ipt>';
    html = /<\/body>/i.test(html) ? html.replace(/<\/body>/i, hook + '</body>') : html + hook;
    return {html: html, error: ''};
  }
  function renderPreview() {
    if ($('preview').classList.contains('hidden')) return;
    var r = previewHtml();
    var msg = $('pvMsg');
    if (r.error) {
      msg.textContent = r.error;
      msg.className = 'pv-msg bad';
      $('pvFrame').srcdoc = '<body style="font:14px system-ui;padding:24px;color:#57606a">' +
        esc(r.error) + '</body>';
      return;
    }
    msg.className = 'pv-msg';
    msg.textContent = 'index.html → ' + (findIndex() || '');
    $('pvFrame').srcdoc = r.html;
  }
  window.addEventListener('message', function (e) {
    var d = e.data;
    if (d && d.idePreview === 'error') {
      var msg = $('pvMsg');
      msg.textContent = 'JS error: ' + d.msg;
      msg.className = 'pv-msg bad';
    }
  });

  /* ---------------- file ops ---------------- */
  function createFileIn(folder) {
    var base = folder ? folder + '/' : '';
    ask(t('newFile'), base ? base + 'new-file.js' : 'src/new-file.js', function (path) {
      if (!path) return;
      if (!validPath(path)) return toast(t('invalidName'), true);
      if (state.files[path] != null) return toast('File មានរួចហើយ', true);
      if (state.folders.indexOf(path) >= 0) return toast('មាន folder ឈ្មោះនេះរួចហើយ', true);
      state.files[path] = '';
      parentFolders(path).forEach(function (f) {
        if (state.folders.indexOf(f) < 0) state.folders.push(f);
      });
      if (folder) { collapsed[folder] = false; state.selFolder = folder; }
      persist(true); refreshAll(); openFile(path);
    });
  }
  $('newFile').onclick = function () { createFileIn(state.selFolder); };
  function createFolderIn(folder) {
    var base = folder ? folder + '/' : '';
    ask(t('newFolder'), base ? base + 'assets' : 'assets', function (raw) {
      var path = normFolder(raw);
      if (!path) return;
      if (!validPath(path)) return toast(t('invalidName'), true);
      if (folderIssue(state.files, path)) {
        return toast('មាន file ឈ្មោះនេះរួចហើយ — folder និង file ដូចគ្នាមិនបានទេ', true);
      }
      var added = 0;
      parentFolders(path).concat([path]).forEach(function (f) {
        if (state.folders.indexOf(f) < 0) { state.folders.push(f); added++; }
      });
      collapsed[path] = false;
      state.selFolder = path;
      persist(true); refreshAll();
      toast(added ? 'Folder បង្កើតរួច · ' + path : 'Folder មានស្រាប់ · ' + path);
    });
  }
  $('newFolder').onclick = function () { createFolderIn(state.selFolder); };
  $('uploadBtn').onclick = function () { $('uploadInput').click(); };
  $('uploadInput').addEventListener('change', function () {
    var files = Array.prototype.slice.call(this.files || []);
    this.value = '';
    files.forEach(function (file) {
      // រក្សា folder របស់ file បើ browser ផ្តល់ webkitRelativePath
      var name = safeName(file.webkitRelativePath || file.name);
      if (!name) return;
      if (!validPath(name)) return toast('ឈ្មោះ file មិនត្រឹមត្រូវ: ' + name, true);
      if (!isText(name) && !isBinary(name)) {
        return toast('ប្រភេទ file .' + ext(name) + ' មិនគាំទ្រ', true);
      }
      if (file.size > 5 * 1024 * 1024) return toast(name + ' ធំពេក (5MB)', true);
      // upload ចូល folder ដែលបានជ្រើស (បើ file មកដោយខ្លួនឯង គ្មាន path)
      if (state.selFolder && name.indexOf('/') < 0) name = state.selFolder + '/' + name;
      var target = uniquePath(state.files, name);
      var reader = new FileReader();
      var binary = isBinary(name);
      reader.onload = function () {
        state.files[target] = reader.result;
        parentFolders(target).forEach(function (f) {
          if (state.folders.indexOf(f) < 0) state.folders.push(f);
        });
        persist(true); refreshAll();
      };
      if (binary) reader.readAsDataURL(file); else reader.readAsText(file);
    });
  });
  $('importBtn').onclick = function () { $('zipInput').click(); };
  $('zipInput').addEventListener('change', function () {
    var file = this.files && this.files[0];
    this.value = '';
    if (!file) return;
    if (file.size > 40 * 1024 * 1024) return toast('ZIP ធំពេក (40MB)', true);
    var fd = new FormData();
    fd.append('file', file);
    status('importing…');
    fetch('/api/ide/import', {method: 'POST', body: fd})
      .then(function (r) { return r.json().then(function (j) { return {ok: r.ok, j: j}; }); })
      .then(function (res) {
        if (!res.ok) throw new Error(res.j.error || 'import failed');
        var replace = window.confirm(t('replaceProject'));
        if (replace) { state.files = {}; state.folders = []; state.open = []; state.active = ''; }
        Object.keys(res.j.files).forEach(function (p) {
          state.files[p] = res.j.files[p];
          // បង្កើត folder មេរបស់ file ដែល import ចូល
          parentFolders(p).forEach(function (f) {
            if (state.folders.indexOf(f) < 0) state.folders.push(f);
          });
        });
        persist(true); refreshAll(); renderEditor();
        toast('Import រួចរាល់ · ' + res.j.count + ' file');
        status(t('saved'), 'ok');
      })
      .catch(function (e) { toast(e.message, true); status(t('failed'), 'bad'); });
  });

  function currentFiles() {
    var out = {};
    Object.keys(state.files).forEach(function (p) { out[p] = state.files[p]; });
    return out;
  }
  function exportZip() {
    status('ZIP…');
    fetch('/api/ide/zip', {
      method: 'POST', headers: {'Content-Type': 'application/json'},
      body: JSON.stringify({name: state.name, files: currentFiles()})
    }).then(function (r) {
      if (!r.ok) return r.json().then(function (j) { throw new Error(j.error || 'zip failed'); });
      return r.blob();
    }).then(function (blob) {
      var a = document.createElement('a');
      a.href = URL.createObjectURL(blob);
      a.download = (state.name || 'project').replace(/[^\w\-]+/g, '-') + '.zip';
      a.click();
      setTimeout(function () { URL.revokeObjectURL(a.href); }, 4000);
      status(t('saved'), 'ok');
    }).catch(function (e) { toast(e.message, true); });
  }
  $('zipBtn').onclick = exportZip;
  $('exportBtn').onclick = exportZip;
  $('buildZipBtn').onclick = exportZip;

  /* ---------------- build ---------------- */
  var pollTimer = null;
  function buildForm() {
    $('bTitle').value = state.name;
    $('bPkg').value = state.pkg;
    $('bVer').value = state.build.ver;
    $('bCode').value = state.build.code;
    $('bMode').value = state.build.mode;
    $('bOrient').value = state.build.orient;
    $('bOnline').value = state.build.online ? '1' : '0';
    if ($('bArtifact')) $('bArtifact').value = state.build.artifact || 'both';
    var host = $('perms');
    host.innerHTML = '';
    PERMS.forEach(function (p) {
      var label = document.createElement('label');
      var checked = state.build.perms.indexOf(p[0]) >= 0;
      label.innerHTML = '<input type="checkbox" value="' + p[0] + '"' +
        (checked ? ' checked' : '') + '>' + p[1];
      host.appendChild(label);
    });
    host.querySelectorAll('input').forEach(function (inp) {
      inp.onchange = function () {
        state.build.perms = Array.prototype.slice.call(host.querySelectorAll('input:checked'))
          .map(function (i) { return i.value; });
        persist();
      };
    });
    $('ghState').textContent = CFG.ghReady
      ? 'GitHub Actions · ' + CFG.repo : 'GH_REPO/GH_TOKEN មិនបានកំណត់';
    $('ghState').className = 'hint' + (CFG.ghReady ? '' : ' bad');
  }
  ['bTitle','bPkg','bVer','bCode','bMode','bOrient','bOnline','bArtifact'].forEach(function (id) {
    $(id).addEventListener('change', function () {
      if (id === 'bTitle') { state.name = $('bTitle').value.trim() || 'My App'; }
      if (id === 'bPkg') { state.pkg = $('bPkg').value.trim(); }
      if (id === 'bVer') { state.build.ver = $('bVer').value.trim() || '1.0'; }
      if (id === 'bCode') { state.build.code = parseInt($('bCode').value, 10) || 1; }
      if (id === 'bMode') {
        state.build.mode = $('bMode').value;
        syncPreviewMode();
      }
      if (id === 'bOrient') { state.build.orient = $('bOrient').value; }
      if (id === 'bOnline') { state.build.online = $('bOnline').value === '1'; }
      if (id === 'bArtifact') { state.build.artifact = $('bArtifact').value; }
      persist(); renderProjectName();
    });
  });

  function setProgress(pct, cls) {
    var fill = $('barFill');
    fill.style.width = Math.max(0, Math.min(100, pct)) + '%';
    fill.className = cls || '';
  }
  function setBuildState(text, cls) {
    var el = $('buildState');
    el.textContent = text;
    el.className = 'hint' + (cls ? ' ' + cls : '');
  }
  function appendLog(line) {
    var log = $('buildLog');
    log.hidden = false;
    log.textContent += line + '\n';
  }

  function startBuild() {
    if (pollTimer) { clearInterval(pollTimer); pollTimer = null; }
    $('dlApk').hidden = true; $('runLink').hidden = true; $('logsBtn').hidden = true;
    if ($('dlAab')) $('dlAab').hidden = true;
    renderProblems([]);
    $('buildLog').textContent = ''; $('buildLog').hidden = true;
    $('buildSteps').innerHTML = '';
    setProgress(1);
    setBuildState(t('running'));
    var filesNow = currentFiles();
    var payload = {
      title: $('bTitle').value.trim() || state.name,
      package: $('bPkg').value.trim() || state.pkg,
      version: $('bVer').value.trim() || '1.0',
      build: parseInt($('bCode').value, 10) || 1,
      mode: currentMode(),
      artifact: (state.build.artifact || ($('bArtifact') ? $('bArtifact').value : 'apk')),
      orientation: $('bOrient').value,
      online: $('bOnline').value === '1',
      permissions: state.build.perms,
      icon: state.icon || '',
      files: filesNow
    };
    if (!payload.title || !/^[a-z][a-z0-9_]{1,30}(\.[a-z][a-z0-9_]{1,30}){1,3}$/.test(payload.package)) {
      setBuildState('package មិនត្រឹមត្រូវ — ឧ. org.pyapp.demo', 'bad');
      setProgress(0, 'bad');
      return;
    }
    $('buildBtn').disabled = true;
    appendLog('POST /api/ide/build · mode=' + payload.mode + ' · artifact=' +
      payload.artifact + ' · files=' + Object.keys(payload.files).length);
    fetch('/api/ide/build', {
      method: 'POST', headers: {'Content-Type': 'application/json'},
      body: JSON.stringify(payload)
    }).then(function (r) {
      return r.json().then(function (j) { return {ok: r.ok, j: j}; });
    }).then(function (res) {
      if (!res.ok) throw new Error(res.j.error || 'build request failed');
      appendLog('job ' + res.j.id + ' · ' + res.j.status);
      addHistory({id: res.j.id, title: payload.title, time: Date.now(),
        status: res.j.status, job_url: res.j.job_url});
      pollJob(res.j);
    }).catch(function (e) {
      $('buildBtn').disabled = false;
      setBuildState(e.message, 'bad');
      setProgress(0, 'bad');
      appendLog('ERROR: ' + e.message);
    });
  }

  function pollJob(job) {
    var started = Date.now();
    var last = '';
    function tick() {
      fetch(job.status_url).then(function (r) { return r.json(); }).then(function (j) {
        var pct = (j.progress && j.progress.percent) || 0;
        if (j.status === 'building' || j.status === 'queued') {
          setProgress(pct);
          setBuildState((j.progress && j.progress.step) || j.note || t('running'));
          if (j.progress && j.progress.steps) {
            $('buildSteps').innerHTML = j.progress.steps.map(function (s) {
              return '<span class="' + (s.state === 'done' ? 'done' : s.state === 'now' ? 'now' : '') +
                '">' + esc(s.name) + '</span>';
            }).join('');
          }
          if (j.run_url) {
            $('runLink').href = j.run_url; $('runLink').hidden = false;
          }
          if (j.note && j.note !== last) { last = j.note; appendLog(j.note); }
          if (Date.now() - started > 12 * 60 * 1000) {
            clearInterval(pollTimer); pollTimer = null;
            $('buildBtn').disabled = false;
            setBuildState('timeout — ពិនិត្យ GitHub Actions', 'bad');
          }
          return;
        }
        clearInterval(pollTimer); pollTimer = null;
        $('buildBtn').disabled = false;
        if (j.status === 'done') {
          setProgress(100, 'ok');
          setBuildState(t('built') + ' · 100%', 'ok');
          var dl = $('dlApk');
          dl.href = '/dl/' + j.id + '?type=apk';
          dl.hidden = false;
          var dlb = $('dlAab');
          if (dlb) { dlb.href = '/dl/' + j.id + '?type=aab'; dlb.hidden = false; }
          if (j.run_url) { $('runLink').href = j.run_url; $('runLink').hidden = false; }
          $('logsBtn').hidden = false;
          $('logsBtn').onclick = function () { showLogs(j.id); };
          appendLog('Artifacts រួចរាល់ — APK + AAB (ចុច Download)');
          markHistory(j.id, 'done');
          loadProblems(j.id);
          toast('APK + AAB រួចរាល់ 🎉');
        } else {
          setProgress(0, 'bad');
          setBuildState(j.error || t('failed'), 'bad');
          appendLog('ERROR: ' + (j.error || 'build failed'));
          $('logsBtn').hidden = false;
          $('logsBtn').onclick = function () { showLogs(j.id); };
          if (j.run_url) { $('runLink').href = j.run_url; $('runLink').hidden = false; }
          markHistory(j.id, 'error');
          loadProblems(j.id);
        }
      }).catch(function () {});
    }
    tick();
    pollTimer = setInterval(tick, 4000);
  }

  function showLogs(jobId) {
    var log = $('buildLog');
    log.hidden = false;
    log.textContent = 'កំពុងទាញ log…';
    fetch('/api/jobs/' + jobId + '/logs').then(function (r) { return r.json(); })
      .then(function (j) {
        log.textContent = j.logs || j.note || 'គ្មាន log';
        if (j.note) log.textContent += '\n' + j.note;
        renderProblems(j.problems || []);
      }).catch(function (e) { log.textContent = 'អាន log មិនបាន: ' + e.message; });
  }

  /* ---------------- Problems (ពី build log ពិត) ---------------- */
  // log មាន path ពេញ (java/org/…/MainActivity.kt) — រក file ក្នុង project តាម suffix
  function resolveFile(p) {
    if (!p) return '';
    if (state.files[p]) return p;
    var keys = Object.keys(state.files);
    var i;
    for (i = 0; i < keys.length; i++) {
      if (keys[i].slice(-p.length - 1) === '/' + p) return keys[i];
    }
    var base = p.split('/').pop();
    for (i = 0; i < keys.length; i++) {
      if (keys[i].split('/').pop() === base) return keys[i];
    }
    return '';
  }
  function renderProblems(items) {
    var host = $('problems');
    if (!host) return;
    host.innerHTML = '';
    if (!items || !items.length) { host.hidden = true; return; }
    host.hidden = false;
    items.forEach(function (p) {
      var row = document.createElement('div');
      var target = resolveFile(p.file);
      row.style.cssText = 'padding:4px 8px;border-left:3px solid ' +
        (p.severity === 'warning' ? '#d29922' : '#f85149') +
        ';font:12px/1.5 ui-monospace,monospace;cursor:' +
        (target ? 'pointer' : 'default') + ';color:#c9d1d9';
      row.textContent = (p.file ? p.file + (p.line ? ':' + p.line : '') + ' · ' : '') + p.message;
      row.title = p.severity + (p.file ? ' — ' + p.file : '');
      row.onclick = function () { if (target) openFile(target); };
      host.appendChild(row);
    });
  }
  function loadProblems(jobId) {
    fetch('/api/jobs/' + jobId + '/logs').then(function (r) { return r.json(); })
      .then(function (j) { renderProblems(j.problems || []); })
      .catch(function () {});
  }
  $('buildBtn').onclick = startBuild;

  function addHistory(item) {
    state.history.unshift(item);
    state.history = state.history.slice(0, 30);
    persist(true);
    renderHistory();
  }
  function markHistory(id, status) {
    state.history.forEach(function (h) { if (h.id === id) h.status = status; });
    persist(true);
    renderHistory();
  }
  function renderHistory() {
    var host = $('historyList');
    if (!state.history.length) {
      host.innerHTML = '<p class="hint">មិនទាន់មាន build។</p>';
      return;
    }
    host.innerHTML = state.history.map(function (h) {
      var badge = h.status === 'done' ? 'ok' : (h.status === 'error' ? 'bad' : '');
      return '<div class="hist-item"><div class="meta"><b>' + esc(h.title) + '</b><small>' +
        new Date(h.time).toLocaleString() + ' · ' + esc(h.id) + '</small></div>' +
        '<span class="badge ' + badge + '">' + esc(h.status) + '</span>' +
        '<a class="btn ghost" href="/dl/' + esc(h.id) + '" download>APK</a>' +
        '<a class="btn ghost" href="/j/' + esc(h.id) + '" target="_blank" rel="noopener">↗</a></div>';
    }).join('');
  }
  $('clearHistory').onclick = function () {
    showModal(t('clearHistory'), '<p>' + esc(t('confirmDel')) + '</p>', {
      onOk: function () { closeModal(); state.history = []; persist(true); renderHistory(); }
    });
  };

  /* ---------------- icon ---------------- */
  $('bIcon').addEventListener('change', function () {
    var file = this.files && this.files[0];
    if (!file) return;
    if (file.size > 5 * 1024 * 1024) { toast('រូបធំពេក (5MB)', true); this.value = ''; return; }
    var reader = new FileReader();
    reader.onload = function () { state.icon = reader.result; persist(); toast('Logo រួចរាល់'); };
    reader.readAsDataURL(file);
  });
  $('iconGen').onclick = function () {
    showModal('Icon generator',
      '<div style="display:flex;gap:12px;flex-wrap:wrap;align-items:flex-start">' +
      '<canvas id="iconCanvas" width="256" height="256"></canvas>' +
      '<div style="display:flex;flex-direction:column;gap:8px;min-width:160px">' +
      '<div class="field"><label>Letter / emoji</label><input id="igText" type="text" maxlength="2" value="P"></div>' +
      '<div class="field"><label>Background</label><input id="igBg" type="color" value="#388bfd"></div>' +
      '<div class="field"><label>Text color</label><input id="igFg" type="color" value="#ffffff"></div>' +
      '<div class="field"><label>Shape</label><select id="igShape"><option value="round">Rounded</option>' +
      '<option value="circle">Circle</option></select></div></div></div>',
      { okText: 'Use icon', onOk: function () {
        var c = $('iconCanvas');
        state.icon = c.toDataURL('image/png');
        persist(true); closeModal(); toast('Icon រួចរាល់');
      } });
    function draw() {
      var c = $('iconCanvas'), g = c.getContext('2d'), w = c.width;
      g.clearRect(0, 0, w, w);
      var shape = $('igShape').value;
      g.fillStyle = $('igBg').value;
      if (shape === 'circle') {
        g.beginPath(); g.arc(w / 2, w / 2, w / 2, 0, Math.PI * 2); g.fill();
      } else {
        var r = w * 0.22;
        g.beginPath();
        g.moveTo(r, 0); g.lineTo(w - r, 0); g.quadraticCurveTo(w, 0, w, r);
        g.lineTo(w, w - r); g.quadraticCurveTo(w, w, w - r, w);
        g.lineTo(r, w); g.quadraticCurveTo(0, w, 0, w - r);
        g.lineTo(0, r); g.quadraticCurveTo(0, 0, r, 0); g.fill();
      }
      g.fillStyle = $('igFg').value;
      g.font = 'bold ' + Math.round(w * 0.5) + 'px system-ui, sans-serif';
      g.textAlign = 'center'; g.textBaseline = 'middle';
      g.fillText($('igText').value || 'A', w / 2, w / 2 + w * 0.03);
    }
    ['igText','igBg','igFg','igShape'].forEach(function (id) {
      $(id).addEventListener('input', draw);
      $(id).addEventListener('change', draw);
    });
    draw();
  };

  /* ---------------- panel & settings ---------------- */
  function openPanel(tab) {
    $('panel').hidden = false;
    document.querySelectorAll('.panel-tabs [data-tab]').forEach(function (b) {
      b.classList.toggle('on', b.getAttribute('data-tab') === tab);
    });
    document.querySelectorAll('[data-pane]').forEach(function (p) {
      p.hidden = p.getAttribute('data-pane') !== tab;
    });
  }
  document.querySelectorAll('.panel-tabs [data-tab]').forEach(function (b) {
    b.onclick = function () { openPanel(b.getAttribute('data-tab')); };
  });
  $('panelClose').onclick = function () { $('panel').hidden = true; };
  $('buildShow').onclick = function () { openPanel('build'); };
  $('settingsTop').onclick = function () { openPanel('settings'); };
  $('settingsBtn') && ($('settingsBtn').onclick = function () { openPanel('settings'); });

  function applyTheme() {
    document.documentElement.setAttribute('data-theme', state.settings.theme);
    if (monacoReady && editor) {
      monaco.editor.setTheme(state.settings.theme === 'light' ? 'vs' : 'pyapp-dark');
    }
  }
  function applyLang() {
    document.documentElement.lang = state.settings.lang;
    document.querySelectorAll('[data-i18n]').forEach(function (el) {
      var k = el.getAttribute('data-i18n');
      if (I18N[state.settings.lang] && I18N[state.settings.lang][k]) {
        el.textContent = I18N[state.settings.lang][k];
      }
    });
    renderTabs();
  }
  function syncSettings() {
    $('setTheme').value = state.settings.theme;
    $('setLang').value = state.settings.lang;
    $('setFont').value = String(state.settings.font);
    $('setWrap').value = state.settings.wrap;
    $('setAuto').value = state.settings.auto ? '1' : '0';
  }
  $('setTheme').onchange = function () { state.settings.theme = this.value; applyTheme(); persist(); };
  $('setLang').onchange = function () { state.settings.lang = this.value; applyLang(); persist(); };
  $('setFont').onchange = function () {
    state.settings.font = parseInt(this.value, 10) || 14;
    if (editor) editor.updateOptions({fontSize: state.settings.font});
    if (!monacoReady) fallback.style.fontSize = state.settings.font + 'px';
    persist();
  };
  $('setWrap').onchange = function () {
    state.settings.wrap = this.value;
    if (editor) editor.updateOptions({wordWrap: this.value === 'on' ? 'on' : 'off'});
    persist();
  };
  $('setAuto').onchange = function () { state.settings.auto = this.value === '1'; persist(); };
  $('resetProject').onclick = function () {
    showModal(t('resetProject'), '<p>' + esc(t('confirmDel')) + '</p>', {
      onOk: function () { closeModal(); newEmptyProject(state.name); }
    });
  };

  /* ---------------- layout toggles ---------------- */
  function closeSidebar() {
    $('sidebar').classList.remove('open');
    $('scrim').classList.remove('on');
  }
  $('menuBtn').onclick = function () {
    $('sidebar').classList.toggle('open');
    $('scrim').classList.toggle('on');
  };
  $('scrim').onclick = closeSidebar;
  /* Native projects គ្មាន HTML ⇒ preview មិនអាចប្រើ (បិទប៊ូតុងហើយលាក់ panel) */
  function syncPreviewMode() {
    var web = currentMode() === 'webview';
    var btn = $('pvToggle');
    btn.disabled = !web;
    btn.title = web ? t('preview') + ' (Ctrl+J)' : t('nativeNoPreview');
    if (!web) $('preview').classList.add('hidden');
  }
  window.syncPreviewMode = syncPreviewMode;

  $('pvToggle').onclick = function () {
    if (currentMode() !== 'webview') { toast(t('nativeNoPreview')); return; }
    $('preview').classList.toggle('hidden');
    renderPreview();
  };
  $('pvClose').onclick = function () { $('preview').classList.add('hidden'); };
  $('pvRefresh').onclick = renderPreview;
  $('pvDevice').onclick = function () {
    $('pvBody').classList.toggle('mobile');
  };
  $('collapseAll').onclick = function () {
    Object.keys(state.files).forEach(function (p) {
      var parts = p.split('/');
      for (var i = 1; i < parts.length; i++) collapsed[parts.slice(0, i).join('/')] = true;
    });
    renderTree();
  };
  $('saveBtn').onclick = function () { persist(true); renderPreview(); toast(t('saved')); };
  $('projBtn').onclick = function () {
    ask('Project name', state.name, function (v) { if (v) { state.name = v; persist(true); renderProjectName(); } });
  };
  $('newProjBtn').onclick = function () {
    ask(t('newProject'), 'My App', function (v) { newEmptyProject(v); });
  };

  document.addEventListener('keydown', function (e) {
    var mod = e.ctrlKey || e.metaKey;
    if (mod && e.key.toLowerCase() === 's') { e.preventDefault(); persist(true); renderPreview(); }
    if (mod && e.key.toLowerCase() === 'b') { e.preventDefault(); $('menuBtn').click(); }
    if (mod && e.key.toLowerCase() === 'j') { e.preventDefault(); $('pvToggle').click(); }
  });

  /* ---------------- boot ---------------- */
  function renderProjectName() {
    $('projName').textContent = state.name;
    $('projPkg').textContent = state.pkg;
  }
  function refreshAll() {
    renderTree(); renderTabs(); renderProjectName(); renderHistory(); buildForm();
  }

  async function boot() {
    var saved = null;
    try { saved = await idbGet('project'); } catch (e) {}
    if (saved && saved.files && Object.keys(saved.files).length) {
      state = Object.assign(state, saved);
      state.settings = Object.assign({theme: 'dark', lang: 'km', font: 14, wrap: 'on', auto: true},
        saved.settings || {});
      state.build = Object.assign({mode: 'webview', ver: '1.0', code: 1,
        orient: 'unspecified', online: true, artifact: 'both',
        perms: ['INTERNET']}, saved.build || {});
    } else {
      state.name = CFG.defaultTitle || 'My App';
      state.pkg = CFG.defaultPackage || 'org.pyapp.demo';
      state.files = TPL.webview.files();
    }
    applyTheme(); syncSettings(); applyLang();
    if (window.matchMedia && window.matchMedia('(max-width: 900px)').matches) {
      $('preview').classList.add('hidden');
    }
    syncPreviewMode();
    refreshAll();
    await setupEditor();
    if (!state.active) {
      var first = state.files['index.html'] != null ? 'index.html' :
        Object.keys(state.files).sort()[0];
      if (first) openFile(first);
    } else {
      renderTabs(); renderTree(); renderEditor();
    }
    renderPreview();
    if (window.matchMedia && window.matchMedia('(max-width: 900px)').matches) {
      $('preview').classList.add('hidden');
    }
    syncPreviewMode();
    if (CFG.ghReady === false) status('GH_REPO/GH_TOKEN មិនកំណត់', 'bad');
  }

  /* ---------------- ZIP → APK (Android project) ---------------- */
  var Z2A_STEPS = [
    ['upload', 'Upload ZIP', 'ជ្រើស Android project (.zip)'],
    ['validate', 'Extract & Validate', 'ពិនិត្យ Gradle, Manifest និង source'],
    ['build', 'Cloud Build APK', 'JDK + Android SDK + Gradle'],
    ['download', 'Download APK', 'Build Log និងប៊ូតុងទាញយក']
  ];

  function z2aMB(bytes) {
    var mb = (Number(bytes) || 0) / (1024 * 1024);
    return mb >= 1 ? mb.toFixed(1) + ' MB' : Math.max(1, Math.round(bytes / 1024)) + ' KB';
  }

  function z2aKB(bytes) {
    var n = Number(bytes) || 0;
    return n >= 1024 * 1024 ? (n / 1048576).toFixed(1) + ' MB'
      : n >= 1024 ? (n / 1024).toFixed(1) + ' KB' : n + ' B';
  }

  function z2aFiles(list, total) {
    var box = $('z2aFiles');
    var byDir = {}, dirs = [];
    (list || []).forEach(function (f) {
      var i = f.path.lastIndexOf('/');
      var d = i >= 0 ? f.path.slice(0, i) : '';
      if (!byDir[d]) { byDir[d] = []; dirs.push(d); }
      byDir[d].push(f);
    });
    var html = '';
    dirs.sort().forEach(function (d) {
      if (d) html += '<div class="z2a-dir">📁 ' + esc(d) + '/</div>';
      byDir[d].sort(function (a, b) { return a.path < b.path ? -1 : 1; }).forEach(function (f) {
        var base = f.path.split('/').pop();
        html += '<div class="z2a-file"><span class="z2a-fn">' + esc(base) + '</span>' +
          '<span class="z2a-fs">' + z2aKB(f.size) + '</span></div>';
      });
    });
    box.innerHTML = html;
    box.hidden = false;
    $('z2aFilesHead').textContent = 'ឯកសារក្នុង project (' + (total || (list || []).length) +
      ' file)' + (total > (list || []).length ? ' — បង្ហាញ ' + list.length : '');
  }

  function zipApk() {
    var steps = Z2A_STEPS.map(function (s) {
      return '<div class="z2a-step" id="z2a-' + s[0] + '">' +
        '<span class="z2a-dot">•</span><div><b>' + s[1] + '</b>' +
        '<div class="hint">' + s[2] + '</div>' +
        '<div class="z2a-note" id="z2a-' + s[0] + '-note"></div></div></div>';
    }).join('');
    showModal('ZIP → APK',
      '<div class="z2a" id="z2aSteps">' + steps + '</div>' +
      '<div class="z2a-pick" id="z2aPick">' +
        '<input type="file" id="z2aFile" accept=".zip,application/zip,application/x-zip-compressed">' +
        '<button class="btn" id="z2aGo">⬆ Upload &amp; មើលឯកសារ</button>' +
        '<p class="hint">Android project ត្រូវមាន <code>settings.gradle(.kts)</code>, ' +
        '<code>build.gradle(.kts)</code> និង <code>AndroidManifest.xml</code> — អតិបរមា 40 MB។' +
        '</p>' +
      '</div>' +
      '<div class="z2a-info" id="z2aInfo" hidden></div>' +
      '<div class="z2a-problems" id="z2aProblems" hidden></div>' +
      '<div class="z2a-fileshead" id="z2aFilesHead" hidden></div>' +
      '<div class="z2a-files" id="z2aFiles" hidden></div>' +
      '<p class="z2a-msg" id="z2aMsg" hidden></p>' +
      '<div class="z2a-actions" id="z2aBuildRow" hidden>' +
        '<button class="btn accent" id="z2aBuild">🔨 Build APK</button>' +
      '</div>' +
      '<div class="z2a-actions" id="z2aActions" hidden>' +
        '<a class="btn accent" id="z2aDl" href="#" hidden>⬇ ទាញយក APK</a>' +
        '<button class="btn" id="z2aLogBtn">📄 Build Log</button>' +
      '</div>' +
      '<pre class="z2a-log" id="z2aLog" hidden></pre>',
      { noOk: true });

    var picked = null, uploadId = '', jobId = '', failed = false;

    function step(name, state, note) {
      var el = $('z2a-' + name);
      if (!el) return;
      el.className = 'z2a-step' + (state ? ' ' + state : '');
      var dot = el.querySelector('.z2a-dot');
      if (dot) dot.textContent = state === 'done' ? '✓' : state === 'now' ? '⟳' : state === 'bad' ? '✕' : '•';
      var n = $('z2a-' + name + '-note');
      if (n) n.textContent = note || '';
    }
    function say(text, bad) {
      var el = $('z2aMsg');
      el.hidden = !text;
      el.textContent = text || '';
      el.className = 'z2a-msg' + (bad ? ' bad' : '');
    }
    function showProblems(list) {
      var el = $('z2aProblems');
      if (!list || !list.length) { el.hidden = true; el.innerHTML = ''; return; }
      el.hidden = false;
      el.innerHTML = '⚠️ ' + list.map(function (t) { return esc(t); }).join('<br>⚠️ ');
    }
    function showInfo(i) {
      var el = $('z2aInfo');
      if (!i) { el.hidden = true; return; }
      el.hidden = false;
      el.innerHTML = 'root: <b>' + esc(i.root) + '</b> · file: ' + i.files +
        ' · source: ' + i.sources + ' · manifest: ' + i.manifests + '<br>' +
        'App ID: <b>' + esc(i.app_id) + '</b> · compileSdk: ' + esc(i.compile_sdk) +
        ' · minSdk: ' + esc(i.min_sdk) + ' · gradlew: ' +
        (i.gradlew ? 'មាន' : 'គ្មាន (ប្រើ Gradle 8.7)');
    }
    function showLog(text) {
      var pre = $('z2aLog');
      pre.hidden = false;
      pre.textContent = text;
    }
    function whatWentWrong(log) {
      var m = /\*\s*What went wrong:([\s\S]*?)(?:\n\*\s*Try:|\n\*\s*Get more help|\n\*\s*Exception is:|$)/.exec(log || '');
      if (!m) return '';
      var text = m[1].replace(/\n{2,}/g, '\n').trim();
      return text.length > 600 ? text.slice(0, 600) + '…' : text;
    }
    function loadLog(id, tries) {
      var pre = $('z2aLog');
      pre.hidden = false;
      pre.textContent = 'កំពុងទាញ build log…';
      fetch('/api/jobs/' + id + '/log', { cache: 'no-store' }).then(function (r) {
        return r.json();
      }).then(function (j) {
        if (j && j.ok) {
          showLog(j.log || '(build log ទទេ)');
          var w = whatWentWrong(j.log);
          if (w && failed) say('មូលហេតុ: ' + w, true);
          return;
        }
        if (tries > 0) {
          setTimeout(function () { loadLog(id, tries - 1); }, 5000);
        } else {
          pre.textContent = 'មិនអាចទាញ build log: ' + ((j && j.error) || 'មិនដឹងមូលហេតុ');
        }
      }).catch(function () { pre.textContent = 'មិនអាចទាញ build log បាន (network)'; });
    }
    function poll(id, tries) {
      tries = (tries || 0) + 1;
      fetch('/api/jobs/' + id, { cache: 'no-store' }).then(function (r) {
        return r.json();
      }).then(function (j) {
        var p = j.progress || {};
        if (j.status === 'done') {
          step('build', 'done', '100%');
          step('download', 'done', 'APK រួចរាល់');
          var dl = $('z2aDl');
          dl.href = '/dl/' + id;
          dl.hidden = false;
          $('z2aActions').hidden = false;
          $('z2aBuildRow').hidden = true;
          say('✓ APK បង្កើតរួចរាល់ — ចុច «ទាញយក APK»។ Build Log បង្ហាញការ compile ពិត។');
          loadLog(id, 3);
          return;
        }
        if (j.status === 'error') {
          failed = true;
          step('build', 'bad', j.error || 'បរាជ័យ');
          step('download', 'pending', 'មិនមាន APK');
          $('z2aActions').hidden = false;
          dl_hide();
          say((j.error || 'Build បរាជ័យ') + (j.run_url ? ' — ' + j.run_url : ''), true);
          loadLog(id, 3);
          return;
        }
        var note = (p.percent || 0) + '%' + (p.step ? ' · ' + p.step : (j.note ? ' · ' + j.note : ''));
        step('build', 'now', note);
        if (tries < 600) setTimeout(function () { poll(id, tries); }, 3000);
      }).catch(function () {
        if (tries < 600) setTimeout(function () { poll(id, tries); }, 5000);
      });
    }
    function dl_hide() {
      var dl = $('z2aDl');
      if (dl) dl.hidden = true;
    }

    function inspect(f) {
      say('');
      $('z2aGo').disabled = true;
      step('upload', 'done', esc(f.name) + ' · ' + z2aKB(f.size));
      step('validate', 'now', 'កំពុងពន្លា និងពិនិត្យ Gradle / Manifest / source…');
      var fd = new FormData();
      fd.append('zip', f, f.name);
      fetch('/api/zip/inspect', { method: 'POST', body: fd }).then(function (r) {
        return r.json().then(function (j) { return { code: r.status, j: j || {} }; });
      }).then(function (res) {
        var j = res.j;
        showInfo(j.info);
        if (j.list) z2aFiles(j.list, j.list_total);
        showProblems(j.problems);
        if (j.upload_id) uploadId = j.upload_id;
        if (!j.ok) {
          step('validate', 'bad', j.error || 'ZIP មិនត្រឹមត្រូវ');
          step('upload', 'now', 'សាក ZIP ផ្សេង');
          say(j.error || 'ZIP មិនត្រឹមត្រូវ — មើលបញ្ហាខាងលើ', true);
          $('z2aGo').disabled = false;
          return;
        }
        step('validate', 'done', (j.info ? j.info.files + ' file · ' + j.info.sources + ' source' : 'រួចរាល់'));
        $('z2aPick').hidden = true;
        $('z2aFilesHead').hidden = false;
        $('z2aBuildRow').hidden = false;
        say('ពិនិត្យឯកសារខាងលើរួចហើយ ចុច «🔨 Build APK» ដើម្បី compile ក្នុង cloud។');
      }).catch(function (err) {
        step('validate', 'bad', 'បញ្ជូនមិនបាន');
        say('បញ្ជូន ZIP មិនបាន: ' + (err && err.message ? err.message : err), true);
        $('z2aGo').disabled = false;
      });
    }

    function startBuild() {
      say('');
      $('z2aBuild').disabled = true;
      step('build', 'now', 'បញ្ជូនទៅ GitHub Actions…');
      step('download', 'pending', 'រង់ចាំ APK');
      var send = function (fd) {
        return fetch('/api/zip/build', { method: 'POST', body: fd }).then(function (r) {
          return r.json().then(function (j) { return { code: r.status, j: j || {} }; });
        });
      };
      var body = new FormData();
      body.append('upload_id', uploadId);
      send(body).then(function (res) {
        if (res.code === 410 && picked) {
          // ZIP ក្នុង server ផុតកំណត់ → upload ម្តងទៀតដោយស្វ័យប្រវត្តិ
          var fd = new FormData();
          fd.append('zip', picked, picked.name);
          return send(fd);
        }
        return res;
      }).then(function (res) {
        var j = res.j || {};
        showInfo(j.info);
        if (!j.ok) {
          step('build', 'bad', j.error || 'Cloud build មិនអាចចាប់ផ្តើម');
          say((j.problems && j.problems.length ? j.problems.join(' · ') : j.error) ||
              'Cloud build មិនអាចចាប់ផ្តើម', true);
          $('z2aBuild').disabled = false;
          return;
        }
        jobId = j.id;
        step('build', 'now', 'បញ្ជូនទៅ GitHub Actions រួច…');
        $('z2aActions').hidden = false;
        poll(jobId);
      }).catch(function (err) {
        step('build', 'bad', 'បញ្ជូនមិនបាន');
        say('បញ្ជូន build មិនបាន: ' + (err && err.message ? err.message : err), true);
        $('z2aBuild').disabled = false;
      });
    }

    $('z2aGo').onclick = function () {
      var input = $('z2aFile');
      var f = input.files && input.files[0];
      if (!f) return say('សូមជ្រើស file .zip សិន', true);
      if (!/\.zip$/i.test(f.name)) return say('ត្រូវជា file .zip', true);
      if (f.size > 40 * 1024 * 1024) return say('ZIP ធំពេក (អតិបរមា 40 MB)', true);
      picked = f;
      $('z2aGo').disabled = true;
      inspect(f);
    };
    $('z2aBuild').onclick = startBuild;
    $('z2aLogBtn').onclick = function () {
      var pre = $('z2aLog');
      pre.hidden = !pre.hidden;
      if (!pre.hidden && jobId && !pre.textContent) loadLog(jobId, 2);
    };
  }
  $('zipApkBtn').onclick = zipApk;

  boot();
})();
