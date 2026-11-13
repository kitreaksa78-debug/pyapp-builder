const canvas = document.getElementById('poolCanvas');
const ctx = canvas.getContext('2d');
const pocketSelect = document.getElementById('pocketSelect');
const shootBtn = document.getElementById('shootBtn');
const resetBtn = document.getElementById('resetBtn');

const angleVal = document.getElementById('angleVal');
const ghostVal = document.getElementById('ghostVal');
const cutAngleVal = document.getElementById('cutAngleVal');

const TABLE_MARGIN = 35;
const BALL_RADIUS = 14;
const POCKET_RADIUS = 22;

const pockets = [
    { x: TABLE_MARGIN, y: TABLE_MARGIN },
    { x: canvas.width / 2, y: TABLE_MARGIN - 4 },
    { x: canvas.width - TABLE_MARGIN, y: TABLE_MARGIN },
    { x: TABLE_MARGIN, y: canvas.height - TABLE_MARGIN },
    { x: canvas.width / 2, y: canvas.height - TABLE_MARGIN + 4 },
    { x: canvas.width - TABLE_MARGIN, y: canvas.height - TABLE_MARGIN }
];

let cueBall = { x: 220, y: 240, vx: 0, vy: 0, color: '#ffffff' };
let objectBall = { x: 520, y: 180, vx: 0, vy: 0, color: '#ef4444' };

let draggingBall = null;
let isSimulating = false;

function resetPositions() {
    isSimulating = false;
    cueBall = { x: 220, y: 240, vx: 0, vy: 0, color: '#ffffff' };
    objectBall = { x: 520, y: 180, vx: 0, vy: 0, color: '#ef4444' };
    draw();
}

function getTargetPocket() {
    const val = pocketSelect.value;
    if (val !== 'auto') {
        return pockets[parseInt(val, 10)];
    }
    let bestPocket = pockets[0];
    let minCutAngle = Infinity;

    for (const p of pockets) {
        const calc = calculateAim(cueBall, objectBall, p, BALL_RADIUS);
        if (calc.cutAngle < minCutAngle) {
            minCutAngle = calc.cutAngle;
            bestPocket = p;
        }
    }
    return bestPocket;
}

function calculateAim(cue, obj, pocket, radius) {
    const dx = pocket.x - obj.x;
    const dy = pocket.y - obj.y;
    const distToPocket = Math.hypot(dx, dy);

    const ux = distToPocket === 0 ? 0 : dx / distToPocket;
    const uy = distToPocket === 0 ? 0 : dy / distToPocket;

    const ghostX = obj.x - ux * (radius * 2);
    const ghostY = obj.y - uy * (radius * 2);

    const aimDx = ghostX - cue.x;
    const aimDy = ghostY - cue.y;
    const aimDist = Math.hypot(aimDx, aimDy);
    const aimUx = aimDist === 0 ? 0 : aimDx / aimDist;
    const aimUy = aimDist === 0 ? 0 : aimDy / aimDist;

    const angleRad = Math.atan2(aimDy, aimDx);
    const angleDeg = (angleRad * 180) / Math.PI;

    const dot = Math.max(-1, Math.min(1, aimUx * ux + aimUy * uy));
    const cutAngleDeg = (Math.acos(dot) * 180) / Math.PI;

    return {
        ghostX,
        ghostY,
        ux,
        uy,
        aimUx,
        aimUy,
        angleRad,
        angleDeg,
        cutAngle: cutAngleDeg
    };
}

function drawTable() {
    ctx.fillStyle = '#5c3a21';
    ctx.fillRect(0, 0, canvas.width, canvas.height);

    ctx.fillStyle = '#0f6b3a';
    ctx.fillRect(
        TABLE_MARGIN - 10,
        TABLE_MARGIN - 10,
        canvas.width - (TABLE_MARGIN - 10) * 2,
        canvas.height - (TABLE_MARGIN - 10) * 2
    );

    for (const p of pockets) {
        ctx.beginPath();
        ctx.arc(p.x, p.y, POCKET_RADIUS, 0, Math.PI * 2);
        ctx.fillStyle = '#090d16';
        ctx.fill();
        ctx.lineWidth = 2;
        ctx.strokeStyle = '#334155';
        ctx.stroke();
    }
}

