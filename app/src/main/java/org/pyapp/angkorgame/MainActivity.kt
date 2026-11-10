package org.pyapp.angkorgame

import android.app.Activity
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/* អង្គរវត្ត: សមរភូមិព្រះអាទិត្យលិច — native OpenGL ES game, fully offline. */

class MainActivity : Activity() {
    private lateinit var gameView: GameView
    private lateinit var hud: TextView
    private lateinit var menu: FrameLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION

        val root = FrameLayout(this)
        gameView = GameView(this)
        root.addView(gameView, FrameLayout.LayoutParams(-1, -1))
        root.addView(StickOverlay(this, gameView), FrameLayout.LayoutParams(-1, -1))

        hud = TextView(this)
        hud.setTextColor(0xFFFFE9C0.toInt())
        hud.textSize = 14f
        hud.setPadding(28, 22, 28, 22)
        val hudLp = FrameLayout.LayoutParams(-2, -2)
        hudLp.gravity = Gravity.TOP or Gravity.START
        root.addView(hud, hudLp)

        menu = buildMenu()
        root.addView(menu, FrameLayout.LayoutParams(-1, -1))

        val fire = makeButton("បាញ់", 0xFFC0392B.toInt())
        val jump = makeButton("លោត", 0xFFB8862F.toInt())
        val rel = makeButton("⟳", 0xFF8A6A3A.toInt())
        val fireLp = FrameLayout.LayoutParams(260, 260)
        fireLp.gravity = Gravity.BOTTOM or Gravity.END
        fireLp.rightMargin = 40
        fireLp.bottomMargin = 60
        root.addView(fire, fireLp)
        val jumpLp = FrameLayout.LayoutParams(150, 150)
        jumpLp.gravity = Gravity.BOTTOM or Gravity.END
        jumpLp.rightMargin = 70
        jumpLp.bottomMargin = 340
        root.addView(jump, jumpLp)
        val relLp = FrameLayout.LayoutParams(150, 150)
        relLp.gravity = Gravity.BOTTOM or Gravity.END
        relLp.rightMargin = 320
        relLp.bottomMargin = 90
        root.addView(rel, relLp)

        fire.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { v.alpha = 0.7f; gameView.fireDown(); true }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { v.alpha = 1f; gameView.fireUp(); true }
                else -> false
            }
        }
        jump.setOnClickListener { gameView.jump() }
        rel.setOnClickListener { gameView.reloadGun() }

        restart = makeButton("លេងម្ដងទៀត", 0xFFC9A227.toInt())
        val rLp = FrameLayout.LayoutParams(420, 150)
        rLp.gravity = Gravity.CENTER
        restart!!.visibility = View.GONE
        root.addView(restart, rLp)
        restart!!.setOnClickListener {
            restart!!.visibility = View.GONE
            gameView.startGame()
        }

        setContentView(root)
        handler.postDelayed(pollHud, 250)
    }

    private fun makeButton(label: String, color: Int): Button {
        val b = Button(this)
        b.text = label
        b.textSize = 20f
        b.setTextColor(0xFF20140A.toInt())
        val bg = android.graphics.drawable.GradientDrawable()
        bg.shape = android.graphics.drawable.GradientDrawable.OVAL
        bg.setColor(color)
        bg.setStroke(3, 0x66FFE8B0)
        b.background = bg
        return b
    }

    private val handler = Handler(Looper.getMainLooper())
    private var restart: Button? = null
    private val pollHud = object : Runnable {
        override fun run() {
            hud.text = gameView.hud()
            if (gameView.isBroken()) {
                hud.visibility = View.GONE
                if (restart != null && restart!!.visibility != View.VISIBLE) restart!!.visibility = View.VISIBLE
            } else {
                hud.visibility = View.VISIBLE
            }
            handler.postDelayed(this, 200)
        }
    }

    private fun buildMenu(): FrameLayout {
        val box = FrameLayout(this)
        box.setBackgroundColor(0xE00B0703.toInt())
        val title = TextView(this)
        title.text = "អង្គរវត្ត 3D\nសមរភូមិព្រះអាទិត្យលិច"
        title.setTextColor(0xFFFFE9C0.toInt())
        title.textSize = 30f
        title.gravity = Gravity.CENTER
        val tLp = FrameLayout.LayoutParams(-1, -2)
        tLp.gravity = Gravity.CENTER
        tLp.bottomMargin = 260
        box.addView(title, tLp)

        val info = TextView(this)
        info.text = "ជីវិត • ពិន្ទុ • រលកយក្ស\nដើរ: joystick ឆ្វេង | មើល: អូសអេក្រង់\nបាញ់: ប៊ូតុងក្រហម | លោត: ព្រួញ"
        info.setTextColor(0xFFCDB894.toInt())
        info.textSize = 14f
        info.gravity = Gravity.CENTER
        val iLp = FrameLayout.LayoutParams(-1, -2)
        iLp.gravity = Gravity.CENTER
        iLp.bottomMargin = 120
        box.addView(info, iLp)

        val start = Button(this)
        start.text = "ចាប់ផ្តើមបាញ់"
        start.textSize = 20f
        val sLp = FrameLayout.LayoutParams(560, 150)
        sLp.gravity = Gravity.CENTER
        box.addView(start, sLp)
        start.setOnClickListener {
            menu.visibility = View.GONE
            gameView.startGame()
        }
        return box
    }

    override fun onResume() {
        super.onResume()
        gameView.onResume()
    }

    override fun onPause() {
        super.onPause()
        gameView.onPause()
    }
}

