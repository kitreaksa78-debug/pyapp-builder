/* global THREE */

/* អង្គរវត្ត 3D — សមរភូមិព្រះអាទិត្យលិច
   Stylised first-person shooter in an Angkor Wat at golden hour.
   All art is generated in code. */

const $ = (s) => document.querySelector(s);
const rand = (a, b) => a + Math.random() * (b - a);
const clamp = (v, a, b) => Math.min(b, Math.max(a, v));

function srgbTex(t) {
  if ('colorSpace' in t) t.colorSpace = THREE.SRGBColorSpace;
  else t.encoding = THREE.sRGBEncoding;
  return t;
}

/* ------------------------------------------------------------ renderer */
const host = $('#app');
const renderer = new THREE.WebGLRenderer({ antialias: true, powerPreference: 'high-performance' });
renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
renderer.setSize(window.innerWidth, window.innerHeight);
renderer.shadowMap.enabled = true;
renderer.shadowMap.type = THREE.PCFSoftShadowMap;
renderer.toneMapping = THREE.ACESFilmicToneMapping;
renderer.toneMappingExposure = 0.72;
if ('outputColorSpace' in renderer) renderer.outputColorSpace = THREE.SRGBColorSpace;
else renderer.outputEncoding = THREE.sRGBEncoding;
host.appendChild(renderer.domElement);

const scene = new THREE.Scene();
scene.fog = new THREE.FogExp2(0xcfa070, 0.0019);

const camera = new THREE.PerspectiveCamera(74, window.innerWidth / window.innerHeight, 0.06, 1600);
camera.rotation.order = 'YXZ';
scene.add(camera);

const MAX_ANISO = renderer.capabilities.getMaxAnisotropy();

/* ------------------------------------------------------------ textures */
function makeTex(size, painter, repeat = [1, 1], srgb = true) {
  const c = document.createElement('canvas');
  c.width = c.height = size;
  painter(c.getContext('2d'), size);
  const t = new THREE.CanvasTexture(c);
  t.wrapS = t.wrapT = THREE.RepeatWrapping;
  t.repeat.set(repeat[0], repeat[1]);
  t.anisotropy = MAX_ANISO;
  if (srgb) srgbTex(t);
  return t;
}

function paintStone(g, s, base = '#bda883', dark = '#8b7452') {
  g.fillStyle = base;
  g.fillRect(0, 0, s, s);
  for (let i = 0; i < 3000; i++) {
    const x = Math.random() * s, y = Math.random() * s, r = Math.random() * 2.6;
    g.fillStyle = Math.random() > 0.5
      ? 'rgba(255,243,214,' + rand(0.02, 0.1).toFixed(3) + ')'
      : 'rgba(72,52,30,' + rand(0.03, 0.15).toFixed(3) + ')';
    g.beginPath(); g.arc(x, y, r, 0, 6.2832); g.fill();
  }
  const rows = 6, h = s / rows;
  g.strokeStyle = dark;
  g.globalAlpha = 0.3;
  g.lineWidth = Math.max(1, s / 240);
  for (let r = 0; r < rows; r++) {
    const y = r * h;
    g.beginPath(); g.moveTo(0, y); g.lineTo(s, y); g.stroke();
    const off = r % 2 ? s / 10 : 0;
    for (let x = off; x <= s; x += s / 5) {
      g.beginPath(); g.moveTo(x, y); g.lineTo(x, y + h); g.stroke();
    }
  }
  g.globalAlpha = 1;
}

function paintRelief(g, s) {
  paintStone(g, s, '#a58d68', '#786040');
  g.strokeStyle = 'rgba(246,226,186,0.32)';
  g.lineWidth = s / 110;
  for (let i = 0; i < 6; i++) {
    const x = s * (0.1 + i * 0.16), y = s * 0.52, h = s * 0.1;
    g.beginPath(); g.arc(x, y - s * 0.17, h * 0.42, 0, 6.2832); g.stroke();
    g.beginPath(); g.arc(x, y - s * 0.17, h * 0.88, 0, 6.2832); g.stroke();
    g.beginPath(); g.moveTo(x, y - s * 0.15); g.lineTo(x, y + s * 0.05); g.stroke();
    g.beginPath(); g.moveTo(x - h * 0.6, y - s * 0.08); g.lineTo(x, y - s * 0.13); g.lineTo(x + h * 0.6, y - s * 0.08); g.stroke();
    g.beginPath(); g.moveTo(x - h * 0.5, y + s * 0.05); g.lineTo(x, y + s * 0.19); g.lineTo(x + h * 0.5, y + s * 0.05); g.stroke();
  }
}

function paintGrass(g, s) {
  g.fillStyle = '#4d5f2e';
  g.fillRect(0, 0, s, s);
  for (let i = 0; i < 5000; i++) {
    const x = Math.random() * s, y = Math.random() * s, v = Math.random();
    g.strokeStyle = v > 0.66 ? 'rgba(156,164,80,.5)'
      : v > 0.33 ? 'rgba(92,108,46,.55)' : 'rgba(48,60,26,.6)';
    g.lineWidth = rand(0.7, 1.8);
    g.beginPath(); g.moveTo(x, y); g.lineTo(x + rand(-3, 3), y - rand(3, 9)); g.stroke();
  }
}

function paintWaterNormal(g, s) {
  g.fillStyle = '#8080ff';
  g.fillRect(0, 0, s, s);
  for (let i = 0; i < 220; i++) {
    const x = Math.random() * s, y = Math.random() * s, r = rand(s * 0.03, s * 0.11);
    const grd = g.createRadialGradient(x, y, 0, x, y, r);
    grd.addColorStop(0, 'rgba(158,178,255,.62)');
    grd.addColorStop(0.5, 'rgba(112,124,255,.26)');
    grd.addColorStop(1, 'rgba(128,128,255,0)');
    g.fillStyle = grd;
    g.beginPath(); g.arc(x, y, r, 0, 6.2832); g.fill();
  }
}

function paintGlow(g, s) {
  const grd = g.createRadialGradient(s / 2, s / 2, 0, s / 2, s / 2, s / 2);
  grd.addColorStop(0, 'rgba(255,248,224,1)');
  grd.addColorStop(0.22, 'rgba(255,214,148,.72)');
  grd.addColorStop(0.55, 'rgba(255,164,74,.2)');
  grd.addColorStop(1, 'rgba(255,140,60,0)');
  g.fillStyle = grd;
  g.fillRect(0, 0, s, s);
}

function paintSpark(g, s) {
  const grd = g.createRadialGradient(s / 2, s / 2, 0, s / 2, s / 2, s / 2);
  grd.addColorStop(0, 'rgba(255,255,255,1)');
  grd.addColorStop(0.4, 'rgba(255,205,130,.7)');
  grd.addColorStop(1, 'rgba(255,150,60,0)');
  g.fillStyle = grd;
  g.fillRect(0, 0, s, s);
}

function paintHole(g, s) {
  const grd = g.createRadialGradient(s / 2, s / 2, 0, s / 2, s / 2, s / 2);
  grd.addColorStop(0, 'rgba(18,12,8,.95)');
  grd.addColorStop(0.45, 'rgba(40,28,18,.75)');
  grd.addColorStop(0.72, 'rgba(120,95,64,.35)');
  grd.addColorStop(1, 'rgba(120,95,64,0)');
  g.fillStyle = grd;
  g.fillRect(0, 0, s, s);
}

const stoneMap = makeTex(512, (g, s) => paintStone(g, s));
const reliefMap = makeTex(512, paintRelief);
const grassMap = makeTex(512, paintGrass, [46, 46]);
const waterNormalMap = makeTex(256, paintWaterNormal, [6, 6], false);
const glowTex = makeTex(128, paintGlow);
const sparkTex = makeTex(64, paintSpark);
const holeTex = makeTex(64, paintHole);

/* ------------------------------------------------------------ materials */
const matStone = new THREE.MeshStandardMaterial({ map: stoneMap, color: 0xd8c6a6, roughness: 0.93, metalness: 0.02 });
const matStoneDark = new THREE.MeshStandardMaterial({ map: stoneMap, color: 0x9d927e, roughness: 0.96 });
const matStoneWarm = new THREE.MeshStandardMaterial({ map: stoneMap, color: 0xdcc49c, roughness: 0.9 });
const matRelief = new THREE.MeshStandardMaterial({ map: reliefMap, roughness: 0.95 });
const matGold = new THREE.MeshStandardMaterial({ color: 0xd8a94b, metalness: 0.85, roughness: 0.32, emissive: 0x3a2205 });
const matWood = new THREE.MeshStandardMaterial({ color: 0x4a3524, roughness: 0.85 });
const matWater = new THREE.MeshStandardMaterial({
  color: 0x2b4644, roughness: 0.1, metalness: 0.55,
  normalMap: waterNormalMap, normalScale: new THREE.Vector2(0.5, 0.5),
  transparent: true, opacity: 0.94
});
const matGrass = new THREE.MeshStandardMaterial({ map: grassMap, roughness: 1 });
const matLeaf = new THREE.MeshStandardMaterial({ color: 0x38521f, roughness: 0.95, flatShading: true });
const matLeaf2 = new THREE.MeshStandardMaterial({ color: 0x2b4218, roughness: 0.95, flatShading: true });
const matBark = new THREE.MeshStandardMaterial({ color: 0x3a2a1c, roughness: 1 });

