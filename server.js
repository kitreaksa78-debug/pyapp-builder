'use strict';
/* City Drive live-view server: serves the game and a small token-protected API
 * so an operator can see, screenshot and nudge a running session.
 *
 *   GET  /                     the game
 *   POST /api/hb               game -> server heartbeat (no token, same page)
 *   GET  /api/cmd?id=...       game -> server: pull queued commands
 *   POST /api/shot             game -> server: upload a JPEG snapshot
 *
 *   GET  /api/live             operator: who is playing, position, fps
 *   POST /api/cmd              operator: queue a command for the session(s)
 *   GET  /api/shot             operator: latest snapshot (image/jpeg)
 *
 * Operator endpoints need the token: header `x-city-token` or `?token=`.
 * The token lives in .live-token (mode 600) or CITY_TOKEN in the environment.
 */

const http = require('http');
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const { URL } = require('url');

const ROOT = __dirname;
const PORT = Number(process.env.PORT || 3000);
const TOKEN_FILE = path.join(ROOT, '.live-token');

let TOKEN = (process.env.CITY_TOKEN || '').trim();
if (!TOKEN) {
  try { TOKEN = fs.readFileSync(TOKEN_FILE, 'utf8').trim(); } catch (_) { /* first run */ }
}
if (!TOKEN) {
  TOKEN = crypto.randomBytes(18).toString('hex');
  fs.writeFileSync(TOKEN_FILE, TOKEN + '\n', { mode: 0o600 });
}

const sessions = new Map();   // id -> state
const queues = new Map();     // id -> [cmd]
let shot = null;              // { data: Buffer, at, id }

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.svg': 'image/svg+xml',
  '.ico': 'image/x-icon',
  '.mp3': 'audio/mpeg',
  '.m4a': 'audio/mp4',
  '.ogg': 'audio/ogg',
  '.wav': 'audio/wav'
};

function json(res, code, obj) {
  const body = JSON.stringify(obj);
  res.writeHead(code, {
    'content-type': 'application/json; charset=utf-8',
    'cache-control': 'no-store',
    'content-length': Buffer.byteLength(body)
  });
  res.end(body);
}

function readBody(req, limit) {
  return new Promise((resolve, reject) => {
    let size = 0;
    const chunks = [];
    req.on('data', (c) => {
      size += c.length;
      if (size > limit) { reject(new Error('body too large')); req.destroy(); return; }
      chunks.push(c);
    });
    req.on('end', () => resolve(Buffer.concat(chunks).toString('utf8')));
    req.on('error', reject);
  });
}

function authed(req, url) {
  const h = req.headers['x-city-token'];
  const q = url.searchParams.get('token');
  const given = Buffer.from(String(h || q || ''));
  const want = Buffer.from(TOKEN);
  return given.length === want.length && crypto.timingSafeEqual(given, want);
}

function queueFor(id, cmd) {
  const q = queues.get(id) || [];
  q.push(cmd);
  while (q.length > 8) q.shift();
  queues.set(id, q);
}