class GameView(context: android.content.Context) : GLSurfaceView(context) {
    private val renderer = GameRenderer()
    private var stickId = -1
    private var stickCx = 0f
    private var stickCy = 0f
    private var stickR = 140f
    @Volatile var knobDx = 0f
    @Volatile var knobDy = 0f

    fun stickBaseX(w: Float) = w * 0.20f
    fun stickBaseY(w: Float, h: Float) = h - w * 0.20f

    private var lookId = -1
    private var lookX = 0f
    private var lookY = 0f

    init {
        setEGLContextClientVersion(2)
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
        isFocusable = true
        isFocusableInTouchMode = true
    }

    fun startGame() {
        renderer.start()
        requestFocus()
    }

    fun fireDown() = renderer.fireDown()
    fun fireUp() = renderer.fireUp()
    fun jump() = renderer.jump()
    fun reloadGun() = renderer.startReload()
    fun hud(): String = renderer.hudText()
    fun isBroken(): Boolean = renderer.brokenNow

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val w = width.toFloat()
        val h = height.toFloat()
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = event.actionIndex
                val x = event.getX(i)
                val y = event.getY(i)
                if (x < w * 0.45f && y > h * 0.40f && stickId < 0) {
                    stickId = event.getPointerId(i)
                    stickR = w * 0.135f
                    stickCx = stickBaseX(w)
                    stickCy = stickBaseY(w, h)
                    knobDx = 0f
                    knobDy = 0f
                    renderer.moveX = 0f
                    renderer.moveY = 0f
                } else if (lookId < 0) {
                    lookId = event.getPointerId(i)
                    lookX = x
                    lookY = y
                }
            }
            MotionEvent.ACTION_MOVE -> {
                for (k in 0 until event.pointerCount) {
                    val id = event.getPointerId(k)
                    if (id == stickId) {
                        val dx = event.getX(k) - stickCx
                        val dy = event.getY(k) - stickCy
                        val len = hypot(dx, dy)
                        val maxR = stickR
                        val s = if (len > maxR) maxR / len else 1f
                        knobDx = dx * s
                        knobDy = dy * s
                        renderer.moveX = dx * s / maxR
                        renderer.moveY = dy * s / maxR
                    } else if (id == lookId) {
                        renderer.look(event.getX(k) - lookX, event.getY(k) - lookY)
                        lookX = event.getX(k)
                        lookY = event.getY(k)
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                val i = event.actionIndex
                val id = event.getPointerId(i)
                if (id == stickId) {
                    stickId = -1
                    knobDx = 0f
                    knobDy = 0f
                    renderer.moveX = 0f
                    renderer.moveY = 0f
                }
                if (id == lookId) lookId = -1
            }
        }
        return true
    }
}

/* ---------------------------------------------------------------- meshes */
class Mesh {
    val v = ArrayList<Float>(4096)

    fun addQuad(a: FloatArray, b: FloatArray, c: FloatArray, d: FloatArray, col: FloatArray) {
        val ux = b[0] - a[0]; val uy = b[1] - a[1]; val uz = b[2] - a[2]
        val wx = c[0] - a[0]; val wy = c[1] - a[1]; val wz = c[2] - a[2]
        var nx = uy * wz - uz * wy
        var ny = uz * wx - ux * wz
        var nz = ux * wy - uy * wx
        val nl = sqrt(nx * nx + ny * ny + nz * nz)
        if (nl < 1e-5f) return
        nx /= nl; ny /= nl; nz /= nl
        push(a, nx, ny, nz, col); push(b, nx, ny, nz, col); push(c, nx, ny, nz, col)
        push(a, nx, ny, nz, col); push(c, nx, ny, nz, col); push(d, nx, ny, nz, col)
    }

    private fun push(p: FloatArray, nx: Float, ny: Float, nz: Float, col: FloatArray) {
        v.add(p[0]); v.add(p[1]); v.add(p[2])
        v.add(nx); v.add(ny); v.add(nz)
        v.add(col[0]); v.add(col[1]); v.add(col[2]); v.add(col[3])
    }

