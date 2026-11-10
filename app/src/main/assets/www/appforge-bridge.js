/* ==========================================================================
 * AppForge bridge — JS ↔ Kotlin (សម្រាប់ WebView app)
 *
 * ក្នុង app ពិត (APK):  window.Native  ហៅ Kotlin តាម AppForge bridge
 * ក្នុង browser preview: window.Native ប្រើ fallback (console / vibrate / clipboard)
 *
 *   Native.toast('សួស្តី')
 *   Native.vibrate(60)
 *   Native.copy('text')
 *   Native.share('text', 'ចែករំលែក')
 *   Native.open('https://example.com')
 *   Native.notify('ចំណងជើង', 'សារ')
 *   Native.info()        // {brand, model, sdk, android, package, locale}
 *   Native.net()         // {online, wifi, mobile}
 *   Native.battery()     // {level, charging}
 *   const url = await Native.pickImage()      // data URL ឬ null
 *   const ok  = await Native.permission('android.permission.CAMERA')
 * ========================================================================== */
(function () {
  'use strict';

  var A = window.AppForge || null;
  var has = !!(A && typeof A.version === 'function');
  var waiters = {};
  var seq = 0;

  function call(name, args) {
    if (!has) return null;
    try { return A[name].apply(A, args); } catch (e) { return null; }
  }
  function read(name) {
    if (!has) return null;
    try { return JSON.parse(A[name]()); } catch (e) { return null; }
  }
  function nextId() { seq += 1; return 'w' + seq; }
  function fallbackToast(msg) {
    if (typeof console !== 'undefined') console.log('[Native.toast] ' + msg);
  }

  var Native = {
    available: has,
    version: has ? Number(A.version()) : 0,

    toast: function (msg) {
      msg = String(msg == null ? '' : msg);
      if (has) call('toast', [msg]); else fallbackToast(msg);
    },

    vibrate: function (ms) {
      ms = Number(ms) || 40;
      if (has) call('vibrate', [ms | 0]);
      else if (navigator.vibrate) navigator.vibrate(ms);
    },

    copy: function (text) {
      text = String(text == null ? '' : text);
      if (has) { call('copy', [text]); return Promise.resolve(true); }
      if (navigator.clipboard && navigator.clipboard.writeText) {
        return navigator.clipboard.writeText(text).then(function () { return true; },
          function () { return false; });
      }
      return Promise.resolve(false);
    },

    share: function (text, title) {
      text = String(text == null ? '' : text);
      title = String(title == null ? '' : title);
      if (has) { call('share', [text, title]); return Promise.resolve(true); }
      if (navigator.share) {
        return navigator.share({ text: text, title: title }).then(function () { return true; },
          function () { return false; });
      }
      fallbackToast('share: ' + text);
      return Promise.resolve(false);
    },

    open: function (url) {
      url = String(url == null ? '' : url);
      if (has) { call('openUrl', [url]); return; }
      try { window.open(url, '_blank'); } catch (e) {}
    },

    notify: function (title, body) {
      title = String(title == null ? '' : title);
      body = String(body == null ? '' : body);
      if (has) { call('notify', [title, body]); return; }
      if (typeof Notification !== 'undefined' && Notification.permission === 'granted') {
        try { new Notification(title, { body: body }); } catch (e) {}
      } else {
        fallbackToast(title + ' — ' + body);
      }
    },

    info: function () {
      return read('info') || {
        brand: '', model: '', device: '', sdk: 0, android: '',
        package: '', locale: (navigator.language || ''), bridge: 0
      };
    },

    net: function () {
      var r = read('net');
      if (r) return r;
      return { online: !!navigator.onLine, wifi: false, mobile: false };
    },

    battery: function () { return read('battery') || { level: -1, charging: false }; },

    granted: function (name) {
      if (!has) return false;
      try { return !!A.granted(String(name)); } catch (e) { return false; }
    },

    permission: function (name) {
      name = String(name);
      if (has && Native.granted(name)) return Promise.resolve(true);
      if (!has) return Promise.resolve(false);
      return new Promise(function (resolve) {
        var id = nextId();
        waiters[id] = resolve;
        call('permission', [name, id]);
      });
    },

    pickImage: function () {
      if (has) {
        return new Promise(function (resolve) {
          waiters.pick = resolve;
          call('pickImage', []);
        });
      }
      return new Promise(function (resolve) {
        var inp = document.createElement('input');
        inp.type = 'file';
        inp.accept = 'image/*';
        inp.style.display = 'none';
        inp.onchange = function () {
          var file = inp.files && inp.files[0];
          if (!file) { resolve(null); return; }
          var fr = new FileReader();
          fr.onload = function () { resolve(String(fr.result)); };
          fr.onerror = function () { resolve(null); };
          fr.readAsDataURL(file);
        };
        document.body.appendChild(inp);
        inp.click();
        setTimeout(function () {
          if (inp.parentNode) inp.parentNode.removeChild(inp);
        }, 60000);
      });
    }
  };

  /* Kotlin ហៅមកតាម evaluateJavascript('window.__AppForgeNative._xxx(...)') */
  window.__AppForgeNative = {
    _pickImage: function (dataUrl) {
      var f = waiters.pick;
      waiters.pick = null;
      if (f) f(dataUrl || null);
    },
    _permission: function (id, ok) {
      var f = waiters[id];
      if (f) { delete waiters[id]; f(!!ok); }
    }
  };

  window.Native = Native;
  window.AppForgeJS = Native;
})();
