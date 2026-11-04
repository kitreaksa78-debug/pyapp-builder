const app = document.getElementById('app');
app.innerHTML = `
  <div style="padding:24px;font-family:system-ui;background:#0f172a;color:#fff;min-height:100vh">
    <h1 style="margin:0 0 8px">JS App ✅</h1>
    <p>ចុច <b id="n">0</b> ដង</p>
    <button id="b" style="padding:12px 18px;border:0;border-radius:10px;background:#34d399;color:#062">ចុចទីនេះ</button>
  </div>`;
let n = 0;
document.getElementById('b').addEventListener('click', () => {
  n += 1;
  document.getElementById('n').textContent = n;
});