    fun addBox(cx: Float, cy: Float, cz: Float, sx: Float, sy: Float, sz: Float,
               r: Float, g: Float, b: Float, a: Float = 1f) {
        val x0 = cx - sx / 2; val x1 = cx + sx / 2
        val y0 = cy - sy / 2; val y1 = cy + sy / 2
        val z0 = cz - sz / 2; val z1 = cz + sz / 2
        val c = floatArrayOf(r, g, b, a)
        addQuad(floatArrayOf(x0, y1, z0), floatArrayOf(x0, y1, z1), floatArrayOf(x1, y1, z1), floatArrayOf(x1, y1, z0), c)
        addQuad(floatArrayOf(x0, y0, z1), floatArrayOf(x0, y0, z0), floatArrayOf(x1, y0, z0), floatArrayOf(x1, y0, z1), c)
        addQuad(floatArrayOf(x0, y0, z0), floatArrayOf(x0, y1, z0), floatArrayOf(x1, y1, z0), floatArrayOf(x1, y0, z0), c)
        addQuad(floatArrayOf(x1, y0, z1), floatArrayOf(x1, y1, z1), floatArrayOf(x0, y1, z1), floatArrayOf(x0, y0, z1), c)
        addQuad(floatArrayOf(x0, y0, z1), floatArrayOf(x0, y1, z1), floatArrayOf(x0, y1, z0), floatArrayOf(x0, y0, z0), c)
        addQuad(floatArrayOf(x1, y0, z0), floatArrayOf(x1, y1, z0), floatArrayOf(x1, y1, z1), floatArrayOf(x1, y0, z1), c)
    }

    fun addPyramid(cx: Float, cy: Float, cz: Float, base: Float, h: Float, r: Float, g: Float, b: Float) {
        val c = floatArrayOf(r, g, b, 1f)
        val top = floatArrayOf(cx, cy + h, cz)
        val p0 = floatArrayOf(cx - base, cy, cz - base)
        val p1 = floatArrayOf(cx + base, cy, cz - base)
        val p2 = floatArrayOf(cx + base, cy, cz + base)
        val p3 = floatArrayOf(cx - base, cy, cz + base)
        addQuad(p0, p1, p2, p3, c)
        addQuad(p0, top, p1, p1, c)
        addQuad(p1, top, p2, p2, c)
        addQuad(p2, top, p3, p3, c)
        addQuad(p3, top, p0, p0, c)
    }

    fun buffer(): FloatBuffer {
        val bb = ByteBuffer.allocateDirect(v.size * 4).order(ByteOrder.nativeOrder())
        val fb = bb.asFloatBuffer()
        for (i in v.indices) fb.put(v[i])
        fb.position(0)
        return fb
    }
}

/* ---------------------------------------------------------------- world */
class World {
    val collision = ArrayList<FloatArray>()
    val mesh = Mesh()

    private fun collider(x: Float, z: Float, hw: Float, hd: Float) {
        collision.add(floatArrayOf(x - hw, z - hd, x + hw, z + hd))
    }
    fun solid(x: Float, z: Float, hw: Float, hd: Float) {
        collider(x, z, hw, hd)
    }

