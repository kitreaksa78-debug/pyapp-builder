package com.example.groqpool

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.view.*
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.max

class CaptureOverlayService : Service() {
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var guideView: GuideView? = null
    private var windowManager: WindowManager? = null
    private var lastRequestAt = 0L
    private var model = ""
    private var apiKey = ""
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startCaptureNotification()
        model = intent?.getStringExtra("model").orEmpty()
        apiKey = intent?.getStringExtra("apiKey").orEmpty()
        val resultCode = intent?.getIntExtra("resultCode", Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        val captureData = if (Build.VERSION.SDK_INT >= 33) {
            intent?.getParcelableExtra("data", Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra<Intent>("data")
        }
        if (captureData != null && apiKey.isNotBlank() && model.isNotBlank()) {
            beginCapture(resultCode, captureData)
        } else {
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startCaptureNotification() {
        val channelId = "groq_pool_capture"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(NotificationChannel(channelId, "Groq Pool screen analysis", NotificationManager.IMPORTANCE_LOW))
        }
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(this, channelId) else Notification.Builder(this)
        val notification = builder.setContentTitle("Groq Pool AI is active")
            .setContentText("Screen analysis is running. Stop it from the app.")
            .setSmallIcon(android.R.drawable.ic_menu_view).build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(7, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(7, notification)
        }
    }

    private fun beginCapture(resultCode: Int, data: Intent) {
        try {
            val metrics = resources.displayMetrics
            val width = max(1, metrics.widthPixels)
            val height = max(1, metrics.heightPixels)
            imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projection = manager.getMediaProjection(resultCode, data)
            projection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() { stopSelf() }
            }, mainHandler)
            virtualDisplay = projection?.createVirtualDisplay(
                "GroqPoolPracticeCapture", width, height, metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface, null, mainHandler
            )
            imageReader?.setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                try {
                    val now = System.currentTimeMillis()
                    if (now - lastRequestAt >= 4500L) {
                        lastRequestAt = now
                        analyzeImage(image)
                    }
                } finally { image.close() }
            }, mainHandler)
            showOverlay()
        } catch (e: Exception) {
            showMessage("Capture setup failed: ${e.message}")
            stopSelf()
        }
    }

    private fun analyzeImage(image: Image) {
        val plane = image.planes[0]
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * image.width
        val fullBitmap = Bitmap.createBitmap(image.width + rowPadding / pixelStride, image.height, Bitmap.Config.ARGB_8888)
        fullBitmap.copyPixelsFromBuffer(plane.buffer)
        val bitmap = Bitmap.createBitmap(fullBitmap, 0, 0, image.width, image.height)
        fullBitmap.recycle()
        val scaled = Bitmap.createScaledBitmap(bitmap, minOf(bitmap.width, 1024), (bitmap.height * (minOf(bitmap.width, 1024).toFloat() / bitmap.width)).toInt().coerceAtLeast(1), true)
        bitmap.recycle()
        val output = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 68, output)
        scaled.recycle()
        val base64Image = android.util.Base64.encodeToString(output.toByteArray(), android.util.Base64.NO_WRAP)
        Thread {
            try {
                val result = GroqClient.analyze(apiKey, model, base64Image)
                val cue = result.optJSONObject("cue")
                val target = result.optJSONObject("target")
                if (cue != null && target != null) {
                    val cx = cue.optDouble("x", -1.0).toFloat()
                    val cy = cue.optDouble("y", -1.0).toFloat()
                    val tx = target.optDouble("x", -1.0).toFloat()
                    val ty = target.optDouble("y", -1.0).toFloat()
                    if (listOf(cx, cy, tx, ty).all { it in 0f..1f }) {
                        mainHandler.post { guideView?.setGuide(cx, cy, tx, ty, "AI practice line • verify visually") }
                    } else showMessage("AI returned invalid coordinates. Try again.")
                } else {
                    showMessage(result.optString("message", "Could not clearly identify the cue and target balls."))
                }
            } catch (e: Exception) {
                showMessage("Groq analysis error: ${e.message?.take(110)}")
            }
        }.start()
    }

    private fun showMessage(message: String) = mainHandler.post { guideView?.setMessage(message) }

    private fun showOverlay() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        guideView = GuideView(this)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        windowManager?.addView(guideView, params)
    }

    override fun onDestroy() {
        try { guideView?.let { windowManager?.removeView(it) } } catch (_: Exception) { }
        virtualDisplay?.release()
        imageReader?.close()
        projection?.stop()
        apiKey = ""
        super.onDestroy()
    }
    override fun onBind(intent: Intent?) = null
}

