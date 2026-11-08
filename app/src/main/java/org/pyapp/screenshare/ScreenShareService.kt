package org.pyapp.screenshare

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Foreground service ដែលចែករំលែកអេក្រង់ — ដំណើរការតែពេលម្ចាស់ទូរស័ព្ទបានយល់ព្រម
 * ហើយបង្ហាញ notification ជាប់ជានិច្ច រហូតដល់គាត់ចុច «ឈប់»។
 *
 * បើ network ដាច់ ឬ server បិទ session វាបង្កើត session ថ្មីដោយស្វ័យប្រវត្តិ
 * (ព្យាយាមឡើងវិញរាល់ ៣→៦០ វិនាទី) ដើម្បីឲ្យ link ថ្មីមកដល់ Telegram វិញ។
 */
class ScreenShareService : Service() {

    companion object {
        val ACTION_STOP = "org.pyapp.screenshare.action.STOP"
        val EXTRA_RESULT_CODE = "resultCode"
        val EXTRA_RESULT_DATA = "resultData"
        val EXTRA_SERVER = "server"
        private const val TAG = "ScreenShare"
        private const val CHANNEL = "screenshare"
        private const val NOTIFICATION_ID = 4201
        private const val NOTIFICATION_FINAL = 4202
        private const val PREFS = "share"
        private const val FRAME_INTERVAL_MS = 500L
        private const val WATCHDOG_MS = 5000L
        private const val STALE_FRAME_MS = 45000L
        private const val MAX_WIDTH = 640
        private const val JPEG_QUALITY = 45
    }

    private val worker = Executors.newSingleThreadExecutor()
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var captureThread: HandlerThread? = null
    private var handler: Handler? = null
    private var server = ""
    private var code = ""
    private var token = ""
    private var screenWidth = 0
    private var screenHeight = 0
    private var screenDensity = 0
    private var attempts = 0

    @Volatile private var uploading = false
    @Volatile private var finished = false
    @Volatile private var reconnecting = false
    @Volatile private var lastFrameAt = 0L
    @Volatile private var watchdogStarted = false
    @Volatile private var paused = false

    private val captureTask = object : Runnable {
        override fun run() {
            if (finished) return
            grab()
            handler?.postDelayed(this, FRAME_INTERVAL_MS)
        }
    }

    private val retryTask = object : Runnable {
        override fun run() {
            if (finished) return
            worker.execute { createSession() }
        }
    }