    fun build() {
        val rand = Random(7)
        // sand ground with patches
        val n = 26
        val span = 300f
        val step = span / n
        for (i in 0 until n) {
            for (j in 0 until n) {
                val x0 = -span / 2 + i * step
                val z0 = -span / 2 + j * step
                val t = rand.nextFloat()
                val gr = 0.60f + t * 0.12f
                val gg = 0.50f + t * 0.14f
                val gb = 0.28f + t * 0.08f
                val a = floatArrayOf(x0, 0f, z0)
                val b = floatArrayOf(x0, 0f, z0 + step)
                val c = floatArrayOf(x0 + step, 0f, z0 + step)
                val d = floatArrayOf(x0 + step, 0f, z0)
                mesh.addQuad(a, b, c, d, floatArrayOf(gr, gg, gb, 1f))
            }
        }
        // roads (Siem Reap avenue + cross street)
        road(0f, 120f, 16f, 150f, 0.30f, 0.28f, 0.29f)
        road(0f, 200f, 190f, 14f, 0.30f, 0.28f, 0.29f)
        road(0f, 300f, 420f, 18f, 0.28f, 0.26f, 0.27f)
        // moat ring
        moat(74f, 1.0f, 0.18f, 0.30f, 0.31f)
        // temple terraces
        terrace()
        // causeway
        mesh.addBox(0f, 0.28f, 96f, 9f, 0.55f, 60f, 0.62f, 0.53f, 0.36f)
        collider(0f, 96f, 4.5f, 30f)
        // towers
        tower(0f, 0f, 7.4f, 7.6f, 26f)
        tower(-14f, -14f, 7.4f, 5.4f, 16f)
        tower(14f, -14f, 7.4f, 5.4f, 16f)
        tower(-14f, 14f, 7.4f, 5.4f, 16f)
        tower(14f, 14f, 7.4f, 5.4f, 16f)
        // galleries
        gallery(-34f, 0f, 40f, true)
        gallery(34f, 0f, 40f, true)
        gallery(0f, -34f, 40f, false)
        gallery(0f, 34f, 40f, false)
        // city blocks
        for (side in intArrayOf(-1, 1)) {
            var z = 130f
            while (z < 290f) {
                val w = 9f + rand.nextFloat() * 8f
                val d = 10f + rand.nextFloat() * 9f
                val h = 7f + rand.nextFloat() * 13f
                val warm = 0.42f + rand.nextFloat() * 0.18f
                mesh.addBox(side * (22f + w / 2), h / 2, z, w, h, d, warm, warm * 0.82f, warm * 0.62f)
                mesh.addBox(side * (22f + w / 2), h + 0.3f, z, w + 0.6f, 0.6f, d + 0.6f, 0.22f, 0.19f, 0.16f)
                collider(side * (22f + w / 2), z, w / 2, d / 2)
                z += d + 4f
            }
        }
        // jungle trees
        var placed = 0
        while (placed < 120) {
            val a = rand.nextFloat() * 6.283f
            val rr = 60f + rand.nextFloat() * 130f
            val x = cos(a) * rr
            val z = sin(a) * rr
            val s = 0.8f + rand.nextFloat() * 0.7f
            mesh.addBox(x, 3f * s, z, 0.5f * s, 6f * s, 0.5f * s, 0.20f, 0.15f, 0.10f)
            mesh.addBox(x, 7f * s, z, 4.6f * s, 3.4f * s, 4.2f * s, 0.16f, 0.31f, 0.13f)
            mesh.addBox(x + 1.4f * s, 9f * s, z - 1.2f * s, 3.2f * s, 2.4f * s, 3.0f * s, 0.13f, 0.27f, 0.11f)
            placed++
        }
        // distant hills
        for (k in 0 until 9) {
            val a = rand.nextFloat() * 6.283f
            val rr = 240f + rand.nextFloat() * 90f
            mesh.addPyramid(cos(a) * rr, -1f, sin(a) * rr, 60f + rand.nextFloat() * 50f, 40f + rand.nextFloat() * 40f,
                0.30f, 0.33f, 0.22f)
        }
    }

    private fun road(cx: Float, cz: Float, w: Float, d: Float, r: Float, g: Float, b: Float) {
        val c = floatArrayOf(r, g, b, 1f)
        mesh.addQuad(floatArrayOf(cx - w / 2, 0.03f, cz - d / 2), floatArrayOf(cx - w / 2, 0.03f, cz + d / 2),
            floatArrayOf(cx + w / 2, 0.03f, cz + d / 2), floatArrayOf(cx + w / 2, 0.03f, cz - d / 2), c)
    }

    private fun moat(outer: Float, inner: Float, r: Float, g: Float, b: Float) {
        val c = floatArrayOf(r, g, b, 1f)
        val y = 0.02f
        val o = outer; val i = inner
        mesh.addQuad(floatArrayOf(-o, y, -o), floatArrayOf(-o, y, -i), floatArrayOf(o, y, -i), floatArrayOf(o, y, -o), c)
        mesh.addQuad(floatArrayOf(-o, y, i), floatArrayOf(-o, y, o), floatArrayOf(o, y, o), floatArrayOf(o, y, i), c)
        mesh.addQuad(floatArrayOf(-o, y, -i), floatArrayOf(-o, y, i), floatArrayOf(-i, y, i), floatArrayOf(-i, y, -i), c)
        mesh.addQuad(floatArrayOf(i, y, -i), floatArrayOf(i, y, i), floatArrayOf(o, y, i), floatArrayOf(o, y, -i), c)
    }

    private fun terrace() {
        mesh.addBox(0f, 0.8f, 0f, 88f, 1.6f, 88f, 0.66f, 0.57f, 0.40f)
        mesh.addBox(0f, 2.6f, 0f, 66f, 2.0f, 66f, 0.69f, 0.59f, 0.42f)
        mesh.addBox(0f, 4.8f, 0f, 44f, 2.4f, 44f, 0.72f, 0.62f, 0.45f)
        collider(0f, 0f, 44f, 44f)
        // stairs at the south face
        for (i in 0 until 5) {
            mesh.addBox(0f, 0.16f + i * 0.32f, 44f + (4 - i) * 1.2f, 8f, 0.32f * (i + 1) * 2f, 1.2f, 0.75f, 0.65f, 0.47f)
        }
    }