/* ------------------------------------------------------------ sky & light */
const sky = new THREE.Mesh(
  new THREE.SphereGeometry(900, 40, 20),
  new THREE.ShaderMaterial({
    side: THREE.BackSide, depthWrite: false, fog: false,
    uniforms: {
      top: { value: new THREE.Color(0x24527e) },
      mid: { value: new THREE.Color(0xe9a765) },
      bot: { value: new THREE.Color(0xf7d9a8) }
    },
    vertexShader: 'varying vec3 vP; void main(){ vP = position; gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0); }',
    fragmentShader:
      'uniform vec3 top; uniform vec3 mid; uniform vec3 bot; varying vec3 vP;\n' +
      'void main(){ float h = normalize(vP).y;\n' +
      '  vec3 c = mix(bot, mid, smoothstep(-0.06, 0.26, h));\n' +
      '  c = mix(c, top, smoothstep(0.2, 0.78, h));\n' +
      '  gl_FragColor = vec4(c, 1.0); }'
  })
);
scene.add(sky);

const sunPos = new THREE.Vector3(-250, 120, 210);
const sunSprite = new THREE.Sprite(new THREE.SpriteMaterial({
  map: glowTex, color: 0xffd6a0, transparent: true, depthWrite: false,
  blending: THREE.AdditiveBlending, fog: false, opacity: 0.55
}));
sunSprite.position.copy(sunPos.clone().normalize().multiplyScalar(620));
sunSprite.scale.setScalar(190);
scene.add(sunSprite);

const hemi = new THREE.HemisphereLight(0xffe3c2, 0x33240f, 0.42);
scene.add(hemi);

const sun = new THREE.DirectionalLight(0xffb469, 2.9);
sun.position.copy(sunPos.clone().normalize().multiplyScalar(180));
sun.castShadow = true;
sun.shadow.mapSize.set(2048, 2048);
sun.shadow.camera.near = 20;
sun.shadow.camera.far = 520;
sun.shadow.camera.left = -170;
sun.shadow.camera.right = 170;
sun.shadow.camera.top = 170;
sun.shadow.camera.bottom = -170;
sun.shadow.bias = -0.0009;
sun.shadow.normalBias = 0.04;
scene.add(sun);
scene.add(sun.target);

const fill = new THREE.DirectionalLight(0xffc08a, 0.7);
fill.position.set(-60, 55, 220);
scene.add(fill);

const rim = new THREE.DirectionalLight(0x86a8dc, 0.55);
rim.position.set(150, 90, -150);
scene.add(rim);

/* ------------------------------------------------------------ physics */
const ISLAND = 72;
const WATER_Y = -1.35;
const TERRAIN_Y = -1.62;
const CAUSEWAY_Y = 0.45;

const solids = [];
const colliders = [];

function boxGeo(w, h, d, uvScale = 2) {
  const g = new THREE.BoxGeometry(w, h, d);
  const a = g.attributes.uv;
  const sx = Math.max(w, d) / uvScale;
  const sy = Math.max(h, d) / uvScale;
  for (let i = 0; i < a.count; i++) a.setXY(i, a.getX(i) * sx, a.getY(i) * sy);
  a.needsUpdate = true;
  return g;
}

function addStatic(mesh, collide = false) {
  mesh.castShadow = true;
  mesh.receiveShadow = true;
  scene.add(mesh);
  mesh.updateWorldMatrix(true, false);
  solids.push(mesh);
  if (collide) colliders.push(new THREE.Box3().setFromObject(mesh));
  return mesh;
}

function block(w, h, d, x, y, z, mat, collide = true, rotY = 0) {
  const m = new THREE.Mesh(boxGeo(w, h, d), mat);
  m.position.set(x, y, z);
  m.rotation.y = rotY;
  return addStatic(m, collide);
}

function groundAt(x, z, feetY) {
  let g = Math.abs(x) <= ISLAND && Math.abs(z) <= ISLAND ? 0 : TERRAIN_Y;
  if (Math.abs(x) <= 5.6 && z > 62 && z < 176) g = CAUSEWAY_Y;
  for (let i = 0; i < colliders.length; i++) {
    const b = colliders[i];
    if (x > b.min.x - 0.1 && x < b.max.x + 0.1 && z > b.min.z - 0.1 && z < b.max.z + 0.1) {
      if (b.max.y <= feetY + 0.66 && b.max.y > g) g = b.max.y;
    }
  }
  return g;
}

/* ------------------------------------------------------------ terrain */
const islandTop = new THREE.Mesh(new THREE.PlaneGeometry(ISLAND * 2, ISLAND * 2, 1, 1), matGrass);
islandTop.rotation.x = -Math.PI / 2;
islandTop.position.y = 0.01;
islandTop.receiveShadow = true;
scene.add(islandTop);

const islandBody = new THREE.Mesh(boxGeo(ISLAND * 2, 3.4, ISLAND * 2, 8), matStoneDark);
islandBody.position.y = -1.7;
islandBody.receiveShadow = true;
islandBody.castShadow = true;
scene.add(islandBody);

const ground = new THREE.Mesh(new THREE.PlaneGeometry(900, 900), matGrass);
ground.rotation.x = -Math.PI / 2;
ground.position.y = TERRAIN_Y;
ground.receiveShadow = true;
scene.add(ground);

const water = new THREE.Mesh(new THREE.PlaneGeometry(330, 330), matWater);
water.rotation.x = -Math.PI / 2;
water.position.y = WATER_Y;
water.receiveShadow = true;
scene.add(water);

/* ------------------------------------------------------------ causeway */
block(11, 2.6, 112, 0, CAUSEWAY_Y - 1.3, 120, matStoneDark, false);
for (let i = 0; i < 26; i++) {
  block(10.6, 0.22, 3.6, 0, CAUSEWAY_Y + 0.11, 68 + i * 4.2, matStoneWarm, false);
}
block(0.7, 1.05, 112, -5.2, CAUSEWAY_Y + 0.52, 120, matStone, true);
block(0.7, 1.05, 112, 5.2, CAUSEWAY_Y + 0.52, 120, matStone, true);

function nagaHead(x, y, z, dir, scale) {
  const g = new THREE.Group();
  const neck = new THREE.Mesh(boxGeo(0.9, 1.2, 0.9, 1), matStone);
  neck.position.y = 0.6;
  g.add(neck);
  const skull = new THREE.Mesh(boxGeo(0.85, 0.7, 1.5, 1), matStoneWarm);
  skull.position.set(0, 1.35, 0.45);
  g.add(skull);
  const crown = new THREE.Mesh(new THREE.ConeGeometry(0.55, 0.75, 5), matGold);
  crown.position.set(0, 1.9, 0.35);
  g.add(crown);
  for (let i = -1; i <= 1; i += 2) {
    const eye = new THREE.Mesh(new THREE.SphereGeometry(0.09, 8, 8), matGold);
    eye.position.set(0.3 * i, 1.5, 1.02);
    g.add(eye);
  }
  g.traverse((o) => { if (o.isMesh) { o.castShadow = true; o.receiveShadow = true; solids.push(o); } });
  g.position.set(x, y, z);
  g.rotation.y = dir;
  g.scale.setScalar(scale);
  scene.add(g);
  return g;
}

for (const s of [-1, 1]) {
  for (let i = 0; i < 4; i++) {
    const x = s * (5.2 + i * 1.1);
    nagaHead(x, CAUSEWAY_Y + 1.0, 176 - i * 0.35, s * 0.35, 0.85 - i * 0.06);
  }
}

/* braziers */
const brazierLights = [];
for (let i = 0; i < 4; i++) {
  const z = 96 + i * 22;
  for (const s of [-1, 1]) {
    const post = new THREE.Mesh(boxGeo(0.28, 1.9, 0.28, 1), matStoneDark);
    post.position.set(s * 4.4, CAUSEWAY_Y + 0.95, z);
    addStatic(post, false);
    const bowl = new THREE.Mesh(new THREE.CylinderGeometry(0.55, 0.3, 0.4, 10), matGold);
    bowl.position.set(s * 4.4, CAUSEWAY_Y + 2.0, z);
    addStatic(bowl, false);
    const flame = new THREE.Sprite(new THREE.SpriteMaterial({
      map: glowTex, color: 0xffa042, transparent: true, depthWrite: false,
      blending: THREE.AdditiveBlending, opacity: 0.9
    }));
    flame.position.set(s * 4.4, CAUSEWAY_Y + 2.3, z);
    flame.scale.setScalar(1.9);
    scene.add(flame);
    const light = new THREE.PointLight(0xff8a35, 6, 16, 2);
    light.position.copy(flame.position);
    scene.add(light);
    brazierLights.push({ light, flame, seed: Math.random() * 10 });
  }
}

/* ------------------------------------------------------------ temple */
const T1 = 1.6, T2 = 3.6, T3 = 6.0;

block(96, T1, 96, 0, T1 / 2, 0, matStone, true);
block(74, T2 - T1, 74, 0, T1 + (T2 - T1) / 2, 0, matStone, true);
block(48, T3 - T2, 48, 0, T2 + (T3 - T2) / 2, 0, matStoneWarm, true);

function stairs(cx, zFront, width, fromY, toY, dir) {
  const steps = Math.max(3, Math.round((toY - fromY) / 0.38));
  const rise = (toY - fromY) / steps;
  for (let i = 0; i < steps; i++) {
    const h = rise * (steps - i);
    block(width, h, 1.05, cx, fromY + h / 2, zFront + dir * (i + 0.5) * 1.05, matStoneWarm, true);
  }
}