    /** អេក្រង់បិទ/បើក — ផ្អាកថតពេលបិទ (frame ខ្មៅ) តែ session/link នៅដើម។ */
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> pauseCapture("អេក្រង់បិទ — ផ្អាកថត (link នៅដើម)")
                Intent.ACTION_SCREEN_ON -> resumeCapture()
            }
        }
    }

    private val watchdog = object : Runnable {
        override fun run() {
            if (finished) return
            val silent = System.currentTimeMillis() - lastFrameAt
            if (!reconnecting && lastFrameAt > 0 && silent > STALE_FRAME_MS) {
                Log.w(TAG, "no frame sent for $silent ms — reconnect")
                retrySession()
            }
            handler?.postDelayed(this, WATCHDOG_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @Suppress("DEPRECATION")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null && intent.action == ACTION_STOP) {
            shutdown()
            return START_NOT_STICKY
        }
        if (intent == null) {
            // ប្រព័ន្ធបើក service ឡើងវិញក្រោយ process ត្រូវ kill — MediaProjection ត្រូវការ
            // ការយល់ព្រមថ្មីពីម្ចាស់ទូរស័ព្ទ ដូច្នេះបញ្ចប់ស្អាត ហើយប្រាប់ឲ្យបើក app វិញ
            notifyFinal("ការចែករំលែកបានឈប់", "បើក app ដើម្បីចាប់ផ្តើមម្តងទៀត")
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, buildNotification("កំពុងភ្ជាប់ server…", null))
        try {
            val filter = IntentFilter()
            filter.addAction(Intent.ACTION_SCREEN_OFF)
            filter.addAction(Intent.ACTION_SCREEN_ON)
            registerReceiver(screenReceiver, filter)
        } catch (exc: Exception) {
            Log.w(TAG, "screen receiver failed: " + exc.message)
        }
        val data: Intent? = intent?.getParcelableExtra(EXTRA_RESULT_DATA)
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        server = (intent?.getStringExtra(EXTRA_SERVER) ?: "").trimEnd('/')
        if (data == null || resultCode == 0 || server.isEmpty()) {
            finish("គ្មានការយល់ព្រម ឬគ្មាន server URL — បញ្ចប់")
            return START_NOT_STICKY
        }
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = manager.getMediaProjection(resultCode, data)
        if (projection == null) {
            finish("Android មិនអនុញ្ញាតការថតអេក្រង់")
            return START_NOT_STICKY
        }
        val metrics = DisplayMetrics()
        (getSystemService(Context.WINDOW_SERVICE) as WindowManager)
            .defaultDisplay.getRealMetrics(metrics)
        screenWidth = metrics.widthPixels.coerceAtLeast(1)
        screenHeight = metrics.heightPixels.coerceAtLeast(1)
        screenDensity = metrics.densityDpi.coerceAtLeast(120)
        val looperThread = HandlerThread("share-capture")
        looperThread.start()
        captureThread = looperThread
        handler = Handler(looperThread.looper)
        projection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                shutdown()
            }
        }, handler)
        worker.execute { createSession() }
        // START_STICKY → ស្វាយ app ចេញពី recent / process ត្រូវរំលាយ ក៏ប្រព័ន្ធព្យាយាមរក្សា service
        return START_STICKY
    }

    /** ស្វាយ app ចេញពី recent → service បន្តចែករំលែក (មិនបញ្ចប់តាម task)។ */
    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.i(TAG, "task removed — ចែករំលែកបន្ត")
        super.onTaskRemoved(rootIntent)
    }

    private fun createSession() {
        if (tryReuse()) return
        val payload = JSONObject().put("title", applicationInfo.loadLabel(packageManager).toString())
        val json = postJson(server + "/api/screenshare/start", payload.toString())
        if (json == null) {
            onConnectFailed("ភ្ជាប់ server មិនបាន: " + server)
            return
        }
        val newCode = json.optString("code")
        val newToken = json.optString("token")
        val viewer = json.optString("viewer_url")
        if (newCode.isEmpty() || newToken.isEmpty()) {
            onConnectFailed("server ឆ្លើយតបមិនត្រឹមត្រូវ: " + server)
            return
        }
        code = newCode
        token = newToken
        saveSession()
        attempts = 0
        reconnecting = false
        ShareState.sharing = true
        ShareState.lastError = ""
        ShareState.lastLink = viewer
        val viaTelegram = json.optBoolean("telegram", false)
        // link ផ្ញើទៅ Telegram → notification បង្ហាញតែសារខ្លី (គ្មាន URL លើ lock screen);
        // បើគ្មាន Telegram ទេ notification ត្រូវបង្ហាញ link ដើម្បីកុំឲ្យបាត់
        if (viaTelegram) updateNotification("ចុច «ឈប់» ដើម្បីបញ្ចប់ · link នៅ Telegram", null)
        else updateNotification("link: " + viewer, viewer)
        lastFrameAt = System.currentTimeMillis()
        startCapture()
        ensureWatchdog()
    }

    private fun ensureWatchdog() {
        if (watchdogStarted) return
        watchdogStarted = true
        handler?.postDelayed(watchdog, WATCHDOG_MS)
    }

    /** ប្រើ session ដើមវិញ ដើម្បីរក្សា link ដូចដើម (៣០ នាទី) ពេល network ដាច់។ */
    private fun tryReuse(): Boolean {
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val oldCode = prefs.getString("code", "") ?: ""
        val oldToken = prefs.getString("token", "") ?: ""
        if (oldCode.isEmpty() || oldToken.isEmpty()) return false
        if (getJson(server + "/api/screenshare/" + oldCode + "/state") == null) {
            clearSession()
            return false
        }
        code = oldCode
        token = oldToken
        attempts = 0
        reconnecting = false
        paused = false
        ShareState.sharing = true
        ShareState.lastError = ""
        ShareState.lastLink = server + "/s/" + oldCode
        updateNotification("ចុច «ឈប់» ដើម្បីបញ្ចប់", null)
        lastFrameAt = System.currentTimeMillis()
        startCapture()
        ensureWatchdog()
        return true
    }

    private fun saveSession() {
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("code", code).putString("token", token).apply()
    }

    private fun clearSession() {
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove("code").remove("token").apply()
    }

    /** អេក្រង់បិទ → ផ្អាកថត (frame ខ្មៅ); session/link នៅដើមដដែល។ */
    private fun pauseCapture(reason: String) {
        if (finished || paused) return
        paused = true
        handler?.removeCallbacks(captureTask)
        updateNotification(reason, reason)
    }

    private fun resumeCapture() {
        if (finished || !paused || reconnecting) return
        paused = false
        lastFrameAt = System.currentTimeMillis()
        handler?.postDelayed(captureTask, FRAME_INTERVAL_MS)
    }

    private fun onConnectFailed(message: String) {
        ShareState.lastError = message
        ShareState.sharing = false
        if (attempts > 0) {
            val delayMs = minOf(60000L, 3000L * attempts)
            attempts += 1
            updateNotification("កំពុងព្យាយាមឡើងវិញ (" + (delayMs / 1000) + "s) — " + message, message)
            handler?.postDelayed(retryTask, delayMs)
        } else {
            finish(message)
        }
    }

    /** network ដាច់ ឬ session ផុត → បង្កើត session ថ្មី + link ថ្មី។ */
    private fun retrySession() {
        if (finished || reconnecting) return
        reconnecting = true
        attempts = 1
        releaseCapture()
        ShareState.sharing = false
        ShareState.lastError = "កំពុងភ្ជាប់ឡើងវិញ…"
        updateNotification("កំពុងភ្ជាប់ឡើងវិញ…", null)
        handler?.post(retryTask)
    }

    private fun updateNotification(text: String, big: String?) {
        try {
            notify(buildNotification(text, big))
        } catch (exc: Exception) {
            Log.w(TAG, "notify failed: " + exc.message)
        }
    }

    private fun startCapture() {
        val looper = handler ?: return
        looper.post {
            if (finished) return@post
            releaseCapture()
            try {
                val imageReader = ImageReader.newInstance(
                    screenWidth, screenHeight, PixelFormat.RGBA_8888, 2)
                reader = imageReader
                virtualDisplay = projection?.createVirtualDisplay(
                    "pyapp-screenshare", screenWidth, screenHeight, screenDensity,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    imageReader.surface, null, looper)
                if (!paused) looper.postDelayed(captureTask, FRAME_INTERVAL_MS)
            } catch (exc: Exception) {
                Log.w(TAG, "capture start failed: " + exc.message)
                finish("ចាប់ផ្តើមថតអេក្រង់មិនបាន")
            }
        }
    }

    private fun releaseCapture() {
        handler?.removeCallbacks(captureTask)
        try {
            virtualDisplay?.release()
        } catch (exc: Exception) {
            Log.w(TAG, "display release failed: " + exc.message)
        }
        virtualDisplay = null
        try {
            reader?.close()
        } catch (exc: Exception) {
            Log.w(TAG, "reader close failed: " + exc.message)
        }
        reader = null
    }

    private fun grab() {
        val image = try {
            reader?.acquireLatestImage()
        } catch (exc: Exception) {
            Log.w(TAG, "acquire failed: " + exc.message)
            null
        } ?: return
        if (uploading) {
            image.close()
            return
        }
        uploading = true
        worker.execute {
            try {
                sendFrame(image)
            } catch (exc: Exception) {
                Log.w(TAG, "frame failed: " + exc.message)
            } finally {
                try {
                    image.close()
                } catch (exc: Exception) {
                    Log.w(TAG, "close failed: " + exc.message)
                }
                uploading = false
            }
        }
    }

    private fun sendFrame(image: Image) {
        val plane = image.planes[0]
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val padding = rowStride - pixelStride * image.width
        val padded = Bitmap.createBitmap(
            image.width + padding / pixelStride, image.height, Bitmap.Config.ARGB_8888)
        padded.copyPixelsFromBuffer(plane.buffer)
        val cropped = Bitmap.createBitmap(padded, 0, 0, image.width, image.height)
        val scaled = if (image.width > MAX_WIDTH) {
            val targetHeight = (image.height.toFloat() * MAX_WIDTH / image.width)
                .toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(cropped, MAX_WIDTH, targetHeight, true)
        } else {
            cropped
        }
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        val bytes = out.toByteArray()
        padded.recycle()
        if (scaled !== cropped) scaled.recycle()
        cropped.recycle()
        if (code.isEmpty() || token.isEmpty()) return
        val status = postBytes(server + "/api/screenshare/" + code + "/frame", bytes, token)
        if (status in 200..299) {
            lastFrameAt = System.currentTimeMillis()
            if (reconnecting) {
                reconnecting = false
                attempts = 0
                ShareState.lastError = ""
            }
        } else if (status == 404 || status == 403) {
            // session ផុតកំណត់/ត្រូវលុប → បង្កើតថ្មី (link ថ្មីទៅ Telegram)
            handler?.post { retrySession() }
        }
    }

    private fun getJson(url: String): JSONObject? {
        var conn: HttpURLConnection? = null
        return try {
            conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            if (conn.responseCode !in 200..299) return null
            JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        } catch (exc: Exception) {
            Log.w(TAG, "getJson failed: " + exc.message)
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun postJson(url: String, body: String): JSONObject? {
        var conn: HttpURLConnection? = null
        return try {
            conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            val bytes = body.toByteArray(Charsets.UTF_8)
            conn.setFixedLengthStreamingMode(bytes.size)
            conn.outputStream.use { it.write(bytes) }
            if (conn.responseCode !in 200..299) return null
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            JSONObject(text)
        } catch (exc: Exception) {
            Log.w(TAG, "postJson failed: " + exc.message)
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun postBytes(url: String, body: ByteArray, sessionToken: String): Int {
        var conn: HttpURLConnection? = null
        return try {
            conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "image/jpeg")
            conn.setRequestProperty("X-Session-Token", sessionToken)
            conn.setFixedLengthStreamingMode(body.size)
            conn.outputStream.use { it.write(body) }
            conn.responseCode
        } catch (exc: Exception) {
            Log.w(TAG, "postBytes failed: " + exc.message)
            -1
        } finally {
            conn?.disconnect()
        }
    }

    private fun postStop() {
        val url = server + "/api/screenshare/" + code + "/stop"
        var conn: HttpURLConnection? = null
        try {
            conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 6000
            conn.readTimeout = 6000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("X-Session-Token", token)
            conn.setFixedLengthStreamingMode(0)
            conn.outputStream.use { }
            conn.responseCode
        } catch (exc: Exception) {
            Log.w(TAG, "postStop failed: " + exc.message)
        } finally {
            conn?.disconnect()
        }
    }

    private fun finish(message: String) {
        if (finished) return
        finished = true
        ShareState.sharing = false
        ShareState.lastError = message
        notifyFinal("ការចែករំលែកបានបញ្ចប់", message)
        showToast(message)
        clearSession()
        cleanup()
        try {
            stopForeground(true)
        } catch (exc: Exception) {
            Log.w(TAG, "stopForeground failed: " + exc.message)
        }
        stopSelf()
    }

    private fun shutdown() {
        if (finished) return
        finished = true
        ShareState.sharing = false
        ShareState.lastError = ""
        notifyFinal("បានឈប់ចែករំលែកអេក្រង់", null)
        if (code.isNotEmpty() && token.isNotEmpty()) {
            worker.execute { postStop() }
        }
        clearSession()
        cleanup()
        try {
            stopForeground(true)
        } catch (exc: Exception) {
            Log.w(TAG, "stopForeground failed: " + exc.message)
        }
        stopSelf()
    }

    private fun cleanup() {
        try {
            unregisterReceiver(screenReceiver)
        } catch (exc: Exception) {
            Log.w(TAG, "unregister failed: " + exc.message)
        }
        handler?.removeCallbacksAndMessages(null)
        releaseCapture()
        try {
            projection?.stop()
        } catch (exc: Exception) {
            Log.w(TAG, "projection stop failed: " + exc.message)
        }
        projection = null
        captureThread?.quitSafely()
        captureThread = null
        handler = null
        worker.shutdown()
    }

    override fun onDestroy() {
        finished = true
        ShareState.sharing = false
        cleanup()
        super.onDestroy()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL) == null) {
            val channel = NotificationChannel(
                CHANNEL, "ការចែករំលែកអេក្រង់", NotificationManager.IMPORTANCE_LOW)
            channel.description = "បង្ហាញពេល app កំពុងចែករំលែកអេក្រង់"
            manager.createNotificationChannel(channel)
        }
    }

    @Suppress("DEPRECATION")
    private fun buildNotification(text: String, big: String?, ongoing: Boolean = true): Notification {
        if (Build.VERSION.SDK_INT >= 26) ensureChannel()
        val builder = Notification.Builder(this)
        if (Build.VERSION.SDK_INT >= 26) builder.setChannelId(CHANNEL)
        builder.setSmallIcon(android.R.drawable.presence_video_online)
            .setContentTitle(if (ongoing) "កំពុងចែករំលែកអេក្រង់" else "ការចែករំលែកអេក្រង់")
            .setContentText(text)
            .setOngoing(ongoing)
            .setOnlyAlertOnce(true)
        if (ongoing) {
            val flags = if (Build.VERSION.SDK_INT >= 23)
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            else
                PendingIntent.FLAG_UPDATE_CURRENT
            val stopIntent = PendingIntent.getService(
                this, 1, Intent(this, ScreenShareService::class.java).setAction(ACTION_STOP), flags)
            builder.addAction(Notification.Action.Builder(
                android.R.drawable.ic_menu_close_clear_cancel, "ឈប់", stopIntent).build())
        }
        if (big != null) builder.setStyle(Notification.BigTextStyle().bigText(big))
        return builder.build()
    }

    private fun notify(notification: Notification) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    /** សារបញ្ចប់ — ID ដោយឡែក ដើម្បីកុំបាត់ពេល `stopForeground(true)`។ */
    private fun notifyFinal(title: String, message: String?) {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission("android.permission.POST_NOTIFICATIONS") !=
            android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return
        }
        try {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_FINAL,
                buildNotification(message ?: title, message, ongoing = false))
        } catch (exc: Exception) {
            Log.w(TAG, "notifyFinal failed: " + exc.message)
        }
    }

    private fun showToast(message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
        }
    }
}