function serveFile(res, file) {
  fs.readFile(file, (err, buf) => {
    if (err) { json(res, 404, { ok: false, error: 'not found' }); return; }
    res.writeHead(200, {
      'content-type': MIME[path.extname(file).toLowerCase()] || 'application/octet-stream',
      'cache-control': 'no-cache',
      'content-length': buf.length
    });
    res.end(buf);
  });
}

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, 'http://localhost');
  const p = url.pathname;

  try {
    /* ---------- game side ---------- */

    if (p === '/api/hb' && req.method === 'POST') {
      const body = JSON.parse((await readBody(req, 8 * 1024)) || '{}');
      const id = String(body.id || '').slice(0, 40);
      if (!id) { json(res, 400, { ok: false, error: 'id required' }); return; }
      sessions.set(id, {
        id,
        at: Date.now(),
        running: !!body.running,
        x: Number(body.x) || 0,
        z: Number(body.z) || 0,
        speed: Number(body.speed) || 0,
        heading: Number(body.heading) || 0,
        fps: Number(body.fps) || 0,
        hd: !!body.hd,
        night: Number(body.night) || 0,
        size: Array.isArray(body.size) ? body.size.slice(0, 2) : null,
        paint: typeof body.paint === 'string' ? body.paint.slice(0, 9) : null,
        mode: body.mode === 'walk' ? 'walk' : 'car',
        engineOn: !!body.engineOn,
        radio: typeof body.radio === 'string' ? body.radio.slice(0, 120) : null
      });
      json(res, 200, { ok: true });
      return;
    }

    if (p === '/api/cmd' && req.method === 'GET') {
      const id = String(url.searchParams.get('id') || '').slice(0, 40);
      const q = queues.get(id) || [];
      queues.set(id, []);
      json(res, 200, { ok: true, cmds: q });
      return;
    }

    if (p === '/api/shot' && req.method === 'POST') {
      const body = JSON.parse((await readBody(req, 6 * 1024 * 1024)) || '{}');
      const data = String(body.data || '');
      const m = /^data:image\/(jpeg|png);base64,(.+)$/.exec(data);
      if (!m) { json(res, 400, { ok: false, error: 'bad image' }); return; }
      shot = { data: Buffer.from(m[2], 'base64'), type: m[1] === 'png' ? 'image/png' : 'image/jpeg', at: Date.now(), id: String(body.id || '') };
      json(res, 200, { ok: true, bytes: shot.data.length });
      return;
    }

    /* ---------- operator side (token) ---------- */

    if (p === '/api/live') {
      if (!authed(req, url)) { json(res, 401, { ok: false, error: 'unauthorized' }); return; }
      const now = Date.now();
      const list = [...sessions.values()]
        .map((s) => ({ ...s, online: now - s.at < 6000, ageMs: now - s.at }))
        .sort((a, b) => b.at - a.at);
      json(res, 200, {
        ok: true,
        sessions: list,
        shot: shot ? { at: shot.at, bytes: shot.data.length, id: shot.id } : null
      });
      return;
    }

    if (p === '/api/cmd' && req.method === 'POST') {
      if (!authed(req, url)) { json(res, 401, { ok: false, error: 'unauthorized' }); return; }
      const body = JSON.parse((await readBody(req, 4 * 1024)) || '{}');
      const cmd = body.cmd;
      if (!cmd || typeof cmd !== 'object') { json(res, 400, { ok: false, error: 'cmd object required' }); return; }
      const target = body.id ? [String(body.id)] : [...sessions.keys()];
      if (!target.length) { json(res, 200, { ok: true, sent: 0, note: 'no session is playing yet' }); return; }
      for (const id of target) queueFor(id, cmd);
      json(res, 200, { ok: true, sent: target.length, targets: target });
      return;
    }

    if (p === '/api/shot' && req.method === 'GET') {
      if (!authed(req, url)) { json(res, 401, { ok: false, error: 'unauthorized' }); return; }
      if (!shot) { json(res, 404, { ok: false, error: 'no shot yet' }); return; }
      res.writeHead(200, { 'content-type': shot.type, 'cache-control': 'no-store', 'content-length': shot.data.length });
      res.end(shot.data);
      return;
    }

    /* ---------- static ---------- */

    const rel = p === '/' ? 'index.html' : decodeURIComponent(p).replace(/^\/+/, '');
    const file = path.join(ROOT, rel);
    if (!file.startsWith(ROOT) || rel.startsWith('.')) { json(res, 404, { ok: false, error: 'not found' }); return; }
    serveFile(res, file);
  } catch (err) {
    json(res, 400, { ok: false, error: String((err && err.message) || err) });
  }
});

server.listen(PORT, '0.0.0.0', () => {
  console.log('city drive live server on http://0.0.0.0:' + PORT);
});