stairs(0, 48, 9, 0, T1, 1);
stairs(0, 37, 8, T1, T2, 1);
stairs(0, 24, 7, T2, T3, 1);
stairs(0, -48, 9, 0, T1, -1);
stairs(0, -24, 7, T2, T3, -1);

/* relief frieze along the first terrace */
for (let i = 0; i < 4; i++) {
  const a = i * Math.PI / 2;
  const panel = new THREE.Mesh(new THREE.PlaneGeometry(64, 1.05), matRelief);
  panel.position.set(Math.sin(a) * 48.08, 0.85, Math.cos(a) * 48.08);
  panel.rotation.y = a;
  panel.receiveShadow = true;
  scene.add(panel);
  solids.push(panel);
}

/* towers */
function tower(x, z, baseY, size, height) {
  let y = baseY;
  const tiers = 5;
  for (let i = 0; i < tiers; i++) {
    const w = size * (1 - i * 0.145);
    const th = (height / tiers) * (1 - i * 0.05);
    const body = new THREE.Mesh(boxGeo(w, th, w, 3), matStone);
    body.position.set(x, y + th / 2, z);
    addStatic(body, i === 0);
    const cornice = new THREE.Mesh(boxGeo(w + 0.8, 0.32, w + 0.8, 2), matStoneWarm);
    cornice.position.set(x, y + th, z);
    addStatic(cornice, false);
    y += th;
  }
  const cap = new THREE.Mesh(boxGeo(size * 0.34, 0.9, size * 0.34, 2), matStoneWarm);
  cap.position.set(x, y + 0.45, z);
  addStatic(cap, false);
  const bud = new THREE.Mesh(new THREE.ConeGeometry(size * 0.2, size * 0.62, 9), matGold);
  bud.position.set(x, y + 0.9 + size * 0.31, z);
  addStatic(bud, false);
  const ring = new THREE.Mesh(new THREE.TorusGeometry(size * 0.16, size * 0.028, 6, 14), matGold);
  ring.position.set(x, y + 1.15, z);
  ring.rotation.x = Math.PI / 2;
  addStatic(ring, false);
}

tower(0, 0, T3, 11, 27);
tower(-16, -16, T3, 8, 17);
tower(16, -16, T3, 8, 17);
tower(-16, 16, T3, 8, 17);
tower(16, 16, T3, 8, 17);

/* galleries with relief walls and pillars */
function gallery(cx, cz, len, alongZ, y) {
  const w = 7;
  const depth = alongZ ? w : len;
  const width = alongZ ? len : w;
  const sx = alongZ ? 1 : -1;

  block(width + 1.4, 0.35, depth + 1.4, cx, y + 0.17, cz, matStoneWarm, true);

  const wallOff = 0;
  block(alongZ ? 0.9 : len, 3.1, alongZ ? len : 0.9,
    cx + (alongZ ? sx * (w / 2 - 0.45) : 0), y + 1.55,
    cz + (alongZ ? 0 : sx * (w / 2 - 0.45)), matRelief, true);

  const pillarCount = Math.floor(len / 4);
  for (let i = 0; i <= pillarCount; i++) {
    const t = -len / 2 + i * (len / pillarCount);
    const px = alongZ ? cx - sx * (w / 2 - 0.6) : cx + t;
    const pz = alongZ ? cz + t : cz - sx * (w / 2 - 0.6);
    const col = new THREE.Mesh(new THREE.CylinderGeometry(0.34, 0.4, 3.1, 8), matStone);
    col.position.set(px, y + 1.9, pz);
    addStatic(col, false);
    const cap = new THREE.Mesh(boxGeo(0.95, 0.28, 0.95, 1), matStoneWarm);
    cap.position.set(px, y + 3.42, pz);
    addStatic(cap, false);
  }

  for (let i = 0; i < 3; i++) {
    block(width + 1.8 - i * 1.2, 0.42, depth + 1.8 - i * 1.2,
      cx, y + 3.62 + i * 0.42, cz, i === 1 ? matStoneWarm : matStone, false);
  }
  block(alongZ ? 0.7 : len * 0.72, 0.5, alongZ ? len * 0.72 : 0.7, cx, y + 4.9, cz, matGold, false);
}

gallery(-44, 0, 50, true, T1);
gallery(44, 0, 50, true, T1);
gallery(0, -44, 50, false, T1);
gallery(0, 44, 50, false, T1);

/* guardian lions flanking the causeway */
function lion(x, z, rot) {
  const g = new THREE.Group();
  const base = new THREE.Mesh(boxGeo(1.5, 0.6, 2.2, 1), matStoneWarm);
  base.position.y = 0.3;
  g.add(base);
  const body = new THREE.Mesh(boxGeo(1.0, 1.0, 1.5, 1), matStone);
  body.position.y = 1.1;
  g.add(body);
  const head = new THREE.Mesh(boxGeo(0.9, 0.85, 0.85, 1), matStoneWarm);
  head.position.set(0, 1.85, 0.45);
  g.add(head);
  const mane = new THREE.Mesh(new THREE.IcosahedronGeometry(0.72, 0), matStoneDark);
  mane.position.set(0, 1.9, 0.2);
  g.add(mane);
  g.traverse((o) => { if (o.isMesh) { o.castShadow = true; o.receiveShadow = true; solids.push(o); } });
  g.position.set(x, CAUSEWAY_Y, z);
  g.rotation.y = rot;
  scene.add(g);
}
lion(-6.4, 70, 2.6);
lion(6.4, 70, -2.6);

/* ------------------------------------------------------------ jungle */
function tree(x, z, s, palm) {
  const g = new THREE.Group();
  const h = (palm ? 9 : 6.5) * s;
  const trunk = new THREE.Mesh(new THREE.CylinderGeometry(0.22 * s, 0.42 * s, h, 6), matBark);
  trunk.position.y = h / 2;
  g.add(trunk);
  if (!palm) {
    for (let i = 0; i < 3; i++) {
      const canopy = new THREE.Mesh(new THREE.IcosahedronGeometry((2.1 + i * 0.5) * s, 0), i % 2 ? matLeaf : matLeaf2);
      canopy.position.set(rand(-1.4, 1.4) * s, h + i * 1.1 * s - 0.4, rand(-1.4, 1.4) * s);
      canopy.rotation.set(rand(0, 3), rand(0, 3), rand(0, 3));
      g.add(canopy);
    }
  } else {
    for (let i = 0; i < 7; i++) {
      const a = (i / 7) * Math.PI * 2;
      const frond = new THREE.Mesh(new THREE.ConeGeometry(0.55 * s, 4.2 * s, 4), matLeaf);
      frond.position.set(Math.cos(a) * 1.5 * s, h, Math.sin(a) * 1.5 * s);
      frond.rotation.set(Math.PI / 2.6, -a, 0, 'YXZ');
      g.add(frond);
    }
  }
  g.traverse((o) => { if (o.isMesh) { o.castShadow = true; o.receiveShadow = true; } });
  g.position.set(x, TERRAIN_Y, z);
  scene.add(g);
}

let placed = 0;
while (placed < 150) {
  const x = rand(-380, 380), z = rand(-380, 380);
  const edge = Math.max(Math.abs(x), Math.abs(z));
  if (edge < 160) continue;
  tree(x, z, rand(0.8, 1.5), Math.random() < 0.22);
  placed++;
}
for (let i = 0; i < 22; i++) {
  const a = rand(0, Math.PI * 2), r = rand(58, 70);
  tree(Math.cos(a) * r, Math.sin(a) * r, rand(0.7, 1.1), Math.random() < 0.4);
}

/* grass tufts */
const tuftGeo = new THREE.ConeGeometry(0.09, 0.62, 3);
const tufts = new THREE.InstancedMesh(tuftGeo, matLeaf, 2400);
const dummy = new THREE.Object3D();
for (let i = 0; i < 2400; i++) {
  const onIsland = Math.random() < 0.62;
  const x = onIsland ? rand(-71, 71) : rand(-200, 200);
  const z = onIsland ? rand(-71, 71) : rand(-200, 200);
  dummy.position.set(x, onIsland ? 0.25 : TERRAIN_Y + 0.25, z);
  dummy.rotation.y = rand(0, 6.28);
  dummy.scale.setScalar(rand(0.7, 1.5));
  dummy.updateMatrix();
  tufts.setMatrixAt(i, dummy.matrix);
}
tufts.instanceMatrix.needsUpdate = true;
tufts.receiveShadow = true;
scene.add(tufts);

/* fallen stones and broken columns */
for (let i = 0; i < 30; i++) {
  const a = rand(0, Math.PI * 2), r = rand(52, 70);
  const x = Math.cos(a) * r, z = Math.sin(a) * r;
  if (Math.random() < 0.5) {
    const s = new THREE.Mesh(boxGeo(rand(1.2, 2.8), rand(0.6, 1.1), rand(1.2, 2.6), 1), matStone);
    s.position.set(x, rand(0.3, 0.5), z);
    s.rotation.set(rand(-0.3, 0.3), rand(0, 3), rand(-0.3, 0.3));
    addStatic(s, false);
  } else {
    const c = new THREE.Mesh(new THREE.CylinderGeometry(rand(0.4, 0.6), rand(0.45, 0.65), rand(1.2, 3.4), 7), matStoneWarm);
    c.position.set(x, c.geometry.parameters.height / 2, z);
    c.rotation.z = Math.random() < 0.4 ? rand(-0.5, 0.5) : 0;
    addStatic(c, false);
  }
}