function drawBall(x, y, color, label = '') {
    ctx.beginPath();
    ctx.arc(x, y, BALL_RADIUS, 0, Math.PI * 2);
    ctx.fillStyle = color;
    ctx.fill();
    ctx.lineWidth = 1.5;
    ctx.strokeStyle = 'rgba(0,0,0,0.4)';
    ctx.stroke();

    if (label) {
        ctx.fillStyle = '#000';
        ctx.font = 'bold 11px sans-serif';
        ctx.textAlign = 'center';
        ctx.textBaseline = 'middle';
        ctx.fillText(label, x, y);
    }
}

function draw() {
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    drawTable();

    const targetPocket = getTargetPocket();
    const aim = calculateAim(cueBall, objectBall, targetPocket, BALL_RADIUS);

    if (!isSimulating) {
        ctx.beginPath();
        ctx.setLineDash([6, 6]);
        ctx.moveTo(objectBall.x, objectBall.y);
        ctx.lineTo(targetPocket.x, targetPocket.y);
        ctx.strokeStyle = 'rgba(250, 204, 21, 0.8)';
        ctx.lineWidth = 2.5;
        ctx.stroke();

        ctx.beginPath();
        ctx.setLineDash([]);
        ctx.moveTo(cueBall.x, cueBall.y);
        ctx.lineTo(aim.ghostX, aim.ghostY);
        ctx.strokeStyle = 'rgba(255, 255, 255, 0.9)';
        ctx.lineWidth = 2.5;
        ctx.stroke();

        ctx.beginPath();
        ctx.arc(aim.ghostX, aim.ghostY, BALL_RADIUS, 0, Math.PI * 2);
        ctx.strokeStyle = '#38bdf8';
        ctx.lineWidth = 2;
        ctx.setLineDash([4, 3]);
        ctx.stroke();
        ctx.setLineDash([]);

        const tangentX = -aim.uy;
        const tangentY = aim.ux;
        const dotSign = (aim.aimUx * tangentX + aim.aimUy * tangentY) >= 0 ? 1 : -1;
        ctx.beginPath();
        ctx.moveTo(aim.ghostX, aim.ghostY);
        ctx.lineTo(aim.ghostX + tangentX * dotSign * 90, aim.ghostY + tangentY * dotSign * 90);
        ctx.strokeStyle = 'rgba(56, 189, 248, 0.65)';
        ctx.lineWidth = 2;
        ctx.stroke();
    }

    drawBall(objectBall.x, objectBall.y, objectBall.color, '3');
    drawBall(cueBall.x, cueBall.y, cueBall.color);

    angleVal.textContent = `${aim.angleDeg.toFixed(2)}°`;
    ghostVal.textContent = `(${Math.round(aim.ghostX)}, ${Math.round(aim.ghostY)})`;
    cutAngleVal.textContent = `${aim.cutAngle.toFixed(2)}°`;
    cutAngleVal.style.color = aim.cutAngle > 75 ? '#ef4444' : '#38bdf8';
}

function getPointerPos(e) {
    const rect = canvas.getBoundingClientRect();
    const scaleX = canvas.width / rect.width;
    const scaleY = canvas.height / rect.height;
    const clientX = e.touches ? e.touches[0].clientX : e.clientX;
    const clientY = e.touches ? e.touches[0].clientY : e.clientY;
    return {
        x: (clientX - rect.left) * scaleX,
        y: (clientY - rect.top) * scaleY
    };
}

function startDrag(e) {
    if (isSimulating) return;
    const pos = getPointerPos(e);
    if (Math.hypot(pos.x - cueBall.x, pos.y - cueBall.y) <= BALL_RADIUS + 10) {
        draggingBall = cueBall;
    } else if (Math.hypot(pos.x - objectBall.x, pos.y - objectBall.y) <= BALL_RADIUS + 10) {
        draggingBall = objectBall;
    }
}

