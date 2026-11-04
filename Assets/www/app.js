// app.js — UI របស់ app សរសេរដោយ JavaScript
// បង្ហាញក្នុង <div id="app"> — ដំណើរការក្នុង WebView ដូច browser ធម្មតា។

const app = document.getElementById('app');

app.innerHTML = `
  <header class="hero">
    <span class="badge">JS</span>
    <h1>សួស្តី 👋</h1>
    <p>app នេះបង្កើតដោយ JavaScript</p>
  </header>

  <main class="body">
    <section class="card">
      <h2>សាកល្បងចុច</h2>
      <p>អ្នកបានចុច <b id="count">0</b> ដង</p>
      <button id="tap" class="btn">ចុចទីនេះ</button>
    </section>

    <section class="card">
      <h2>បញ្ជីការងារ</h2>
      <ul id="list" class="list">
        <li>សរសេរ UI ដោយ JS</li>
        <li>មើលផ្ទាំង preview</li>
        <li>ចុច Build APK</li>
      </ul>
    </section>
  </main>

  <nav class="tabs">
    <span class="on">ដើម</span><span>ស្វែងរក</span><span>ខ្ញុំ</span>
  </nav>
`;

let count = 0;
const counter = document.getElementById('count');

document.getElementById('tap').addEventListener('click', () => {
  count += 1;
  counter.textContent = count;
  document.getElementById('list').insertAdjacentHTML(
    'beforeend',
    '<li>ចុចលើកទី ' + count + '</li>'
  );
});