/* distant hills for depth */
const hillMat = new THREE.MeshStandardMaterial({ color: 0x6d7452, roughness: 1 });
for (let i = 0; i < 9; i++) {
  const r = rand(55, 130), h = rand(30, 78);
  const hill = new THREE.Mesh(new THREE.ConeGeometry(r, h, 7), hillMat);
  hill.position.set(rand(-420, 420), h / 2 - 12, -330 - rand(0, 160));
  hill.rotation.y = rand(0, 3);
  hill.receiveShadow = true;
  scene.add(hill);
}

/* ------------------------------------------------------------ dust motes */
const dustCount = 900;
const dustPos = new Float32Array(dustCount * 3);
for (let i = 0; i < dustCount; i++) {
  dustPos[i * 3] = rand(-110, 110);
  dustPos[i * 3 + 1] = rand(0.4, 26);
  dustPos[i * 3 + 2] = rand(-40, 170);
}
const dustGeo = new THREE.BufferGeometry();
dustGeo.setAttribute('position', new THREE.BufferAttribute(dustPos, 3));
const dust = new THREE.Points(dustGeo, new THREE.PointsMaterial({
  size: 0.16, map: sparkTex, color: 0xffd9a0, transparent: true, opacity: 0.5,
  depthWrite: false, blending: THREE.AdditiveBlending, sizeAttenuation: true
}));
scene.add(dust);

/* birds */
const birdMat = new THREE.MeshBasicMaterial({ color: 0x2a1d12, fog: false });
const birds = [];
for (let i = 0; i < 12; i++) {
  const b = new THREE.Group();
  const wingGeo = new THREE.PlaneGeometry(1.6, 0.34);
  const wL = new THREE.Mesh(wingGeo, birdMat);
  const wR = new THREE.Mesh(wingGeo, birdMat);
  wL.position.x = -0.85; wR.position.x = 0.85;
  b.add(wL, wR);
  b.userData = { r: rand(45, 130), a: rand(0, 6.28), y: rand(34, 62), sp: rand(0.06, 0.16), fl: rand(0, 6.28) };
  scene.add(b);
  birds.push(b);
}

/* ------------------------------------------------------------ effects */
const SPARKS = 420;
const spPos = new Float32Array(SPARKS * 3);
const spCol = new Float32Array(SPARKS * 3);
const spVel = new Float32Array(SPARKS * 3);
const spLife = new Float32Array(SPARKS);
const spTtl = new Float32Array(SPARKS);
for (let i = 0; i < SPARKS; i++) spPos[i * 3 + 1] = -999;
const sparkGeo = new THREE.BufferGeometry();
sparkGeo.setAttribute('position', new THREE.BufferAttribute(spPos, 3));
sparkGeo.setAttribute('color', new THREE.BufferAttribute(spCol, 3));
const sparkPoints = new THREE.Points(sparkGeo, new THREE.PointsMaterial({
  size: 0.17, map: sparkTex, vertexColors: true, transparent: true,
  depthWrite: false, blending: THREE.AdditiveBlending
}));
sparkPoints.frustumCulled = false;
scene.add(sparkPoints);
let spCursor = 0;

function spawnSparks(p, n, color, speed) {
  for (let k = 0; k < n; k++) {
    const i = spCursor = (spCursor + 1) % SPARKS;
    spPos[i * 3] = p.x + rand(-0.08, 0.08);
    spPos[i * 3 + 1] = p.y + rand(-0.08, 0.08);
    spPos[i * 3 + 2] = p.z + rand(-0.08, 0.08);
    const a = rand(0, Math.PI * 2), b = rand(-0.6, 1);
    const s = speed * rand(0.35, 1);
    spVel[i * 3] = Math.cos(a) * s;
    spVel[i * 3 + 1] = b * s + 1.4;
    spVel[i * 3 + 2] = Math.sin(a) * s;
    spLife[i] = spTtl[i] = rand(0.28, 0.75);
    spCol[i * 3] = color.r; spCol[i * 3 + 1] = color.g; spCol[i * 3 + 2] = color.b;
  }
}

const decalMat = new THREE.MeshBasicMaterial({
  map: holeTex, transparent: true, depthWrite: false, opacity: 0.95,
  polygonOffset: true, polygonOffsetFactor: -4
});
const decals = [];
for (let i = 0; i < 44; i++) {
  const d = new THREE.Mesh(new THREE.PlaneGeometry(0.3, 0.3), decalMat);
  d.visible = false;
  scene.add(d);
  decals.push(d);
}
let decalCursor = 0;

function addDecal(point, normal) {
  const d = decals[decalCursor = (decalCursor + 1) % decals.length];
  d.position.copy(point).addScaledVector(normal, 0.012);
  d.lookAt(point.clone().add(normal));
  d.visible = true;
}

const tracers = [];
for (let i = 0; i < 10; i++) {
  const g = new THREE.BufferGeometry().setFromPoints([new THREE.Vector3(), new THREE.Vector3()]);
  const m = new THREE.LineBasicMaterial({ color: 0xffd9a0, transparent: true, opacity: 0, blending: THREE.AdditiveBlending, depthWrite: false });
  const line = new THREE.Line(g, m);
  line.frustumCulled = false;
  scene.add(line);
  tracers.push({ line, life: 0 });
}
let tracerCursor = 0;

function addTracer(from, to) {
  const t = tracers[tracerCursor = (tracerCursor + 1) % tracers.length];
  t.line.geometry.setFromPoints([from.clone(), to.clone()]);
  t.line.material.opacity = 0.85;
  t.life = 0.07;
}

/* ------------------------------------------------------------ weapon */
const gun = new THREE.Group();
const matGun = new THREE.MeshStandardMaterial({ color: 0x474d56, metalness: 0.45, roughness: 0.52 });
const matGunDark = new THREE.MeshStandardMaterial({ color: 0x2b2f35, metalness: 0.4, roughness: 0.6 });
const matGrip = new THREE.MeshStandardMaterial({ color: 0x7a4e2c, metalness: 0.1, roughness: 0.72 });

function part(geo, mat, x, y, z, rx = 0, ry = 0, rz = 0) {
  const m = new THREE.Mesh(geo, mat);
  m.position.set(x, y, z);
  m.rotation.set(rx, ry, rz);
  m.castShadow = false;
  gun.add(m);
  return m;
}

part(new THREE.BoxGeometry(0.1, 0.13, 0.52), matGun, 0, 0, 0);
part(new THREE.BoxGeometry(0.075, 0.075, 0.44), matGunDark, 0, 0.005, -0.36);
part(new THREE.CylinderGeometry(0.026, 0.026, 0.46, 8), matGunDark, 0, 0.012, -0.58, Math.PI / 2);
part(new THREE.BoxGeometry(0.085, 0.2, 0.1), matGrip, 0, -0.13, 0.16, 0.28);
part(new THREE.BoxGeometry(0.07, 0.16, 0.12), matGunDark, 0, -0.13, -0.06, -0.06);
part(new THREE.BoxGeometry(0.09, 0.1, 0.2), matGrip, 0, -0.02, 0.32);
part(new THREE.BoxGeometry(0.06, 0.05, 0.14), matGun, 0, 0.1, -0.05);
part(new THREE.TorusGeometry(0.035, 0.008, 6, 10), matGunDark, 0, 0.13, -0.1, Math.PI / 2);
const muzzle = new THREE.Object3D();
muzzle.position.set(0, 0.012, -0.82);
gun.add(muzzle);

const flash = new THREE.Sprite(new THREE.SpriteMaterial({
  map: glowTex, color: 0xffcf8a, transparent: true, depthWrite: false,
  blending: THREE.AdditiveBlending, opacity: 0
}));
flash.position.copy(muzzle.position);
flash.scale.setScalar(0.7);
gun.add(flash);

const muzzleLight = new THREE.PointLight(0xffb060, 0, 9, 2);
muzzleLight.position.copy(muzzle.position);
gun.add(muzzleLight);

gun.position.set(0.28, -0.24, -0.78);
gun.rotation.set(0.02, -0.03, 0);
gun.scale.setScalar(0.95);
camera.add(gun);
const gunLight = new THREE.PointLight(0xffd6a8, 1.6, 3.2, 2);
gunLight.position.set(0.2, 0.15, 0.2);
camera.add(gunLight);

const gunBase = new THREE.Vector3(0.28, -0.24, -0.78);
let kick = 0, kickRot = 0, bobT = 0;

/* ------------------------------------------------------------ player */
const GRAVITY = 24;
const player = {
  pos: new THREE.Vector3(0, CAUSEWAY_Y, 150),
  vel: new THREE.Vector3(),
  yaw: 0, pitch: 0,
  onGround: true, hp: 100,
  radius: 0.42, height: 1.76, eye: 1.58
};

const keys = Object.create(null);
const raycaster = new THREE.Raycaster();

let ammo = 30;
const MAG = 30;
let reloading = false, reloadT = 0, nextShot = 0;

