const app = document.getElementById('app');
app.innerHTML = '<div style="padding:24px;font-family:system-ui;background:#0f172a;color:#fff;min-height:100vh"><h1>Kotlin APK ✅</h1><p id="n">0</p><button id="b" style="padding:14px 20px;border:0;border-radius:12px;background:#22c55e;color:#052e16;font-weight:700;font-size:16px">ចុច</button></div>';
let n=0;
document.getElementById('b').addEventListener('click', () => { n+=1; document.getElementById('n').textContent = 'ចុច ' + n; });