    private fun tower(x: Float, z: Float, base: Float, size: Float, h: Float) {
        var y = base
        for (i in 0 until 5) {
            val w = size * (1f - i * 0.14f)
            val th = h / 5f * (1f - i * 0.05f)
            mesh.addBox(x, y + th / 2, z, w, th, w, 0.74f, 0.64f, 0.46f)
            mesh.addBox(x, y + th, z, w + 0.7f, 0.3f, w + 0.7f, 0.80f, 0.70f, 0.50f)
            y += th
        }
        mesh.addBox(x, y + 0.5f, z, size * 0.3f, 1.0f, size * 0.3f, 0.85f, 0.70f, 0.35f)
        mesh.addPyramid(x, y + 1f, z, size * 0.22f, size * 0.7f, 0.86f, 0.68f, 0.26f)
        collider(x, z, size / 2, size / 2)
    }

    private fun gallery(x: Float, z: Float, len: Float, alongZ: Boolean) {
        val w = 7f
        val sx = if (alongZ) w else len
        val sz = if (alongZ) len else w
        mesh.addBox(x, 1.9f, z, sx, 3.4f, sz, 0.70f, 0.60f, 0.43f)
        mesh.addBox(x, 3.9f, z, sx + 1.6f, 0.8f, sz + 1.6f, 0.62f, 0.53f, 0.38f)
        collider(x, z, sx / 2, sz / 2)
    }
}

/* ---------------------------------------------------------------- renderer */
class GameRenderer : GLSurfaceView.Renderer {
    @Volatile var moveX = 0f
    @Volatile var moveY = 0f

    private val world = World()
    private var worldBuf: FloatBuffer? = null
    private var worldCount = 0

    private var prog = 0
    private var aPos = 0; private var aNorm = 0; private var aCol = 0
    private var uMVP = 0; private var uMV = 0; private var uFog = 0

    private var skyProg = 0
    private var skyPos = 0; private var skyTop = 0; private var skyBot = 0

    private val proj = FloatArray(16)
    private val view = FloatArray(16)
    private val mvp = FloatArray(16)
    private val mv = FloatArray(16)
    private val eye = FloatArray(3)

    // player
    private var px = 0f; private var pz = 120f; private var jumpY = 0f; private var vy = 0f
    private var yaw = 0f; private var pitch = 0f
    private var hp = 100f; private var ammo = 30; private var score = 0; private var wave = 0
    private var alive = 0
    private var reload = 0f; private var fireCd = 0f; private var kick = 0f
    private var lastHurt = -99f
    private var spawnTimer = 0f
    private var broken = false

    @Volatile var running = false
    @Volatile private var hudCache = ""

    private class Enemy(var x: Float, var z: Float, var hp: Float, var speed: Float, var dmg: Float) {
        var alive = true
        var cd = 0f
        var phase = 0f
    }

    private val enemies = ArrayList<Enemy>()

    fun start() {
        if (running) return
        enemies.clear()
        px = 0f; pz = 120f; jumpY = 0f; vy = 0f
        yaw = 0f; pitch = 0f
        hp = 100f; ammo = 30; score = 0; wave = 0; alive = 0
        reload = 0f; fireCd = 0f; kick = 0f; broken = false; spawnTimer = 0f
        nextWave()
        running = true
    }

    fun hudText(): String {
        if (!running && !broken) return ""
        if (broken) return "អ្នកបានដួល…\nពិន្ទុ $score • រលក $wave\nចុចលើអេក្រង់ដើម្បីលេងម្ដងទៀត"
        return "ជីវិត ${hp.toInt()}   គ្រាប់ $ammo   ពិន្ទុ $score   រលក $wave\nយក្សនៅសល់ $alive"
    }

    fun look(dx: Float, dy: Float) {
        yaw -= dx * 0.0035f
        pitch = min(1.2f, max(-1.2f, pitch - dy * 0.0035f))
    }

    val brokenNow: Boolean get() = broken

    fun startReload() { if (ammo < 30 && reload <= 0f) reload = 1.4f }

    fun fireDown() { shoot() }
    fun fireUp() {}
    fun jump() { if (jumpY <= 0.01f) vy = 6.2f }

    private fun nextWave() {
        wave++
        val count = min(14, 4 + wave * 2)
        val rand = Random(wave * 31 + 5)
        for (i in 0 until count) {
            val a = rand.nextFloat() * 6.283f
            val r = 18f + rand.nextFloat() * 42f
            enemies.add(Enemy(cos(a) * r, sin(a) * r, 70f + wave * 24f,
                2.1f + min(2.0f, wave * 0.16f), 7f + wave * 1.4f))
        }
        alive = count
    }