function collide(pos) {
  const r = player.radius;
  for (let i = 0; i < colliders.length; i++) {
    const b = colliders[i];
    if (b.max.y < pos.y + 0.08 || b.min.y > pos.y + player.height) continue;
    if (b.max.y <= pos.y + 0.66) continue;
    const cx = clamp(pos.x, b.min.x, b.max.x);
    const cz = clamp(pos.z, b.min.z, b.max.z);
    const dx = pos.x - cx, dz = pos.z - cz;
    const d2 = dx * dx + dz * dz;
    if (d2 >= r * r) continue;
    if (d2 > 1e-6) {
      const d = Math.sqrt(d2);
      pos.x += (dx / d) * (r - d);
      pos.z += (dz / d) * (r - d);
    } else {
      const l = pos.x - b.min.x, rr = b.max.x - pos.x;
      const bk = pos.z - b.min.z, fr = b.max.z - pos.z;
      const m = Math.min(l, rr, bk, fr);
      if (m === l) pos.x = b.min.x - r;
      else if (m === rr) pos.x = b.max.x + r;
      else if (m === bk) pos.z = b.min.z - r;
      else pos.z = b.max.z + r;
    }
  }
}

function updatePlayer(dt) {
  const sprint = keys['ShiftLeft'] || keys['ShiftRight'];
  const speed = sprint ? 8.4 : 5.2;
  const f = new THREE.Vector3(-Math.sin(player.yaw), 0, -Math.cos(player.yaw));
  const right = new THREE.Vector3(Math.cos(player.yaw), 0, -Math.sin(player.yaw));
  const wish = new THREE.Vector3();
  if (isTouch) {
    wish.addScaledVector(f, -touchMove.y).addScaledVector(right, touchMove.x);
  } else {
    if (keys['KeyW'] || keys['ArrowUp']) wish.add(f);
    if (keys['KeyS'] || keys['ArrowDown']) wish.sub(f);
    if (keys['KeyD'] || keys['ArrowRight']) wish.add(right);
    if (keys['KeyA'] || keys['ArrowLeft']) wish.sub(right);
  }
  const moving = wish.lengthSq() > 0;
  if (moving) wish.normalize().multiplyScalar(speed);

  player.vel.x += (wish.x - player.vel.x) * Math.min(1, dt * 12);
  player.vel.z += (wish.z - player.vel.z) * Math.min(1, dt * 12);
  player.vel.y -= GRAVITY * dt;

  player.pos.x += player.vel.x * dt;
  player.pos.z += player.vel.z * dt;
  collide(player.pos);

  player.pos.y += player.vel.y * dt;
  const g = groundAt(player.pos.x, player.pos.z, player.pos.y + 0.05);
  if (player.pos.y <= g + 0.001) {
    player.pos.y = g;
    player.vel.y = 0;
    player.onGround = true;
  } else {
    player.onGround = false;
  }

  const inMoat = Math.abs(player.pos.x) < 164 && Math.abs(player.pos.z) < 164
    && (Math.abs(player.pos.x) > 71 || Math.abs(player.pos.z) > 71);
  if (inMoat && player.pos.y < WATER_Y + 0.05) {
    player.pos.set(0, CAUSEWAY_Y, 158);
    player.vel.set(0, 0, 0);
    damagePlayer(8, 'ទឹកស្ទឹង… អ្នកធ្លាក់ចូលទឹក');
  }

  if (moving && player.onGround) bobT += dt * (sprint ? 12 : 8);
  const bob = moving && player.onGround ? Math.sin(bobT) * 0.035 : 0;
  camera.position.set(player.pos.x, player.pos.y + player.eye + bob * 0.6, player.pos.z);
  camera.rotation.set(player.pitch, player.yaw, 0);

  kick *= Math.pow(0.0009, dt);
  kickRot *= Math.pow(0.0009, dt);
  gun.position.set(
    gunBase.x + Math.cos(bobT * 0.5) * 0.008,
    gunBase.y + bob * 0.8 - kick * 0.3,
    gunBase.z + kick
  );
  gun.rotation.set(0.02 + kickRot, -0.03 + bob * 0.4, bob * 0.5);
  flash.material.opacity *= Math.pow(0.0001, dt);
  muzzleLight.intensity *= Math.pow(0.00005, dt);
}

/* ------------------------------------------------------------ audio */
let actx = null;
function audioCtx() {
  if (!actx) {
    const AC = window.AudioContext || window.webkitAudioContext;
    if (AC) actx = new AC();
  }
  return actx;
}
function blip(freq, dur, type, gain, slide) {
  const a = audioCtx();
  if (!a) return;
  const o = a.createOscillator(), g = a.createGain();
  o.type = type;
  o.frequency.setValueAtTime(freq, a.currentTime);
  if (slide) o.frequency.exponentialRampToValueAtTime(Math.max(40, slide), a.currentTime + dur);
  g.gain.setValueAtTime(gain, a.currentTime);
  g.gain.exponentialRampToValueAtTime(0.0001, a.currentTime + dur);
  o.connect(g); g.connect(a.destination);
  o.start(); o.stop(a.currentTime + dur + 0.02);
}
function noiseBurst(dur, cutoff, gain) {
  const a = audioCtx();
  if (!a) return;
  const n = Math.floor(a.sampleRate * dur);
  const buf = a.createBuffer(1, n, a.sampleRate);
  const d = buf.getChannelData(0);
  for (let i = 0; i < n; i++) d[i] = (Math.random() * 2 - 1) * Math.pow(1 - i / n, 2);
  const src = a.createBufferSource();
  src.buffer = buf;
  const f = a.createBiquadFilter();
  f.type = 'lowpass';
  f.frequency.value = cutoff;
  const g = a.createGain();
  g.gain.value = gain;
  src.connect(f); f.connect(g); g.connect(a.destination);
  src.start();
}
const sfx = {
  shot() { noiseBurst(0.18, 1800, 0.35); blip(120, 0.16, 'sawtooth', 0.18, 45); },
  hit() { blip(880, 0.06, 'square', 0.07, 620); },
  kill() { blip(180, 0.35, 'triangle', 0.16, 70); noiseBurst(0.3, 700, 0.14); },
  reload() { blip(320, 0.05, 'square', 0.06); setTimeout(() => blip(220, 0.06, 'square', 0.06), 160); },
  hurt() { blip(90, 0.3, 'sawtooth', 0.2, 55); },
  roar() { blip(70, 0.55, 'sawtooth', 0.13, 40); noiseBurst(0.5, 400, 0.1); }
};

/* ------------------------------------------------------------ HUD */
const el = {
  score: $('#score'), wave: $('#wave'), enemies: $('#enemies'),
  ammo: $('#ammo'), hp: $('#hpfill'), hit: $('#hitmarker'),
  toast: $('#toast'), dmg: $('#damage'), cross: $('#crosshair'),
  reload: $('#reloadnote'), menu: $('#menu'), hud: $('#hud'), start: $('#startBtn')
};

let toastTimer = 0;
function toast(title, sub) {
  el.toast.querySelector('b').textContent = title;
  el.toast.querySelector('span').textContent = sub || '';
  el.toast.classList.add('on');
  toastTimer = 2.2;
}
let hitTimer = 0;
let damageFlash = 0;

function updateHud() {
  el.score.textContent = game.score;
  el.wave.textContent = game.wave;
  el.enemies.textContent = game.alive;
  el.ammo.textContent = ammo;
  el.hp.style.width = Math.max(0, player.hp) + '%';
}

function damagePlayer(n, msg) {
  if (!game.on || game.over) return;
  player.hp -= n;
  damageFlash = 1;
  lastHurt = performance.now() / 1000;
  sfx.hurt();
  if (msg) toast('រងរបួស', msg);
  if (player.hp <= 0) {
    player.hp = 0;
    gameOver();
  }
  updateHud();
}

let lastHurt = -99;

/* ------------------------------------------------------------ guardians */
const guardians = [];
const enemyMeshes = [];

function makeGuardian(x, z, hp, speed, dmg) {
  const g = new THREE.Group();
  const torso = new THREE.Mesh(boxGeo(1.0, 1.15, 0.62, 1), matStone);
  torso.position.y = 1.28;
  g.add(torso);
  const chest = new THREE.Mesh(boxGeo(1.12, 0.4, 0.7, 1), matGold);
  chest.position.y = 1.72;
  g.add(chest);
  const head = new THREE.Mesh(boxGeo(0.6, 0.6, 0.58, 1), matStoneWarm);
  head.position.y = 2.12;
  g.add(head);
  const crown = new THREE.Mesh(new THREE.ConeGeometry(0.34, 0.55, 5), matGold);
  crown.position.y = 2.62;
  g.add(crown);
  const eyeMat = new THREE.MeshBasicMaterial({ color: 0xff5522 });
  for (const s of [-1, 1]) {
    const eye = new THREE.Mesh(new THREE.BoxGeometry(0.12, 0.06, 0.05), eyeMat);
    eye.position.set(0.15 * s, 2.18, 0.3);
    g.add(eye);
  }
  const arms = [], legs = [];
  for (const s of [-1, 1]) {
    const arm = new THREE.Group();
    const up = new THREE.Mesh(boxGeo(0.26, 0.95, 0.3, 1), matStone);
    up.position.y = -0.45;
    arm.add(up);
    arm.position.set(0.66 * s, 1.72, 0);
    g.add(arm);
    arms.push(arm);
    const leg = new THREE.Group();
    const lb = new THREE.Mesh(boxGeo(0.3, 0.95, 0.34, 1), matStoneDark);
    lb.position.y = -0.48;
    leg.add(lb);
    leg.position.set(0.26 * s, 0.95, 0);
    g.add(leg);
    legs.push(leg);
  }
  g.position.set(x, groundAt(x, z, 0), z);
  g.traverse((o) => { if (o.isMesh) { o.castShadow = true; o.receiveShadow = true; } });
  scene.add(g);

  const enemy = {
    group: g, hp, maxHp: hp, speed, dmg, alive: true, dying: 0,
    arms, legs, eyeMat, awake: false, cooldown: 0, walk: rand(0, 6)
  };
  g.traverse((o) => {
    if (o.isMesh) {
      o.userData.enemy = enemy;
      o.userData.head = o === head || o === crown;
      enemyMeshes.push(o);
    }
  });
  guardians.push(enemy);
  return enemy;
}

