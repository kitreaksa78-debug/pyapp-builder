
const app = document.getElementById('app');

app.innerHTML = `
  <header class="top">
    <div class="brand">
      <div class="logo">🛡️</div>
      <div>
        <h2>SecureGuard</h2>
        <small>Mobile Security Center</small>
      </div>
    </div>
    <button class="theme" id="theme">☾</button>
  </header>

  <main class="content">
    <p class="welcome">សួស្តី! 👋</p>
    <h1>Security Dashboard</h1>
    <p class="muted">គ្រប់គ្រងសុវត្ថិភាពឧបករណ៍របស់អ្នក</p>

    <section class="status">
      <div class="shield">🛡️</div>
      <h2 id="status">Your Device is Protected</h2>
      <p>ប្រព័ន្ធត្រួតពិនិត្យរួចរាល់</p>
      <span class="online">● SYSTEM READY</span>
    </section>

    <h3>Quick Actions</h3>
    <div class="grid">
      <button class="action" id="scan">
        <span>🔍</span>
        <b>Security Scan</b>
        <small>ពិនិត្យសុវត្ថិភាព</small>
      </button>

      <button class="action" id="device">
        <span>📱</span>
        <b>My Device</b>
        <small>ព័ត៌មានឧបករណ៍</small>
      </button>

      <button class="action" id="network">
        <span>🌐</span>
        <b>Network</b>
        <small>ស្ថានភាពបណ្ដាញ</small>
      </button>

      <button class="action" id="privacy">
        <span>🔐</span>
        <b>Privacy</b>
        <small>ការការពារឯកជនភាព</small>
      </button>
    </div>

    <section class="card">
      <h3>Security Activity</h3>
      <div id="activity">
        <p>🟢 App initialized successfully</p>
        <p>🟢 Security dashboard ready</p>
      </div>
    </section>

    <section class="card">
      <h3>System Information</h3>
      <p>Application <b>SecureGuard</b></p>
      <p>Version <b>1.0.0</b></p>
      <p>Protection <b class="green">Enabled</b></p>
    </section>
  </main>

  <nav class="bottom">
    <span class="selected">🏠<small>Home</small></span>
    <span id="navscan">🔍<small>Scan</small></span>
    <span id="navdevice">📱<small>Device</small></span>
    <span id="navsettings">⚙️<small>Settings</small></span>
  </nav>
`;

const activity = document.getElementById('activity');

function log(message) {
  const p = document.createElement('p');
  p.textContent = '🟢 ' + message;
  activity.prepend(p);
}

document.getElementById('scan').onclick = () => {
  document.getElementById('status').textContent =
    'Security Check Complete';
  log('Security check completed');
  alert('ការត្រួតពិនិត្យបានបញ្ចប់!');
};

document.getElementById('device').onclick = () => {
  log('Device information opened');
  alert('នេះជាផ្នែកព័ត៌មានឧបករណ៍');
};

document.getElementById('network').onclick = () => {
  log('Network status checked');
  alert('Network dashboard is ready');
};

document.getElementById('privacy').onclick = () => {
  log('Privacy settings opened');
  alert('Privacy protection settings');
};

document.getElementById('theme').onclick = () => {
  document.body.classList.toggle('dark');
};

document.getElementById('navscan').onclick =
  () => document.getElementById('scan').click();

document.getElementById('navdevice').onclick =
  () => document.getElementById('device').click();

document.getElementById('navsettings').onclick =
  () => document.getElementById('theme').click();