object GroqClient {
    private const val ENDPOINT = "https://api.groq.com/openai/v1/chat/completions"

    fun test(apiKey: String, model: String): String {
        val request = JSONObject()
            .put("model", model)
            .put("messages", org.json.JSONArray().put(JSONObject().put("role", "user").put("content", "Reply with OK")))
            .put("max_tokens", 10)
        val response = post(apiKey, request)
        return response.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content", "Connected") ?: "Connected"
    }

    fun analyze(apiKey: String, model: String, imageBase64: String): JSONObject {
        val instruction = "Analyze this billiards/8-ball-pool table image for PRACTICE ONLY. Return ONLY valid JSON, no markdown, with this schema: {\"cue\":{\"x\":0.0,\"y\":0.0},\"target\":{\"x\":0.0,\"y\":0.0},\"message\":\"short note\"}. Coordinates must be normalized 0..1 relative to the FULL image (x from left, y from top). Identify the cue ball center and a reasonable target ball center. If uncertain, return {\"message\":\"Cannot identify balls clearly\"}. Never claim certainty when unclear."
        val imageUrl = "data:image/jpeg;base64,$imageBase64"
        val content = org.json.JSONArray()
            .put(JSONObject().put("type", "text").put("text", instruction))
            .put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", imageUrl)))
        val message = JSONObject().put("role", "user").put("content", content)
        val request = JSONObject()
            .put("model", model)
            .put("messages", org.json.JSONArray().put(message))
            .put("temperature", 0.1)
            .put("max_tokens", 250)
        val response = post(apiKey, request)
        val text = response.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content", "")
            ?: throw IllegalStateException("Groq returned an empty response")
        val cleaned = text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        return try { JSONObject(cleaned) } catch (_: Exception) { JSONObject().put("message", cleaned.take(180)) }
    }

    private fun post(apiKey: String, payload: JSONObject): JSONObject {
        val connection = (URL(ENDPOINT).openConnection() as HttpURLConnection)
        connection.requestMethod = "POST"
        connection.connectTimeout = 20000
        connection.readTimeout = 45000
        connection.doOutput = true
        connection.setRequestProperty("Authorization", "Bearer $apiKey")
        connection.setRequestProperty("Content-Type", "application/json")
        connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        connection.disconnect()
        if (code !in 200..299) throw IllegalStateException("HTTP $code: ${body.take(220)}")
        return JSONObject(body)
    }
}

class GuideView(context: Context) : View(context) {
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.CYAN; strokeWidth = 5f; style = Paint.Style.STROKE }
    private val ballPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.YELLOW; style = Paint.Style.FILL }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 30f; setShadowLayer(5f, 1f, 1f, Color.BLACK) }
    private var points: FloatArray? = null
    private var label = "Waiting for table image..."

    fun setGuide(cx: Float, cy: Float, tx: Float, ty: Float, message: String) {
        points = floatArrayOf(cx, cy, tx, ty)
        label = message
        postInvalidate()
    }
    fun setMessage(message: String) { points = null; label = message; postInvalidate() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val p = points
        if (p != null && p.size == 4) {
            val x1 = p[0] * width; val y1 = p[1] * height
            val x2 = p[2] * width; val y2 = p[3] * height
            canvas.drawLine(x1, y1, x2, y2, linePaint)
            canvas.drawCircle(x1, y1, 12f, ballPaint)
            canvas.drawCircle(x2, y2, 12f, ballPaint)
        }
        canvas.drawText(label.take(90), 20f, 48f, labelPaint)
    }
}