function updateGuardians(dt, t) {
  for (let i = guardians.length - 1; i >= 0; i--) {
    const e = guardians[i];
    const p = e.group.position;

    if (!e.alive) {
      e.dying += dt;
      e.group.rotation.x = -Math.min(1.5, e.dying * 3.2);
      if (e.dying > 0.9) {
        e.group.position.y -= dt * 1.6;
        e.group.scale.setScalar(Math.max(0.001, 1 - (e.dying - 0.9) * 1.2));
      }
      if (e.dying > 1.8) {
        scene.remove(e.group);
        guardians.splice(i, 1);
      }
      continue;
    }

    const dx = player.pos.x - p.x, dz = player.pos.z - p.z;
    const dist = Math.hypot(dx, dz);
    if (!e.awake && dist < 46) {
      e.awake = true;
      sfx.roar();
    }

    e.cooldown = Math.max(0, e.cooldown - dt);

    const insideIsland = Math.abs(p.x) < 68 && Math.abs(p.z) < 68;
    if (e.awake && dist > 2.1 && insideIsland) {
      const step = e.speed * dt;
      p.x += (dx / dist) * step;
      p.z += (dz / dist) * step;
      e.walk += dt * 7;
      e.group.rotation.y = Math.atan2(dx, dz);
      e.arms[0].rotation.x = Math.sin(e.walk) * 0.65;
      e.arms[1].rotation.x = -Math.sin(e.walk) * 0.65;
      e.legs[0].rotation.x = -Math.sin(e.walk) * 0.55;
      e.legs[1].rotation.x = Math.sin(e.walk) * 0.55;
      e.group.position.y = groundAt(p.x, p.z, p.y + 0.4) + Math.abs(Math.sin(e.walk)) * 0.05;
    } else if (e.awake && dist <= 2.1 && e.cooldown <= 0) {
      e.cooldown = 1.15;
      e.arms[0].rotation.x = -2.2;
      damagePlayer(e.dmg, null);
    }
    if (e.awake && dist > 90) {
      e.awake = false;
    }
  }
}

function damageEnemy(e, amount, point, dir) {
  if (!e.alive) return;
  e.hp -= amount;
  spawnSparks(point, 10, new THREE.Color(0xff9a5a), 3.4);
  sfx.hit();
  hitTimer = 0.22;
  el.hit.classList.add('on');
  if (e.hp <= 0) {
    e.alive = false;
    e.dying = 0;
    game.score += 100;
    game.alive = Math.max(0, game.alive - 1);
    sfx.kill();
    spawnSparks(point, 26, new THREE.Color(0xffb070), 4.6);
    updateHud();
    if (game.alive === 0 && !game.over) {
      game.wave += 1;
      toast('រលក ' + game.wave, 'យក្សកាន់តែច្រើនមកដល់');
      updateHud();
      setTimeout(() => { if (game.on && !game.over) startWave(game.wave); }, 2600);
    }
  } else {
    e.group.position.x -= dir.x * 0.06;
    e.group.position.z -= dir.z * 0.06;
  }
}

/* ------------------------------------------------------------ game state */
const game = { on: false, over: false, score: 0, wave: 0, alive: 0 };
let shooting = false;
let movingNow = false;
const tmpV = new THREE.Vector3();
const muzzleWorld = new THREE.Vector3();

function startWave(n) {
  game.wave = n;
  const count = Math.min(16, 3 + n * 2);
  for (let i = 0; i < count; i++) {
    const a = rand(0, Math.PI * 2);
    const r = rand(16, 60);
    let x = Math.cos(a) * r, z = Math.sin(a) * r;
    if (Math.hypot(x - player.pos.x, z - player.pos.z) < 14) {
      x += 16; z += 12;
      x = clamp(x, -66, 66); z = clamp(z, -66, 66);
    }
    makeGuardian(x, z, 60 + n * 22, 2.15 + Math.min(2.3, n * 0.18), 7 + n * 1.5);
  }
  game.alive = count;
  updateHud();
  toast('រលក ' + n, count + ' យក្សភ្ញាក់ឡើង');
  sfx.roar();
}

function startReload() {
  if (reloading || ammo === MAG) return;
  reloading = true;
  reloadT = 1.5;
  el.reload.classList.add('on');
  sfx.reload();
}

function shoot() {
  const t = performance.now() / 1000;
  if (!game.on || game.over || reloading) return;
  if (t < nextShot) return;
  nextShot = t + 0.115;

  if (ammo <= 0) { startReload(); return; }
  ammo--;
  updateHud();
  sfx.shot();
  kick = 0.085;
  kickRot = 0.055;
  flash.material.opacity = 0.95;
  muzzleLight.intensity = 10;
  el.cross.classList.add('fire');
  setTimeout(() => el.cross.classList.remove('fire'), 90);

  camera.getWorldPosition(tmpV);
  const dir = new THREE.Vector3(0, 0, -1).applyQuaternion(camera.quaternion).normalize();
  const spread = (movingNow ? 0.013 : 0.004) + (player.onGround ? 0 : 0.02);
  dir.x += rand(-spread, spread);
  dir.y += rand(-spread, spread);
  dir.z += rand(-spread, spread);
  dir.normalize();

  raycaster.set(tmpV, dir);
  raycaster.far = 420;
  const targets = solids.concat(enemyMeshes);
  const hits = raycaster.intersectObjects(targets, false);
  const end = tmpV.clone().addScaledVector(dir, 160);

  if (hits.length) {
    const h = hits[0];
    end.copy(h.point);
    const e = h.object.userData.enemy;
    if (e && e.alive) {
      const head = h.object.userData.head;
      damageEnemy(e, head ? 78 : 26, h.point, dir);
    } else {
      const n = h.face ? h.face.normal.clone().transformDirection(h.object.matrixWorld) : dir.clone().negate();
      addDecal(h.point, n);
      spawnSparks(h.point, 11, new THREE.Color(0xffc483), 3.4);
    }
  }

  muzzle.getWorldPosition(muzzleWorld);
  addTracer(muzzleWorld, end);
}

function gameOver() {
  game.on = false;
  game.over = true;
  document.exitPointerLock();
  el.menu.classList.remove('hide');
  el.start.textContent = 'លេងម្តងទៀត';
  el.menu.querySelector('.sub').textContent =
    'អ្នកបានការពារប្រាសាទបាន ' + game.wave + ' រលក • ពិន្ទុ ' + game.score;
}

function resetGame() {
  for (const e of guardians) scene.remove(e.group);
  guardians.length = 0;
  enemyMeshes.length = 0;
  player.hp = 100;
  player.pos.set(0, CAUSEWAY_Y, 150);
  player.vel.set(0, 0, 0);
  player.yaw = 0;
  player.pitch = 0;
  ammo = MAG;
  reloading = false;
  game.score = 0;
  game.wave = 0;
  game.alive = 0;
  game.over = false;
  updateHud();
  el.reload.classList.remove('on');
}

/* ------------------------------------------------------------ touch controls */
const isTouch = (window.matchMedia && window.matchMedia('(pointer: coarse)').matches) || 'ontouchstart' in window || /[?&]touch=1/.test(location.search);
const touchMove = { x: 0, y: 0 };
if (isTouch) document.body.classList.add('touch');

const stickEl = $('#stick');
const knobEl = $('#knob');
let stickId = null, stickCx = 0, stickCy = 0;

function stickUpdate(e) {
  const dx = e.clientX - stickCx, dy = e.clientY - stickCy;
  const len = Math.hypot(dx, dy) || 1;
  const max = 42, k = Math.min(1, len / max);
  const nx = (dx / len) * k, ny = (dy / len) * k;
  touchMove.x = nx;
  touchMove.y = ny;
  knobEl.style.transform = 'translate(calc(-50% + ' + (nx * max).toFixed(1) + 'px), calc(-50% + ' + (ny * max).toFixed(1) + 'px))';
}
function stickEnd() {
  stickId = null;
  touchMove.x = 0;
  touchMove.y = 0;
  knobEl.style.transform = 'translate(-50%,-50%)';
}
if (stickEl && knobEl) {
  stickEl.addEventListener('pointerdown', (e) => {
    e.preventDefault();
    stickId = e.pointerId;
    try { stickEl.setPointerCapture(e.pointerId); } catch (err) { /* synthetic pointer */ }
    const r = stickEl.getBoundingClientRect();
    stickCx = r.left + r.width / 2;
    stickCy = r.top + r.height / 2;
    stickUpdate(e);
  });
  stickEl.addEventListener('pointermove', (e) => { if (e.pointerId === stickId) stickUpdate(e); });
  stickEl.addEventListener('pointerup', stickEnd);
  stickEl.addEventListener('pointercancel', stickEnd);
}