    private fun shoot() {
        if (!running || reload > 0f) return
        if (ammo <= 0) { reload = 1.4f; return }
        ammo--
        kick = 1f
        val cy = cos(pitch); val sy = sin(pitch)
        val fx = -sin(yaw) * cy
        val fy = sy
        val fz = -cos(yaw) * cy
        val ox = px; val oy = 1.62f + jumpY; val oz = pz
        var best = 1e9f
        var target: Enemy? = null
        for (e in enemies) {
            if (!e.alive) continue
            val ex = e.x - ox; val ey = 1.0f - oy; val ez = e.z - oz
            val t = ex * fx + ey * fy + ez * fz
            if (t <= 0f || t > 90f) continue
            val d2 = ex * ex + ey * ey + ez * ez - t * t
            if (d2 < 0.95f && t < best) { best = t; target = e }
        }
        if (target != null) {
            target.hp -= 42f
            if (target.hp <= 0f) {
                target.alive = false
                score += 100
                alive = max(0, alive - 1)
                if (alive == 0) spawnTimer = 2.6f
            }
        }
    }

    private fun collide() {
        val r = 0.55f
        for (b in world.collision) {
            if (px > b[0] - r && px < b[2] + r && pz > b[1] - r && pz < b[3] + r) {
                val l = px - (b[0] - r); val rr = (b[2] + r) - px
                val bk = pz - (b[1] - r); val fr = (b[3] + r) - pz
                val m = min(min(l, rr), min(bk, fr))
                if (m == l) px = b[0] - r
                else if (m == rr) px = b[2] + r
                else if (m == bk) pz = b[1] - r
                else pz = b[3] + r
            }
        }
    }

    private fun update(dt: Float) {
        if (broken) return
        val f = -moveY
        val s = moveX
        val sp = 7.0f
        val fx = -sin(yaw); val fz = -cos(yaw)
        val rx = cos(yaw); val rz = -sin(yaw)
        px += (fx * f + rx * s) * sp * dt
        pz += (fz * f + rz * s) * sp * dt
        collide()

        vy -= 18f * dt
        jumpY += vy * dt
        if (jumpY <= 0f) { jumpY = 0f; vy = 0f }

        if (reload > 0f) {
            reload -= dt
            if (reload <= 0f) { ammo = 30; reload = 0f }
        }
        kick = max(0f, kick - dt * 6f)

        for (e in enemies) {
            if (!e.alive) continue
            val dx = px - e.x; val dz = pz - e.z
            val dist = hypot(dx, dz)
            e.phase += dt * 6f
            if (dist > 2.2f) {
                e.x += dx / dist * e.speed * dt
                e.z += dz / dist * e.speed * dt
            }
            e.cd -= dt
            if (dist < 2.6f && e.cd <= 0f) {
                e.cd = 1.2f
                hp -= e.dmg
                lastHurt = wave.toFloat()
                if (hp <= 0f) {
                    hp = 0f
                    running = false
                    broken = true
                    break
                }
            }
        }
        if (hp < 100f) hp += dt * 3.2f
        if (hp > 100f) hp = 100f

        if (alive == 0 && running) {
            spawnTimer -= dt
            if (spawnTimer <= 0f) nextWave()
        }
        hudCache = hudText()
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.86f, 0.66f, 0.42f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDisable(GLES20.GL_CULL_FACE)

        prog = buildProgram(VS, FS)
        aPos = GLES20.glGetAttribLocation(prog, "aPos")
        aNorm = GLES20.glGetAttribLocation(prog, "aNormal")
        aCol = GLES20.glGetAttribLocation(prog, "aColor")
        uMVP = GLES20.glGetUniformLocation(prog, "uMVP")
        uMV = GLES20.glGetUniformLocation(prog, "uMV")
        uFog = GLES20.glGetUniformLocation(prog, "uFog")

        skyProg = buildProgram(SKY_VS, SKY_FS)
        skyPos = GLES20.glGetAttribLocation(skyProg, "aPos")
        skyTop = GLES20.glGetUniformLocation(skyProg, "uTop")
        skyBot = GLES20.glGetUniformLocation(skyProg, "uBot")

        world.mesh.v.clear()
        world.collision.clear()
        world.build()
        worldBuf = world.mesh.buffer()
        worldCount = world.mesh.v.size / 10
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val aspect = width.toFloat() / height.toFloat()
        Matrix.perspectiveM(proj, 0, 74f, aspect, 0.1f, 500f)
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        if (lastFrame == 0L) lastFrame = now
        var dt = (now - lastFrame) / 1e9f
        lastFrame = now
        if (dt > 0.05f) dt = 0.05f
        if (running) update(dt)

        val cy = cos(pitch); val sy = sin(pitch)
        val dirx = -sin(yaw) * cy
        val diry = sy
        val dirz = -cos(yaw) * cy
        eye[0] = px; eye[1] = 1.62f + jumpY; eye[2] = pz
        Matrix.setLookAtM(view, 0, eye[0], eye[1], eye[2],
            eye[0] + dirx, eye[1] + diry, eye[2] + dirz, 0f, 1f, 0f)
        Matrix.multiplyMM(mv, 0, view, 0, identity, 0)
        Matrix.multiplyMM(mvp, 0, proj, 0, view, 0)

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        // sky
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glUseProgram(skyProg)
        GLES20.glUniform3f(skyTop, 0.20f, 0.32f, 0.52f)
        GLES20.glUniform3f(skyBot, 0.95f, 0.62f, 0.34f)
        GLES20.glVertexAttribPointer(skyPos, 2, GLES20.GL_FLOAT, false, 8, skyQuad)
        GLES20.glEnableVertexAttribArray(skyPos)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(skyPos)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)

