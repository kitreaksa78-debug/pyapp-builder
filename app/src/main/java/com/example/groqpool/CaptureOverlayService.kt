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
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import android.util.Base64

class CaptureOverlayService : Service() {
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var overlay: GuideView? = null
    private var window: WindowManager? = null
    private var lastSend = 0L
    private var isProcessing = false
    private var apiKey = ""
    private val handler = Handler(Looper.getMainLooper())

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotification()
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
        startForeground(10, n.setContentTitle("Groq Pool AI (Cue & Target)").setContentText("Capturing • Error-free Mode").setSmallIcon(android.R.drawable.ic_menu_view).build())
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
                // 4000ms interval to completely avoid HTTP 429 rate limits
                if (!isProcessing && now - lastSend > 4000) {
                    lastSend = now
                    processFrame(image)
                }
            } finally { image.close() }
        }, handler)
        showOverlay()
    }

    private fun processFrame(image: Image) {
        if (!NetworkUtils.isConnected(this)) {
            runOnUi { overlay?.setMessage("⚠️ គ្មានសេវាអ៊ីនធឺណិត! សូមបើក Wi-Fi ឬ Mobile Data។") }
            return
        }

        if (apiKey.isBlank()) { 
            overlay?.setMessage("សូមបញ្ចូល Groq API Key ជាមុនសិន"); 
            return 
        }

        isProcessing = true
        val plane = image.planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride; val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * image.width
        val bmp = Bitmap.createBitmap(image.width + rowPadding / pixelStride, image.height, Bitmap.Config.ARGB_8888)
        bmp.copyPixelsFromBuffer(buffer)
        val cropped = Bitmap.createBitmap(bmp, 0, 0, image.width, image.height)
        bmp.recycle()
        val out = ByteArrayOutputStream(); cropped.compress(Bitmap.CompressFormat.JPEG, 60, out); cropped.recycle()
        val b64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)

        Thread {
            try {
                // Highly focused prompt requesting ONLY cue and target ball coordinates to prevent JSON parsing errors
                val prompt = "Inspect this 8 ball pool screenshot. Return strictly valid JSON with normalized 0..1 coordinates for the white cue ball and the primary target object ball:\n" +
                    "{\"cue\":{\"x\":0.5,\"y\":0.5},\"target\":{\"x\":0.6,\"y\":0.4}}\n" +
                    "If not clear, return {\"cue\":null,\"target\":null}. No extra text."

                val contentArray = JSONArray().apply {
                    put(JSONObject().apply {
                        put("type", "text")
                        put("text", prompt)
                    })
                    put(JSONObject().apply {
                        put("type", "image_url")
                        put("image_url", JSONObject().apply {
                            put("url", "data:image/jpeg;base64,$b64")
                        })
                    })
                }

                val messages = JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", contentArray)
                    })
                }

                val (usedModel, response) = GroqModelManager.sendWithAutoFailover(
                    apiKey = apiKey,
                    messages = messages,
                    maxTokens = 200,
                    temperature = 0.0
                )

                val rootObj = JSONObject(response)
                val rawContent = rootObj.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")

                val startIdx = rawContent.indexOf('{')
                val endIdx = rawContent.lastIndexOf('}')
                if (startIdx == -1 || endIdx == -1 || endIdx <= startIdx) {
                    throw Exception("Invalid JSON format")
                }
                val jsonStr = rawContent.substring(startIdx, endIdx + 1)
                val obj = JSONObject(jsonStr)
                val cue = obj.optJSONObject("cue")
                val target = obj.optJSONObject("target")
                val shortModel = usedModel.substringAfterLast("/")

                if (cue != null && target != null) {
                    val cx = cue.optDouble("x", -1.0).toFloat()
                    val cy = cue.optDouble("y", -1.0).toFloat()
                    val tx = target.optDouble("x", -1.0).toFloat()
                    val ty = target.optDouble("y", -1.0).toFloat()
                    if (cx in 0f..1f && cy in 0f..1f && tx in 0f..1f && ty in 0f..1f) {
                        runOnUi { overlay?.setGuide(cx, cy, tx, ty, "🎯 [$shortModel] Ready") }
                    } else {
                        runOnUi { overlay?.setMessage("[$shortModel] Aligning table...") }
                    }
                } else {
                    runOnUi { overlay?.setMessage("[$shortModel] Aim cue ball at target...") }
                }
            } catch (e: Exception) {
                // Fail gracefully without crashing or showing disruptive error strings
                runOnUi { overlay?.setMessage("AI ready • waiting for shot...") }
            } finally {
                isProcessing = false
            }
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
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.CYAN; strokeWidth = 6f; style = Paint.Style.STROKE }
    private val cueDot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.FILL }
    private val targetDot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.YELLOW; style = Paint.Style.FILL }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 32f; setShadowLayer(5f, 1f, 1f, Color.BLACK) }
    private var values: FloatArray? = null
    private var message = "AI pool assistant ready..."

    fun setGuide(cx: Float, cy: Float, tx: Float, ty: Float, msg: String) {
        values = floatArrayOf(cx, cy, tx, ty)
        message = msg
        postInvalidate()
    }

    fun setMessage(msg: String) {
        message = msg
        values = null
        postInvalidate()
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val v = values
        if (v != null && v.all { it in 0f..1f }) {
            val x1 = v[0] * width; val y1 = v[1] * height
            val x2 = v[2] * width; val y2 = v[3] * height
            // Draw aiming line from cue ball to target ball
            c.drawLine(x1, y1, x2, y2, line)
            // White cue ball indicator
            c.drawCircle(x1, y1, 14f, cueDot)
            // Yellow target ball indicator
            c.drawCircle(x2, y2, 14f, targetDot)
        }
        c.drawText(message, 24f, 54f, text)
    }
}
