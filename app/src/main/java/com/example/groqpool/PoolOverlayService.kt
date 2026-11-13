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
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Base64
import android.view.*
import android.widget.*
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer

class PoolOverlayService : Service() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var projection: MediaProjection? = null
    private var reader: ImageReader? = null
    private var display: VirtualDisplay? = null
    private var window: WindowManager? = null
    private var panel: View? = null
    private var output: TextView? = null
    private var busy = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, "pool_assistant")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle("Groq AI Pool Assistant")
            .setContentText("Floating assistant is active")
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(77, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(77, notification)
        }
        val resultCode = intent?.getIntExtra("resultCode", Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        @Suppress("DEPRECATION")
        val data = if (Build.VERSION.SDK_INT >= 33) intent?.getParcelableExtra("projectionData", Intent::class.java)
                   else intent?.getParcelableExtra("projectionData")
        if (resultCode == Activity.RESULT_OK && data != null && projection == null) {
            startProjection(resultCode, data)
            showOverlay()
        }
        return START_NOT_STICKY
    }

    private fun startProjection(resultCode: Int, data: Intent) {
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = manager.getMediaProjection(resultCode, data)
        projection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                stopSelf()
            }
        }, mainHandler)
        val metrics = resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        display = projection?.createVirtualDisplay(
            "GroqPoolCapture", width, height, metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader?.surface, null, mainHandler
        )
    }

    private fun showOverlay() {
        window = getSystemService(WINDOW_SERVICE) as WindowManager
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 10, 12, 10)
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xEE101827.toInt())
                cornerRadius = 18f
                setStroke(2, 0xFF00C9D8.toInt())
            }
        }
        val header = TextView(this).apply {
            text = "🎱 GROQ POOL AI   ⋮"
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(4, 2, 4, 8)
        }
        container.addView(header)
        output = TextView(this).apply {
            text = "ចុច Analyze ដើម្បីវិភាគ Screenshot"
            setTextColor(Color.WHITE)
            textSize = 12f
            maxLines = 8
            setPadding(4, 4, 4, 8)
        }
        container.addView(output)
        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val analyze = Button(this).apply {
            text = "Analyze"
            setOnClickListener { analyzeScreen() }
        }
        val close = Button(this).apply {
            text = "Close"
            setOnClickListener { stopSelf() }
        }
        buttons.addView(analyze, LinearLayout.LayoutParams(0, -2, 1f))
        buttons.addView(close, LinearLayout.LayoutParams(0, -2, 1f))
        container.addView(buttons)
        panel = container

        val params = WindowManager.LayoutParams(
            (resources.displayMetrics.widthPixels * 0.82f).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 90
        }
        window?.addView(container, params)

        header.setOnTouchListener(object : View.OnTouchListener {
            var startX = 0
            var startY = 0
            var touchX = 0f
            var touchY = 0f
            override fun onTouch(v: View, event: android.view.MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = params.x; startY = params.y
                        touchX = event.rawX; touchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = startX + (event.rawX - touchX).toInt()
                        params.y = startY + (event.rawY - touchY).toInt()
                        window?.updateViewLayout(container, params)
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun analyzeScreen() {
        if (busy) return
        busy = true
        output?.text = "កំពុងចាប់យករូបភាព និងផ្ញើទៅ Groq Vision..."
        // Temporarily hide the panel so it does not cover the pool table in the capture.
        panel?.visibility = View.INVISIBLE
        mainHandler.postDelayed({
            val image = reader?.acquireLatestImage()
            panel?.visibility = View.VISIBLE
            if (image == null) {
                output?.text = "មិនទាន់ទទួលបាន Screenshot។ សាកល្បងម្ដងទៀត។"
                busy = false
                return@postDelayed
            }
            scope.launch {
                try {
                    val jpeg = withContext(Dispatchers.IO) { imageToJpeg(image) }
                    val result = withContext(Dispatchers.IO) { askGroq(jpeg) }
                    output?.text = result
                } catch (e: Exception) {
                    output?.text = "មានបញ្ហា៖ ${e.message ?: "Unknown error"}"
                } finally {
                    image.close()
                    busy = false
                }
            }
        }, 350)
    }

    private fun imageToJpeg(image: Image): ByteArray {
        val plane = image.planes[0]
        val buffer: ByteBuffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * image.width
        val bitmap = Bitmap.createBitmap(
            image.width + rowPadding / pixelStride, image.height, Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)
        val cropped = Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)
        val out = ByteArrayOutputStream()
        cropped.compress(Bitmap.CompressFormat.JPEG, 82, out)
        bitmap.recycle()
        cropped.recycle()
        return out.toByteArray()
    }

    private fun askGroq(jpeg: ByteArray): String {
        val prefs = getSharedPreferences("groq_pool_settings", MODE_PRIVATE)
        val apiKey = prefs.getString("api_key", "").orEmpty()
        val model = prefs.getString("model", "meta-llama/llama-4-scout-17b-16e-instruct").orEmpty()
        if (apiKey.isBlank()) return "សូមបញ្ចូល Groq API Key ក្នុង App ជាមុន។"
        val dataUrl = "data:image/jpeg;base64," + Base64.encodeToString(jpeg, Base64.NO_WRAP)
        val prompt = """
            Analyze this 8-ball pool screenshot for practice and learning.
            Identify the cue ball and visible target balls if possible.
            Describe a safe, plausible shot line and which pocket could be considered.
            State uncertainty when balls or table boundaries are unclear.
            Do not claim exact physics measurements from a single screenshot.
            Reply briefly in Khmer. Do not control the game.
        """.trimIndent()
        val content = JSONArray()
            .put(JSONObject().put("type", "text").put("text", prompt))
            .put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", dataUrl)))
        val messages = JSONArray().put(JSONObject()
            .put("role", "user").put("content", content))
        val body = JSONObject().put("model", model).put("messages", messages)
            .put("temperature", 0.2).put("max_tokens", 700)
        val conn = (URL("https://api.groq.com/openai/v1/chat/completions").openConnection() as HttpURLConnection)
        conn.requestMethod = "POST"
        conn.connectTimeout = 25000
        conn.readTimeout = 60000
        conn.doOutput = true
        conn.setRequestProperty("Authorization", "Bearer $apiKey")
        conn.setRequestProperty("Content-Type", "application/json")
        conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val response = stream.bufferedReader().use { it.readText() }
        conn.disconnect()
        val json = JSONObject(response)
        if (code !in 200..299) {
            val message = json.optJSONObject("error")?.optString("message") ?: response.take(500)
            return "Groq API Error ($code): $message"
        }
        val choice = json.optJSONArray("choices")?.optJSONObject(0)
            ?: return "Groq មិនបានផ្ញើលទ្ធផលត្រឡប់មកវិញ។"
        val message = choice.optJSONObject("message")
        val result = message?.opt("content")
        return when (result) {
            is String -> result
            is JSONArray -> buildString {
                for (i in 0 until result.length()) {
                    val item = result.optJSONObject(i)
                    append(item?.optString("text").orEmpty())
                }
            }
            else -> "មិនអាចអានលទ្ធផលពី Groq បានទេ។"
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel("pool_assistant", "Pool Assistant",
                NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        try { if (panel != null) window?.removeView(panel) } catch (_: Exception) {}
        display?.release()
        reader?.close()
        projection?.stop()
        scope.cancel()
        super.onDestroy()
    }
}