        GLES20.glUseProgram(prog)
        GLES20.glUniform3f(uFog, 0.93f, 0.66f, 0.40f)
        GLES20.glUniformMatrix4fv(uMVP, 1, false, mvp, 0)
        GLES20.glUniformMatrix4fv(uMV, 1, false, view, 0)
        drawBuffer(worldBuf, worldCount)

        // dynamic
        val dm = Mesh()
        for (e in enemies) {
            if (!e.alive) continue
            val bob = abs(sin(e.phase)) * 0.12f
            dm.addBox(e.x, 1.15f + bob, e.z, 1.05f, 1.25f, 0.68f, 0.55f, 0.50f, 0.40f)
            dm.addBox(e.x, 1.9f + bob, e.z, 1.2f, 0.28f, 0.74f, 0.82f, 0.64f, 0.24f)
            dm.addBox(e.x, 2.22f + bob, e.z, 0.62f, 0.62f, 0.6f, 0.66f, 0.58f, 0.45f)
            dm.addBox(e.x - 0.72f, 1.15f + bob, e.z, 0.26f, 1.0f, 0.3f, 0.50f, 0.45f, 0.36f)
            dm.addBox(e.x + 0.72f, 1.15f + bob, e.z, 0.26f, 1.0f, 0.3f, 0.50f, 0.45f, 0.36f)
            dm.addBox(e.x, 2.22f + bob, e.z - 0.32f, 0.16f, 0.09f, 0.06f, 1f, 0.25f, 0.10f)
            dm.addBox(e.x, 0.30f + bob, e.z, 0.9f, 0.6f, 0.6f, 0.42f, 0.38f, 0.30f)
        }
        drawBuffer(dm.buffer(), dm.v.size / 10)

        val hm = Mesh()
        if (!broken) {
            for (e in enemies) {
                if (!e.alive) continue
                val dx = e.x - px; val dz = e.z - pz
                if (hypot(dx, dz) > 26f) continue
                hm.addBox(e.x, 2.95f, e.z, 0.9f, 0.1f, 0.1f, 0.35f, 0.06f, 0.04f)
                val frac = max(0f, min(1f, e.hp / (70f + wave * 24f)))
                hm.addBox(e.x - 0.45f + 0.45f * frac, 2.95f, e.z, 0.9f * frac, 0.1f, 0.11f, 0.95f, 0.35f, 0.15f)
            }
        }
        drawBuffer(hm.buffer(), hm.v.size / 10)

