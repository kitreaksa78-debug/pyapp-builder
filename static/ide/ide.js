/* PyApp IDE — browser Android IDE */
(function () {
  'use strict';

  var $ = function (id) { return document.getElementById(id); };
  var CFG = window.IDE_CONFIG || {};
  var TEXT_EXT = ['html','htm','css','js','mjs','json','md','txt','xml','yml','yaml',
    'gradle','kts','properties','kt','java','py','php','rb','go','rs','c','h','cpp',
    'cs','swift','sql','sh','svg','toml','ini'];
  var BINARY_EXT = ['png','jpg','jpeg','webp','gif','ico','bmp','ttf','otf','woff',
    'woff2','mp3','wav','ogg','mp4','webm','pdf','zip'];
  var MONACO_LANG = {html:'html',htm:'html',css:'css',js:'javascript',mjs:'javascript',
    json:'json',md:'markdown',xml:'xml',svg:'xml',kt:'kotlin',kts:'kotlin',java:'java',
    py:'python',php:'php',yml:'yaml',yaml:'yaml',gradle:'groovy'};
  var PERMS = [['INTERNET','Internet'],['ACCESS_NETWORK_STATE','Network state'],
    ['VIBRATE','Vibrate'],['CAMERA','Camera'],['RECORD_AUDIO','Record audio'],
    ['ACCESS_FINE_LOCATION','GPS location'],['ACCESS_COARSE_LOCATION','Coarse location'],
    ['READ_EXTERNAL_STORAGE','Read storage'],['WRITE_EXTERNAL_STORAGE','Write storage'],
    ['POST_NOTIFICATIONS','Notifications'],['WAKE_LOCK','Wake lock'],['FLASHLIGHT','Flashlight']];

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
  };

  var state = {
    name: 'My App', pkg: 'org.pyapp.demo',
    files: {}, folders: [], open: [], active: '',
    settings: {theme: 'dark', lang: 'km', font: 14, wrap: 'on', auto: true},
    icon: '',
    build: {mode: 'webview', ver: '1.0', code: 1, orient: 'unspecified', online: true,
            perms: ['INTERNET']},
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
  function validPath(path) {
    return /^[A-Za-z0-9_\-][A-Za-z0-9_\-.\/]{0,179}$/.test(path) &&
      path.split('/').every(function (p) { return p && p !== '.' && p !== '..'; });
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

  /* ---------------- templates ---------------- */
  function templateChooser() {
    var body = showModal('New project — template',
      '<p class="hint">ជ្រើសគំរូដើម (file បច្ចុប្បន្ននឹងត្រូវជំនួស)</p>' +
      '<div class="tmpl">' + Object.keys(TPL).map(function (k) {
        return '<button data-tpl="' + k + '"><b>' + esc(TPL[k].label) + '</b><small>' +
          esc(TPL[k].desc) + '</small></button>';
      }).join('') + '</div>', { noOk: true });
    body.querySelectorAll('[data-tpl]').forEach(function (b) {
      b.onclick = function () { closeModal(); newProject(b.getAttribute('data-tpl')); };
    });
  }

  function newProject(key) {
    var tpl = TPL[key] || TPL.webview;
    state.files = tpl.files();
    state.folders = [];
    state.open = [];
    state.active = '';
    state.name = key === 'webview' ? 'My App' : tpl.label;
    state.build.mode = key === 'kotlin' ? 'kotlin' : (key === 'java' ? 'java' : 'webview');
    if (key === 'kotlin' || key === 'java') {
      var src = key === 'kotlin' ? 'MainActivity.kt' : 'MainActivity.java';
      state.files[src] = state.files[src].replace('org.pyapp.demo', state.pkg);
    }
    if (monacoReady) {
      Object.keys(models).forEach(function (k) { try { models[k].dispose(); } catch (e) {} });
    }
    models = {};
    persist(true);
    refreshAll();
    openFile(key === 'kotlin' ? 'MainActivity.kt' : (key === 'java' ? 'MainActivity.java' : 'index.html'));
    renderPreview();
  }

  /* ---------------- tree ---------------- */
  var collapsed = {};
  function treeModel() {
    var root = { dirs: {}, files: [] };
    state.folders.forEach(function (f) {
      var node = root, parts = f.split('/');
      parts.forEach(function (p) {
        node.dirs[p] = node.dirs[p] || { dirs: {}, files: [] };
        node = node.dirs[p];
      });
    });
    Object.keys(state.files).forEach(function (path) {
      var node = root, parts = path.split('/');
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
  function renderTree() {
    var host = $('tree');
    host.innerHTML = '';
    var root = treeModel();
    var wrap = document.createElement('div');
    function walk(node, base, depth) {
      Object.keys(node.dirs).sort().forEach(function (name) {
        var path = base ? base + '/' + name : name;
        var row = document.createElement('div');
        row.className = 'row indent-' + Math.min(depth + 1, 6);
        row.innerHTML = '<span class="ic">' + (collapsed[path] ? '▸' : '▾') + '</span>' +
          '<span class="ic">📁</span><span class="nm">' + esc(name) +
          '</span><button class="more" title="menu">⋮</button>';
        row.onclick = function (e) {
          if (e.target.classList.contains('more')) {
            e.stopPropagation(); folderMenu(path); return;
          }
          collapsed[path] = !collapsed[path];
          renderTree();
        };
        row.oncontextmenu = function (e) { e.preventDefault(); folderMenu(path); };
        wrap.appendChild(row);
        if (!collapsed[path]) walk(node.dirs[name], path, depth + 1);
      });
      node.files.sort(function (a, b) { return a.name.localeCompare(b.name); })
        .forEach(function (f) {
          var row = document.createElement('div');
          row.className = 'row indent-' + Math.min(depth + 1, 6) +
            (state.active === f.path ? ' active' : '');
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
      '<button class="btn ghost" data-act="rename">✏️ ' + t('rename') + '</button>' +
      '<button class="btn ghost" data-act="delete">🗑 ' + t('del') + '</button></div>',
      { noOk: true });
    body.querySelectorAll('[data-act]').forEach(function (b) {
      b.onclick = function () {
        var act = b.getAttribute('data-act');
        closeModal();
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
        var idx = state.folders.indexOf(path);
        if (idx >= 0) state.folders.splice(idx, 1, next);
        Object.keys(state.files).forEach(function (p) {
          if (p === path || p.indexOf(path + '/') === 0) {
            state.files[next + p.slice(path.length)] = state.files[p];
            delete state.files[p];
          }
        });
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
  $('newFile').onclick = function () {
    ask(t('newFile'), 'src/new-file.js', function (path) {
      if (!path) return;
      if (!validPath(path)) return toast(t('invalidName'), true);
      if (state.files[path] != null) return toast('File មានរួចហើយ', true);
      state.files[path] = '';
      var parts = path.split('/');
      for (var i = 1; i < parts.length; i++) {
        var f = parts.slice(0, i).join('/');
        if (state.folders.indexOf(f) < 0) state.folders.push(f);
      }
      persist(true); refreshAll(); openFile(path);
    });
  };
  $('newFolder').onclick = function () {
    ask(t('newFolder'), 'assets', function (path) {
      if (!path) return;
      if (!validPath(path)) return toast(t('invalidName'), true);
      if (state.folders.indexOf(path) < 0) state.folders.push(path);
      persist(true); renderTree();
    });
  };
  $('uploadBtn').onclick = function () { $('uploadInput').click(); };
  $('uploadInput').addEventListener('change', function () {
    var files = Array.prototype.slice.call(this.files || []);
    this.value = '';
    files.forEach(function (file) {
      var reader = new FileReader();
      var binary = !isText(file.name);
      reader.onload = function () {
        state.files[file.name] = reader.result;
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
        Object.keys(res.j.files).forEach(function (p) { state.files[p] = res.j.files[p]; });
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
  ['bTitle','bPkg','bVer','bCode','bMode','bOrient','bOnline'].forEach(function (id) {
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
    $('buildLog').textContent = ''; $('buildLog').hidden = true;
    $('buildSteps').innerHTML = '';
    setProgress(1);
    setBuildState(t('running'));
    var payload = {
      title: $('bTitle').value.trim() || state.name,
      package: $('bPkg').value.trim() || state.pkg,
      version: $('bVer').value.trim() || '1.0',
      build: parseInt($('bCode').value, 10) || 1,
      mode: $('bMode').value,
      orientation: $('bOrient').value,
      online: $('bOnline').value === '1',
      permissions: state.build.perms,
      icon: state.icon || '',
      files: currentFiles()
    };
    if (!payload.title || !/^[a-z][a-z0-9_]{1,30}(\.[a-z][a-z0-9_]{1,30}){1,3}$/.test(payload.package)) {
      setBuildState('package មិនត្រឹមត្រូវ — ឧ. org.pyapp.demo', 'bad');
      setProgress(0, 'bad');
      return;
    }
    $('buildBtn').disabled = true;
    appendLog('POST /api/ide/build · mode=' + payload.mode + ' · files=' +
      Object.keys(payload.files).length);
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
          dl.href = '/dl/' + j.id;
          dl.hidden = false;
          if (j.run_url) { $('runLink').href = j.run_url; $('runLink').hidden = false; }
          $('logsBtn').hidden = false;
          $('logsBtn').onclick = function () { showLogs(j.id); };
          appendLog('APK រួចរាល់ — ចុច Download APK');
          markHistory(j.id, 'done');
          toast('APK រួចរាល់ 🎉');
        } else {
          setProgress(0, 'bad');
          setBuildState(j.error || t('failed'), 'bad');
          appendLog('ERROR: ' + (j.error || 'build failed'));
          $('logsBtn').hidden = false;
          $('logsBtn').onclick = function () { showLogs(j.id); };
          if (j.run_url) { $('runLink').href = j.run_url; $('runLink').hidden = false; }
          markHistory(j.id, 'error');
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
      }).catch(function (e) { log.textContent = 'អាន log មិនបាន: ' + e.message; });
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
      onOk: function () { closeModal(); newProject('webview'); }
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
    var web = state.build.mode === 'webview';
    var btn = $('pvToggle');
    btn.disabled = !web;
    btn.title = web ? t('preview') + ' (Ctrl+J)' : t('nativeNoPreview');
    if (!web) $('preview').classList.add('hidden');
  }
  window.syncPreviewMode = syncPreviewMode;

  $('pvToggle').onclick = function () {
    if (state.build.mode !== 'webview') { toast(t('nativeNoPreview')); return; }
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
  $('newProjBtn').onclick = templateChooser;

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
        orient: 'unspecified', online: true, perms: ['INTERNET']}, saved.build || {});
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

  boot();
})();