function moveDrag(e) {
    if (!draggingBall) return;
    if (e.cancelable) e.preventDefault();
    const pos = getPointerPos(e);
    draggingBall.x = Math.max(TABLE_MARGIN + BALL_RADIUS, Math.min(canvas.width - TABLE_MARGIN - BALL_RADIUS, pos.x));
    draggingBall.y = Math.max(TABLE_MARGIN + BALL_RADIUS, Math.min(canvas.height - TABLE_MARGIN - BALL_RADIUS, pos.y));
    draw();
}

function endDrag() {
    draggingBall = null;
}

canvas.addEventListener('mousedown', startDrag);
canvas.addEventListener('mousemove', moveDrag);
window.addEventListener('mouseup', endDrag);

canvas.addEventListener('touchstart', startDrag, { passive: false });
canvas.addEventListener('touchmove', moveDrag, { passive: false });
window.addEventListener('touchend', endDrag);

shootBtn.addEventListener('click', () => {
    if (isSimulating) return;
    const targetPocket = getTargetPocket();
    const aim = calculateAim(cueBall, objectBall, targetPocket, BALL_RADIUS);
    const speed = 12;
    cueBall.vx = aim.aimUx * speed;
    cueBall.vy = aim.aimUy * speed;
    objectBall.vx = 0;
    objectBall.vy = 0;
    isSimulating = true;
    animateShot();
});

function animateShot() {
    if (!isSimulating) return;

    cueBall.x += cueBall.vx;
    cueBall.y += cueBall.vy;
    objectBall.x += objectBall.vx;
    objectBall.y += objectBall.vy;

    cueBall.vx *= 0.985;
    cueBall.vy *= 0.985;
    objectBall.vx *= 0.985;
    objectBall.vy *= 0.985;

    const dx = objectBall.x - cueBall.x;
    const dy = objectBall.y - cueBall.y;
    const dist = Math.hypot(dx, dy);

    if (dist <= BALL_RADIUS * 2 && dist > 0) {
        const nx = dx / dist;
        const ny = dy / dist;

        const overlap = BALL_RADIUS * 2 - dist;
        cueBall.x -= nx * (overlap / 2);
        cueBall.y -= ny * (overlap / 2);
        objectBall.x += nx * (overlap / 2);
        objectBall.y += ny * (overlap / 2);

        const p = cueBall.vx * nx + cueBall.vy * ny - (objectBall.vx * nx + objectBall.vy * ny);
        cueBall.vx -= p * nx;
        cueBall.vy -= p * ny;
        objectBall.vx += p * nx;
        objectBall.vy += p * ny;
    }

    [cueBall, objectBall].forEach(b => {
        if (b.x - BALL_RADIUS < TABLE_MARGIN) { b.x = TABLE_MARGIN + BALL_RADIUS; b.vx *= -0.85; }
        if (b.x + BALL_RADIUS > canvas.width - TABLE_MARGIN) { b.x = canvas.width - TABLE_MARGIN - BALL_RADIUS; b.vx *= -0.85; }
        if (b.y - BALL_RADIUS < TABLE_MARGIN) { b.y = TABLE_MARGIN + BALL_RADIUS; b.vy *= -0.85; }
        if (b.y + BALL_RADIUS > canvas.height - TABLE_MARGIN) { b.y = canvas.height - TABLE_MARGIN - BALL_RADIUS; b.vy *= -0.85; }
    });

    for (const p of pockets) {
        if (Math.hypot(objectBall.x - p.x, objectBall.y - p.y) < POCKET_RADIUS) {
            isSimulating = false;
            setTimeout(resetPositions, 600);
            draw();
            return;
        }
    }

    draw();

    if (Math.hypot(cueBall.vx, cueBall.vy) < 0.1 && Math.hypot(objectBall.vx, objectBall.vy) < 0.1) {
        isSimulating = false;
        draw();
        return;
    }

    requestAnimationFrame(animateShot);
}

pocketSelect.addEventListener('change', draw);
resetBtn.addEventListener('click', resetPositions);

draw();