        // gun in camera space, drawn last so it never clips into walls
        val gm = Mesh()
        val gx = 0.30f - kick * 0.05f
        val gy = -0.26f + kick * 0.02f
        val gz = -0.62f + kick * 0.12f
        gm.addBox(gx, gy, gz, 0.11f, 0.14f, 0.54f, 0.22f, 0.23f, 0.26f)
        gm.addBox(gx, gy + 0.01f, gz - 0.35f, 0.07f, 0.08f, 0.42f, 0.15f, 0.16f, 0.18f)
        gm.addBox(gx, gy - 0.14f, gz + 0.12f, 0.09f, 0.22f, 0.11f, 0.38f, 0.24f, 0.13f)
        drawBuffer(gm.buffer(), gm.v.size / 10, true)
    }

    private fun drawBuffer(buf: FloatBuffer?, count: Int, viewSpaceGun: Boolean = false) {
        if (buf == null || count <= 0) return
        val stride = 10 * 4
        GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, stride, buf)
        GLES20.glVertexAttribPointer(aNorm, 3, GLES20.GL_FLOAT, false, stride, bufAt(buf, 3))
        GLES20.glVertexAttribPointer(aCol, 4, GLES20.GL_FLOAT, false, stride, bufAt(buf, 6))
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glEnableVertexAttribArray(aNorm)
        GLES20.glEnableVertexAttribArray(aCol)
        if (viewSpaceGun) {
            GLES20.glUniformMatrix4fv(uMVP, 1, false, proj, 0)
            GLES20.glUniformMatrix4fv(uMV, 1, false, identity, 0)
            GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT)
        }
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, count)
        if (viewSpaceGun) {
            GLES20.glUniformMatrix4fv(uMVP, 1, false, mvp, 0)
            GLES20.glUniformMatrix4fv(uMV, 1, false, view, 0)
        }
    }

    private fun bufAt(buf: FloatBuffer, offset: Int): FloatBuffer {
        buf.position(offset)
        return buf
    }

    private var lastFrame = 0L
    private val identity = FloatArray(16).also { Matrix.setIdentityM(it, 0) }
    private val gunMvp = FloatArray(16)
    private val projView2 = FloatArray(16)
    private val skyQuad: FloatBuffer = run {
        val bb = ByteBuffer.allocateDirect(8 * 4).order(ByteOrder.nativeOrder())
        val fb = bb.asFloatBuffer()
        fb.put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
        fb.position(0)
        fb
    }

    companion object {
        private const val VS = """
attribute vec3 aPos; attribute vec3 aNormal; attribute vec4 aColor;
uniform mat4 uMVP; uniform mat4 uMV;
varying vec4 vColor; varying float vDepth;
void main(){
  vec4 vp = uMV * vec4(aPos, 1.0);
  vDepth = length(vp.xyz);
  float sh = 0.45 + 0.55 * max(dot(normalize(aNormal), normalize(vec3(-0.45, 0.82, 0.36))), 0.0);
  vColor = vec4(aColor.rgb * sh, aColor.a);
  gl_Position = uMVP * vec4(aPos, 1.0);
}"""
        private const val FS = """
precision mediump float;
uniform vec3 uFog;
varying vec4 vColor; varying float vDepth;
void main(){
  float f = clamp(1.0 - exp(-vDepth * 0.006), 0.0, 1.0);
  gl_FragColor = vec4(mix(vColor.rgb, uFog, f * 0.85), 1.0);
}"""
        private const val SKY_VS = """
attribute vec2 aPos;
varying vec2 vP;
void main(){ vP = aPos; gl_Position = vec4(aPos, 0.999, 1.0); }"""
        private const val SKY_FS = """
precision mediump float;
uniform vec3 uTop; uniform vec3 uBot;
varying vec2 vP;
void main(){
  float t = clamp(vP.y * 0.5 + 0.5, 0.0, 1.0);
  vec3 c = mix(uBot, uTop, pow(t, 0.85));
  gl_FragColor = vec4(c, 1.0);
}"""

        private fun buildProgram(vs: String, fs: String): Int {
            val v = compile(GLES20.GL_VERTEX_SHADER, vs)
            val f = compile(GLES20.GL_FRAGMENT_SHADER, fs)
            val p = GLES20.glCreateProgram()
            GLES20.glAttachShader(p, v)
            GLES20.glAttachShader(p, f)
            GLES20.glLinkProgram(p)
            return p
        }

        private fun compile(type: Int, src: String): Int {
            val s = GLES20.glCreateShader(type)
            GLES20.glShaderSource(s, src)
            GLES20.glCompileShader(s)
            return s
        }
    }
}

/* Joystick "ដើរ" ដែលមើលឃើញច្បាស់នៅជ្រុងខាងឆ្វេង (គូរពីលើហ្គេម) */
class StickOverlay(context: android.content.Context, private val gv: GameView) : View(context) {
    private val fill = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.FILL
        color = 0x40FFE8B0
    }
    private val ring = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 5f
        color = 0xB3FFE8B0.toInt()
    }
    private val knob = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.FILL
        color = 0xD9D9A441.toInt()
    }
    private val label = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFE9C0.toInt()
        textAlign = android.graphics.Paint.Align.CENTER
        isFakeBoldText = true
    }

    init {
        isClickable = false
        isFocusable = false
        setWillNotDraw(false)
    }

    override fun onDraw(canvas: android.graphics.Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w > 0f && h > 0f) {
            val r = w * 0.135f
            val cx = gv.stickBaseX(w)
            val cy = gv.stickBaseY(w, h)
            canvas.drawCircle(cx, cy, r, fill)
            canvas.drawCircle(cx, cy, r, ring)
            canvas.drawCircle(cx, cy, r * 0.44f, ring)
            val kx = cx + gv.knobDx
            val ky = cy + gv.knobDy
            canvas.drawCircle(kx, ky, r * 0.40f, knob)
            canvas.drawCircle(kx, ky, r * 0.40f, ring)
            label.textSize = w * 0.042f
            canvas.drawText("ដើរ", cx, cy + r + w * 0.072f, label)
        }
        postInvalidateDelayed(33)
    }
}