let lookId = null, lookX = 0, lookY = 0;
renderer.domElement.addEventListener('pointerdown', (e) => {
  if (!isTouch || !game.on) return;
  lookId = e.pointerId;
  lookX = e.clientX;
  lookY = e.clientY;
});
renderer.domElement.addEventListener('pointermove', (e) => {
  if (!isTouch || e.pointerId !== lookId) return;
  player.yaw -= (e.clientX - lookX) * 0.0055;
  player.pitch = clamp(player.pitch - (e.clientY - lookY) * 0.0055, -1.35, 1.35);
  lookX = e.clientX;
  lookY = e.clientY;
});
const endLook = (e) => { if (e.pointerId === lookId) lookId = null; };
renderer.domElement.addEventListener('pointerup', endLook);
renderer.domElement.addEventListener('pointercancel', endLook);

const btnFire = $('#btnFire');
if (btnFire) {
  btnFire.addEventListener('pointerdown', (e) => {
    e.preventDefault();
    if (!game.on) return;
    shooting = true;
    shoot();
  });
  btnFire.addEventListener('pointerup', () => { shooting = false; });
  btnFire.addEventListener('pointercancel', () => { shooting = false; });
}
const btnReload = $('#btnReload');
if (btnReload) btnReload.addEventListener('pointerdown', (e) => { e.preventDefault(); startReload(); });
const btnJump = $('#btnJump');
if (btnJump) btnJump.addEventListener('pointerdown', (e) => {
  e.preventDefault();
  if (game.on && player.onGround) {
    player.vel.y = 7.8;
    player.onGround = false;
  }
});


/* ------------------------------------------------------------ input */
addEventListener('keydown', (e) => {
  keys[e.code] = true;
  if (e.code === 'KeyR') startReload();
  if (e.code === 'Space' && game.on) e.preventDefault();
});
addEventListener('keyup', (e) => { keys[e.code] = false; });

renderer.domElement.addEventListener('mousedown', (e) => {
  if (e.button !== 0) return;
  if (!game.on || isTouch) return;
  if (document.pointerLockElement !== renderer.domElement) {
    renderer.domElement.requestPointerLock();
    return;
  }
  shooting = true;
  shoot();
});
addEventListener('mouseup', () => { shooting = false; });

addEventListener('mousemove', (e) => {
  if (document.pointerLockElement !== renderer.domElement) return;
  player.yaw -= e.movementX * 0.0022;
  player.pitch = clamp(player.pitch - e.movementY * 0.0022, -1.45, 1.45);
});

document.addEventListener('pointerlockchange', () => {
  if (isTouch) return;
  if (document.pointerLockElement !== renderer.domElement && game.on && !game.over) {
    game.on = false;
    shooting = false;
    el.menu.classList.remove('hide');
    el.start.textContent = 'បន្តបាញ់';
    el.menu.querySelector('.sub').textContent = 'ហ្គេមផ្អាក — ចុចដើម្បីបន្ត';
  }
});

el.start.addEventListener('click', () => {
  audioCtx();
  if (game.over) resetGame();
  el.menu.classList.add('hide');
  el.hud.classList.add('on');
  game.on = true;
  if (!isTouch) renderer.domElement.requestPointerLock();
  if (!game.wave) {
    startWave(1);
  } else {
    toast('បន្តបាញ់', 'យក្សនៅសល់ ' + game.alive);
  }
  updateHud();
});

addEventListener('resize', () => {
  camera.aspect = window.innerWidth / window.innerHeight;
  camera.updateProjectionMatrix();
  renderer.setSize(window.innerWidth, window.innerHeight);
});

/* ------------------------------------------------------------ effects tick */
function updateEffects(dt, t) {
  const pos = sparkGeo.attributes.position.array;
  const col = sparkGeo.attributes.color.array;
  for (let i = 0; i < SPARKS; i++) {
    if (spLife[i] <= 0) {
      pos[i * 3 + 1] = -999;
      continue;
    }
    spLife[i] -= dt;
    pos[i * 3] += spVel[i * 3] * dt;
    pos[i * 3 + 1] += spVel[i * 3 + 1] * dt;
    pos[i * 3 + 2] += spVel[i * 3 + 2] * dt;
    spVel[i * 3 + 1] -= 17 * dt;
    const fade = Math.pow(0.06, dt);
    col[i * 3] *= fade;
    col[i * 3 + 1] *= fade;
    col[i * 3 + 2] *= fade;
    if (spLife[i] <= 0) pos[i * 3 + 1] = -999;
  }
  sparkGeo.attributes.position.needsUpdate = true;
  sparkGeo.attributes.color.needsUpdate = true;

  for (const tr of tracers) {
    if (tr.life > 0) {
      tr.life -= dt;
      tr.line.material.opacity = Math.max(0, tr.life / 0.07) * 0.85;
    } else {
      tr.line.material.opacity = 0;
    }
  }

  waterNormalMap.offset.x += dt * 0.013;
  waterNormalMap.offset.y += dt * 0.008;

  for (const b of brazierLights) {
    const f = 5.4 + Math.sin(t * 9 + b.seed) * 1.5 + Math.random() * 0.9;
    b.light.intensity = f;
    b.flame.scale.setScalar(1.7 + Math.sin(t * 7 + b.seed) * 0.22);
  }

  for (const b of birds) {
    const u = b.userData;
    u.a += dt * u.sp;
    b.position.set(Math.cos(u.a) * u.r, u.y + Math.sin(t * 0.6 + u.fl) * 2.2, Math.sin(u.a) * u.r - 20);
    b.rotation.y = -u.a + Math.PI / 2;
    b.children[0].rotation.z = Math.sin(t * 7 + u.fl) * 0.6;
    b.children[1].rotation.z = -Math.sin(t * 7 + u.fl) * 0.6;
  }

  const dp = dustGeo.attributes.position.array;
  for (let i = 0; i < dustCount; i++) {
    dp[i * 3 + 1] += dt * 0.18;
    if (dp[i * 3 + 1] > 27) dp[i * 3 + 1] = 0.4;
  }
  dustGeo.attributes.position.needsUpdate = true;

  if (hitTimer > 0) {
    hitTimer -= dt;
    if (hitTimer <= 0) el.hit.classList.remove('on');
  }
  if (damageFlash > 0) {
    damageFlash = Math.max(0, damageFlash - dt * 1.6);
    el.dmg.style.opacity = damageFlash.toFixed(3);
  }
  if (toastTimer > 0) {
    toastTimer -= dt;
    if (toastTimer <= 0) el.toast.classList.remove('on');
  }
}

/* ------------------------------------------------------------ loop */
const clock = new THREE.Clock();

function animate() {
  requestAnimationFrame(animate);
  const dt = Math.min(0.05, clock.getDelta());
  const t = performance.now() / 1000;

  if (game.on) {
    movingNow = isTouch ? Math.hypot(touchMove.x, touchMove.y) > 0.1
      : !!(keys['KeyW'] || keys['KeyA'] || keys['KeyS'] || keys['KeyD']);
    updatePlayer(dt);
    updateGuardians(dt, t);
    if (shooting) shoot();

    if (reloading) {
      reloadT -= dt;
      if (reloadT <= 0) {
        ammo = MAG;
        reloading = false;
        el.reload.classList.remove('on');
        updateHud();
      }
    }
    if (t - lastHurt > 6 && player.hp < 100) {
      player.hp = Math.min(100, player.hp + 7 * dt);
      updateHud();
    }
  } else {
    camera.position.set(player.pos.x, player.pos.y + player.eye, player.pos.z);
    camera.rotation.set(player.pitch, player.yaw, 0);
  }

  updateEffects(dt, t);
  sun.target.position.set(player.pos.x, 0, player.pos.z);
  sun.position.set(player.pos.x + sunPos.x * 0.6, sunPos.y, player.pos.z + sunPos.z * 0.6);
  sun.target.updateMatrixWorld();

  renderer.render(scene, camera);
}

updateHud();
animate();


/* ============================================================ Siem Reap city
   Stylised shophouses, Pub Street neon, river and tuk-tuks south of the moat. */
const cityMat = new THREE.MeshStandardMaterial({ color: 0x6c6152, roughness: 0.9 });
const roofMat = new THREE.MeshStandardMaterial({ color: 0x4a3a2c, roughness: 0.95 });
const roadMat = new THREE.MeshStandardMaterial({ color: 0x26262a, roughness: 0.9 });
const lineMat = new THREE.MeshBasicMaterial({ color: 0xd8c9a0 });

function facadeTex(base, lit) {
  return makeTex(128, (g, s) => {
    g.fillStyle = base; g.fillRect(0, 0, s, s);
    for (let y = 0; y < 4; y++) {
      for (let x = 0; x < 4; x++) {
        const on = Math.random() < lit;
        g.fillStyle = on
          ? 'rgba(255,203,132,' + rand(0.55, 0.95).toFixed(2) + ')'
          : 'rgba(28,24,20,0.92)';
        g.fillRect(x * s / 4 + 7, y * s / 4 + 9, s / 4 - 14, s / 4 - 18);
      }
    }
  });
}

const facades = [
  facadeTex('#8d6f4f', 0.5), facadeTex('#7a6250', 0.62),
  facadeTex('#9c8460', 0.42), facadeTex('#6b5a4a', 0.7)
];

