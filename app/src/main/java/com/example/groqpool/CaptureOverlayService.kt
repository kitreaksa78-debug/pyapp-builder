package com.example.groqpool

import android.app.*
import android.content.*
import android.graphics.*
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.view.*
import android.widget.TextView
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import android.util.Base64
import kotlin.math.*

class CaptureOverlayService : Service() {
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var overlay: GuideView? = null
    private var window: WindowManager? = null
    private var lastSend = 0L
    private var backend = ""
    private var model = ""
    private var apiKey = ""
    private val handler = Handler(Looper.getMainLooper())

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotification()
        backend = intent?.getStringExtra("backend") ?: ""
        model = intent?.getStringExtra("model") ?: ""
        apiKey = intent?.getStringExtra("apiKey") ?: ""
        val code = intent?.getIntExtra("resultCode", 0) ?: 0
        val data = if (Build.VERSION.SDK_INT >= 33) intent?.getParcelableExtra("data", Intent::class.java) else @Suppress("DEPRECATION") (intent?.getParcelableExtra("data") as? Intent)
        if (data != null) beginCapture(code, data)
        return START_NOT_STICKY
    }

    private fun createNotification() {
        val channel = "pool_capture"
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(NotificationChannel(channel, "Screen capture", NotificationManager.IMPORTANCE_LOW))
        }
        val n = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, channel) else Notification.Builder(this)
        startForeground(10, n.setContentTitle("Groq Pool AI is capturing").setContentText("Screen capture active • Tap Stop in app to end").setSmallIcon(android.R.drawable.ic_menu_view).build())
    }

    private fun beginCapture(resultCode: Int, data: Intent) {
        val dm = resources.displayMetrics
        val w = dm.widthPixels; val h = dm.heightPixels; val dpi = dm.densityDpi
        reader = ImageReader.newInstance(w, h, android.graphics.PixelFormat.RGBA_8888, 2)
        val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = mgr.getMediaProjection(resultCode, data)
        display = projection?.createVirtualDisplay("PoolPracticeCapture", w, h, dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader?.surface, null, handler)
        reader?.setOnImageAvailableListener({ r ->
            val image = r.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                val now = System.currentTimeMillis()
                if (now - lastSend > 1800) { lastSend = now; processFrame(image) }
            } finally { image.close() }
        }, handler)
        showOverlay()
    }

    private fun processFrame(image: Image) {
        if (backend.isBlank() || model.isBlank() || apiKey.isBlank()) { overlay?.setMessage("Set backend, model and API key"); return }
        val plane = image.planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride; val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * image.width
        val bmp = Bitmap.createBitmap(image.width + rowPadding / pixelStride, image.height, Bitmap.Config.ARGB_8888)
        bmp.copyPixelsFromBuffer(buffer)
        val cropped = Bitmap.createBitmap(bmp, 0, 0, image.width, image.height)
        bmp.recycle()
        val out = ByteArrayOutputStream(); cropped.compress(Bitmap.CompressFormat.JPEG, 65, out); cropped.recycle()
        val b64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        Thread {
            try {
                val conn = (URL("$backend/analyze").openConnection() as HttpURLConnection)
                conn.requestMethod = "POST"; conn.connectTimeout = 25000; conn.readTimeout = 45000
                conn.doOutput = true; conn.setRequestProperty("Content-Type", "application/json")
                val payload = JSONObject().put("apiKey", apiKey).put("model", model).put("image", b64)
                conn.outputStream.use { it.write(payload.toString().toByteArray()) }
                val response = (if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream).bufferedReader().readText()
                if (conn.responseCode !in 200..299) throw Exception("HTTP ${conn.responseCode}: ${response.take(180)}")
                val obj = JSONObject(response)
                val balls = obj.optJSONArray("balls")
                val cue = obj.optJSONObject("cue")
                val target = obj.optJSONObject("target")
                if (cue != null && target != null) {
                    val cx = cue.optDouble("x", -1.0).toFloat(); val cy = cue.optDouble("y", -1.0).toFloat()
                    val tx = target.optDouble("x", -1.0).toFloat(); val ty = target.optDouble("y", -1.0).toFloat()
                    runOnUi { overlay?.setGuide(cx, cy, tx, ty, "AI detected • practice guide") }
                } else runOnUi { overlay?.setMessage("No clear cue/target detected. Try a clearer table view.") }
            } catch (e: Exception) { runOnUi { overlay?.setMessage("Analysis error: ${e.message?.take(100)}") } }
        }.start()
    }

    private fun runOnUi(block: () -> Unit) = handler.post(block)

    private fun showOverlay() {
        window = getSystemService(WINDOW_SERVICE) as WindowManager
        overlay = GuideView(this)
        val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
        val p = WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT)
        window?.addView(overlay, p)
    }

    override fun onDestroy() {
        try { overlay?.let { window?.removeView(it) } } catch (_: Exception) {}
        display?.release(); reader?.close(); projection?.stop()
        super.onDestroy()
    }
    override fun onBind(intent: Intent?) = null
}

class GuideView(ctx: Context) : View(ctx) {
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.CYAN; strokeWidth = 5f; style = Paint.Style.STROKE }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.YELLOW; style = Paint.Style.FILL }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 34f; setShadowLayer(5f, 1f, 1f, Color.BLACK) }
    private var values: FloatArray? = null
    private var message = "Waiting for table image..."
    fun setGuide(cx: Float, cy: Float, tx: Float, ty: Float, msg: String) {
        values = floatArrayOf(cx, cy, tx, ty); message = msg; postInvalidate()
    }
    fun setMessage(msg: String) { message = msg; values = null; postInvalidate() }
    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val v = values
        if (v != null && v.all { it in 0f..1f }) {
            val x1 = v[0] * width; val y1 = v[1] * height
            val x2 = v[2] * width; val y2 = v[3] * height
            c.drawLine(x1, y1, x2, y2, line); c.drawCircle(x1, y1, 11f, dot); c.drawCircle(x2, y2, 11f, dot)
        }
        c.drawText(message, 24f, 54f, text)
    }
}
