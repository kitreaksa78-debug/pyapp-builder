package com.example.groqpool

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.graphics.*
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.view.*
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.*

class CaptureOverlayService : Service() {
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var overlay: GuideView? = null
    private var window: WindowManager? = null
    private var lastSend = 0L
    private var model = "qwen/qwen3.8-27b"
    private val handler = Handler(Looper.getMainLooper())

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotification()
        model = intent?.getStringExtra("model") ?: "qwen/qwen3.8-27b"
        val code = intent?.getIntExtra("resultCode", 0) ?: 0
        val data = readProjectionIntent(intent)
        if (data != null) beginCapture(code, data)
        return START_NOT_STICKY
    }

    @Suppress("DEPRECATION")
    private fun readProjectionIntent(source: Intent?): Intent? {
        if (source == null) return null
        return if (Build.VERSION.SDK_INT >= 33) {
            source.getParcelableExtra("data", Intent::class.java)
        } else {
            source.getParcelableExtra<Intent>("data")
        }
    }

    private fun createNotification() {
        val channel = "pool_capture"
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(NotificationChannel(channel, "Screen capture", NotificationManager.IMPORTANCE_LOW))
        }
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, channel) else Notification.Builder(this)
        val notification = builder.setContentTitle("Groq Pool Practice Guide")
            .setContentText("Screen capture active • Stop from the app")
            .setSmallIcon(android.R.drawable.ic_menu_view).build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(10, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else startForeground(10, notification)
    }

    private fun beginCapture(resultCode: Int, data: Intent) {
        val dm = resources.displayMetrics
        val w = dm.widthPixels; val h = dm.heightPixels; val dpi = dm.densityDpi
        reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
        val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = mgr.getMediaProjection(resultCode, data)
        display = projection?.createVirtualDisplay("PoolPracticeCapture", w, h, dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader?.surface, null, handler)
        reader?.setOnImageAvailableListener({ source ->
            val image = source.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                val now = System.currentTimeMillis()
                if (now - lastSend > 2500) { lastSend = now; processFrame(image) }
            } finally { image.close() }
        }, handler)
        showOverlay()
    }

    private fun processFrame(image: Image) {
        val apiKey = try { SecureStore.read(this).orEmpty() } catch (e: Exception) { "" }
        if (apiKey.isBlank()) { overlay?.setMessage("Save Groq API key in app settings"); return }
        val plane = image.planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val paddedWidth = image.width + (rowStride - pixelStride * image.width) / pixelStride
        val bitmap = Bitmap.createBitmap(paddedWidth, image.height, Bitmap.Config.ARGB_8888)
        buffer.rewind()
        bitmap.copyPixelsFromBuffer(buffer)
        val cropped = Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)
        bitmap.recycle()
        val out = ByteArrayOutputStream()
        cropped.compress(Bitmap.CompressFormat.JPEG, 68, out)
        cropped.recycle()
        val dataUrl = "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)

        Thread {
            var conn: HttpURLConnection? = null
            try {
                conn = URL("https://api.groq.com/openai/v1/chat/completions").openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 20000
                conn.readTimeout = 45000
                conn.doOutput = true
                conn.setRequestProperty("Authorization", "Bearer $apiKey")
                conn.setRequestProperty("Content-Type", "application/json")
                val instruction = "Analyze this 8-ball pool practice screenshot. Find the cue ball and one clearly visible object ball. Return ONLY a JSON object: {\"cue\":{\"x\":0.0,\"y\":0.0},\"target\":{\"x\":0.0,\"y\":0.0},\"note\":\"short note\"}. x and y must be normalized from 0.0 to 1.0 relative to the FULL screenshot. If uncertain or either ball is not visible, return {\"cue\":null,\"target\":null,\"note\":\"not clear\"}. Do not invent positions. This is a visual practice estimate, not a physics guarantee."
                val content = JSONArray()
                    .put(JSONObject().put("type", "text").put("text", instruction))
                    .put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", dataUrl)))
                val message = JSONObject().put("role", "user").put("content", content)
                val payload = JSONObject()
                    .put("model", model)
                    .put("messages", JSONArray().put(message))
                    .put("temperature", 0)
                    .put("max_completion_tokens", 400)
                    .put("response_format", JSONObject().put("type", "json_object"))
                conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val responseText = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (code !in 200..299) {
                    val messageText = try { JSONObject(responseText).optJSONObject("error")?.optString("message") ?: responseText.take(140) } catch (_: Exception) { responseText.take(140) }
                    throw Exception("Groq HTTP $code: $messageText")
                }
                val contentText = JSONObject(responseText).optJSONArray("choices")?.optJSONObject(0)
                    ?.optJSONObject("message")?.optString("content") ?: throw Exception("Empty Groq response")
                val result = JSONObject(contentText)
                val cue = result.optJSONObject("cue")
                val target = result.optJSONObject("target")
                if (cue != null && target != null) {
                    val cx = cue.optDouble("x", -1.0).toFloat()
                    val cy = cue.optDouble("y", -1.0).toFloat()
                    val tx = target.optDouble("x", -1.0).toFloat()
                    val ty = target.optDouble("y", -1.0).toFloat()
                    if (listOf(cx, cy, tx, ty).all { it.isFinite() && it in 0f..1f }) {
                        runOnUi { overlay?.setGuide(cx, cy, tx, ty, "Groq Vision • practice estimate") }
                    } else runOnUi { overlay?.setMessage("Ball positions were outside the image. Retrying...") }
                } else runOnUi { overlay?.setMessage("Cue or target not clear. Adjust the game view.") }
            } catch (e: Exception) {
                runOnUi { overlay?.setMessage("Groq error: ${e.message?.take(110)}") }
            } finally { conn?.disconnect() }
        }.start()
    }

    private fun runOnUi(block: () -> Unit) { handler.post(block) }

    private fun showOverlay() {
        window = getSystemService(WINDOW_SERVICE) as WindowManager
        overlay = GuideView(this)
        val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
        val params = WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            type, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT)
        window?.addView(overlay, params)
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
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 32f; setShadowLayer(5f, 1f, 1f, Color.BLACK) }
    private var values: FloatArray? = null
    private var message = "Waiting for table image..."
    fun setGuide(cx: Float, cy: Float, tx: Float, ty: Float, msg: String) { values = floatArrayOf(cx, cy, tx, ty); message = msg; postInvalidate() }
    fun setMessage(msg: String) { message = msg; values = null; postInvalidate() }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val points = values
        if (points != null && points.all { it in 0f..1f }) {
            val x1 = points[0] * width; val y1 = points[1] * height
            val x2 = points[2] * width; val y2 = points[3] * height
            canvas.drawLine(x1, y1, x2, y2, line)
            canvas.drawCircle(x1, y1, 12f, dot); canvas.drawCircle(x2, y2, 12f, dot)
        }
        canvas.drawText(message, 24f, 52f, label)
    }
}