function building(x, z, w, h, d, tex) {
  const mat = new THREE.MeshStandardMaterial({
    map: tex, emissiveMap: tex, emissive: 0xffb877,
    emissiveIntensity: 0.55, roughness: 0.85
  });
  const b = new THREE.Mesh(boxGeo(w, h, d, 4), mat);
  b.position.set(x, TERRAIN_Y + h / 2, z);
  b.castShadow = true;
  b.receiveShadow = true;
  scene.add(b);
  solids.push(b);
  const roof = new THREE.Mesh(boxGeo(w + 0.6, 0.5, d + 0.6, 2), roofMat);
  roof.position.set(x, TERRAIN_Y + h + 0.25, z);
  roof.castShadow = true;
  scene.add(roof);
  return b;
}

/* avenue + cross road */
const roadY = TERRAIN_Y + 0.03;
function road(w, d, x, z) {
  const m = new THREE.Mesh(new THREE.PlaneGeometry(w, d), roadMat);
  m.rotation.x = -Math.PI / 2;
  m.position.set(x, roadY, z);
  m.receiveShadow = true;
  scene.add(m);
  return m;
}
road(22, 210, 0, 285);
road(300, 20, 0, 352);

for (let i = 0; i < 26; i++) {
  const seg = new THREE.Mesh(new THREE.PlaneGeometry(0.5, 4), lineMat);
  seg.rotation.x = -Math.PI / 2;
  seg.position.set(0, roadY + 0.01, 182 + i * 7.6);
  scene.add(seg);
}
for (let i = 0; i < 34; i++) {
  const seg = new THREE.Mesh(new THREE.PlaneGeometry(4, 0.5), lineMat);
  seg.rotation.x = -Math.PI / 2;
  seg.position.set(-148 + i * 8.6, roadY + 0.01, 352);
  scene.add(seg);
}

/* shophouse rows along the avenue */
for (const side of [-1, 1]) {
  let z = 188;
  while (z < 330) {
    const w = rand(9, 14), d = rand(10, 16);
    building(side * (19 + w / 2), z + d / 2, w, rand(7, 17), d, facades[Math.floor(rand(0, 4))]);
    z += d + rand(2, 5);
  }
}
/* a second row further back for depth */
for (const side of [-1, 1]) {
  let z = 196;
  while (z < 326) {
    const w = rand(10, 16), d = rand(10, 14);
    building(side * (40 + w / 2), z + d / 2, w, rand(9, 22), d, facades[Math.floor(rand(0, 4))]);
    z += d + rand(3, 7);
  }
}
/* buildings along the cross road */
for (const side of [-1, 1]) {
  let x = -150;
  while (x < 150) {
    const w = rand(10, 18);
    building(x + w / 2, 352 + side * (20 + rand(6, 12)), w, rand(8, 20), rand(10, 16), facades[Math.floor(rand(0, 4))]);
    x += w + rand(4, 9);
  }
}

/* neon signs */
function neonSign(text, x, y, z, rotY, color, w) {
  const c = document.createElement('canvas');
  c.width = 512; c.height = 128;
  const g = c.getContext('2d');
  g.fillStyle = 'rgba(0,0,0,0)';
  g.fillRect(0, 0, 512, 128);
  g.font = 'bold 74px "Battambang", sans-serif';
  g.textAlign = 'center';
  g.textBaseline = 'middle';
  g.shadowColor = color;
  g.shadowBlur = 26;
  g.fillStyle = color;
  g.fillText(text, 256, 66);
  g.shadowBlur = 0;
  g.lineWidth = 2;
  g.strokeStyle = 'rgba(255,255,255,.55)';
  g.strokeText(text, 256, 66);
  const tex = new THREE.CanvasTexture(c);
  srgbTex(tex);
  const m = new THREE.Mesh(
    new THREE.PlaneGeometry(w, w / 4),
    new THREE.MeshBasicMaterial({ map: tex, transparent: true, depthWrite: false, toneMapped: false })
  );
  m.position.set(x, y, z);
  m.rotation.y = rotY;
  scene.add(m);
  const glow = new THREE.Sprite(new THREE.SpriteMaterial({
    map: glowTex, color: new THREE.Color(color), transparent: true, depthWrite: false,
    blending: THREE.AdditiveBlending, opacity: 0.35
  }));
  glow.position.copy(m.position);
  glow.scale.setScalar(w * 1.1);
  scene.add(glow);
}

neonSign('PUB STREET', -26, 7.5, 246, Math.PI / 2, '#ff4d6d', 13);
neonSign('NIGHT MARKET', 26, 8.5, 262, -Math.PI / 2, '#4dd2ff', 14);
neonSign('ម្ហូបខ្មែរ', -26, 6.4, 214, Math.PI / 2, '#ffd166', 12);
neonSign('HOSTEL', 26, 9.2, 300, -Math.PI / 2, '#7cff9b', 10);
neonSign('ANgkOR BAR', -26, 10.5, 292, Math.PI / 2, '#c88bff', 13);

/* street lamps */
const lampLights = [];
for (let i = 0; i < 7; i++) {
  const z = 190 + i * 24;
  for (const side of [-1, 1]) {
    const pole = new THREE.Mesh(boxGeo(0.22, 5.2, 0.22, 1), matStoneDark);
    pole.position.set(side * 12.5, TERRAIN_Y + 2.6, z);
    pole.castShadow = true;
    scene.add(pole);
    const head = new THREE.Mesh(new THREE.SphereGeometry(0.34, 8, 8),
      new THREE.MeshBasicMaterial({ color: 0xffd9a0 }));
    head.position.set(side * 12.5, TERRAIN_Y + 5.3, z);
    scene.add(head);
    if (i % 2 === 0) {
      const light = new THREE.PointLight(0xffb46a, 5.5, 22, 2);
      light.position.copy(head.position);
      scene.add(light);
      lampLights.push(light);
    }
  }
}

/* tuk-tuks */
function tukTuk(x, z, rot) {
  const g = new THREE.Group();
  const body = new THREE.Mesh(boxGeo(1.6, 1.1, 2.4, 1),
    new THREE.MeshStandardMaterial({ color: 0xb23b2e, roughness: 0.6, metalness: 0.2 }));
  body.position.y = 1.15;
  g.add(body);
  const cabin = new THREE.Mesh(boxGeo(1.5, 1.0, 1.0, 1),
    new THREE.MeshStandardMaterial({ color: 0x2c2c30, roughness: 0.5 }));
  cabin.position.set(0, 1.9, -1.1);
  g.add(cabin);
  for (const sx of [-0.85, 0.85]) {
    for (const sz of [-1.2, 1.1]) {
      const wheel = new THREE.Mesh(new THREE.CylinderGeometry(0.38, 0.38, 0.22, 10),
        new THREE.MeshStandardMaterial({ color: 0x141416, roughness: 0.9 }));
      wheel.rotation.z = Math.PI / 2;
      wheel.position.set(sx, 0.38, sz);
      g.add(wheel);
    }
  }
  g.traverse((o) => { if (o.isMesh) { o.castShadow = true; o.receiveShadow = true; } });
  g.position.set(x, TERRAIN_Y, z);
  g.rotation.y = rot;
  scene.add(g);
}
tukTuk(-8, 208, 0.2);
tukTuk(9.5, 236, -0.15);
tukTuk(-10, 300, 1.4);

/* river with stone banks */
const river = new THREE.Mesh(new THREE.PlaneGeometry(420, 26), matWater);
river.rotation.x = -Math.PI / 2;
river.position.set(0, TERRAIN_Y - 0.6, 392);
scene.add(river);
for (const side of [-1, 1]) {
  const bank = new THREE.Mesh(boxGeo(420, 1.4, 2.2, 6), matStoneDark);
  bank.position.set(0, TERRAIN_Y - 0.2, 392 + side * 14);
  bank.receiveShadow = true;
  scene.add(bank);
}
for (let i = 0; i < 16; i++) {
  tree(-190 + i * 24, 392 + (i % 2 ? 20 : -20), rand(0.7, 1.1), i % 3 === 0);
}
for (let i = 0; i < 30; i++) {
  tree(rand(-230, 230), rand(182, 430), rand(0.8, 1.4), Math.random() < 0.3);
}

/* distant skyline */
for (let i = 0; i < 40; i++) {
  const h = rand(8, 30);
  const b = new THREE.Mesh(boxGeo(rand(8, 20), h, rand(8, 20), 6),
    new THREE.MeshStandardMaterial({ color: 0x54493c, roughness: 1 }));
  b.position.set(rand(-330, 330), TERRAIN_Y + h / 2, 470 + rand(0, 120));
  scene.add(b);
}

/* extra rim light for the neon strip */
const pubLight = new THREE.PointLight(0xff6b8a, 8, 70, 2);
pubLight.position.set(0, 8, 250);
scene.add(pubLight);

/* dev hook */
window.__dbg = () => ({
  pos: player.pos.toArray().map((v) => +v.toFixed(2)),
  yaw: +player.yaw.toFixed(2), pitch: +player.pitch.toFixed(2),
  onGround: player.onGround, hp: +player.hp.toFixed(1),
  guardians: guardians.length, ammo, wave: game.wave
});

window.__tp = (x, y, z) => { player.pos.set(x, y, z); player.vel.set(0, 0, 0); };
window.__fire = () => shoot();
window.__look = (y, p) => { player.yaw = y; player.pitch = p || 0; };
window.__hurt = (n) => damagePlayer(n || 10, null);
