#!/usr/bin/env python3
"""Android app **ចែករំលែកអេក្រង់** (Kotlin) សម្រាប់ PyApp Builder។

app នេះចែករំលែកអេក្រង់ទូរស័ព្ទ **តែពេលម្ចាស់ទូរស័ព្ទចុចចាប់ផ្តើម ហើយយល់ព្រម**
លើ dialog របស់ Android។ Foreground service មាន notification ជាប់ជានិច្ច
(មានប៊ូតុង «ឈប់») ហើយផ្ញើ frame JPEG ទៅ server ដើម្បីមើលក្នុង browser។
បើ network ដាច់ វាភ្ជាប់ឡើងវិញដោយស្វ័យប្រវត្តិ (session ថ្មី + link ថ្មី)។
គ្មានការលាក់ខ្លួន គ្មានការចាប់ផ្តើមដោយស្ងាត់ គ្មានការថតពេល app បិទ។
"""
from __future__ import annotations

import androidproject

MANIFEST = '''<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <application android:usesCleartextTraffic="true">
        <service
            android:name=".ScreenShareService"
            android:exported="false"
            android:foregroundServiceType="mediaProjection" />
    </application>

</manifest>
'''

MAIN_ACTIVITY = '''package org.pyapp.screenshare

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

// server ដែល ZIP នេះត្រូវបានទាញចេញ (អាចប្តូរបានក្នុង app)
private const val DEFAULT_SERVER = "@@SERVER@@"

/** ស្ថានភាពដែល service ចែកជាមួយ UI (ដើម្បីបង្ហាញ link / កំហុសក្នុង app)។ */
internal object ShareState {
    @Volatile var sharing: Boolean = false
    @Volatile var lastError: String = ""
    @Volatile var lastLink: String = ""
}

class MainActivity : Activity() {

    private lateinit var serverInput: EditText
    private lateinit var statusView: TextView
    private val ui = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            renderStatus()
            ui.postDelayed(this, 2000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (20 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(pad, pad, pad, pad)

        val title = TextView(this)
        title.text = getString(R.string.app_name)
        title.textSize = 22f

        val hint = TextView(this)
        hint.text = "បើក app → Android សួរ → ចុច «ចាប់ផ្តើមឥឡូវ» → link ទៅ Telegram ស្វ័យប្រវត្តិ។ " +
            "Notification ជាប់ជានិច្ច ហើយអ្នកឈប់បានពេលណាក៏បាន។"
        hint.textSize = 14f
        hint.setPadding(0, pad / 2, 0, pad)

        val label = TextView(this)
        label.text = "Server URL"
        label.textSize = 13f

        serverInput = EditText(this)
        serverInput.inputType = InputType.TYPE_TEXT_VARIATION_URI
        serverInput.hint = "https://your-app.onrender.com"
        serverInput.setSingleLine(true)
        val prefs = getSharedPreferences("share", Context.MODE_PRIVATE)
        // server URL ត្រូវបានបង្កប់ពេល build — បង្ហាញប្រអប់តែពេលគ្មានសោះ
        serverInput.setText(if (DEFAULT_SERVER.isEmpty())
            prefs.getString("server", "") else DEFAULT_SERVER)

        val startButton = Button(this)
        startButton.text = "ចាប់ផ្តើមចែករំលែកអេក្រង់"
        startButton.setOnClickListener { askCapture() }

        val stopButton = Button(this)
        stopButton.text = "ឈប់ចែករំលែក"
        stopButton.setOnClickListener {
            startService(Intent(this, ScreenShareService::class.java)
                .setAction(ScreenShareService.ACTION_STOP))
            ShareState.sharing = false
            ShareState.lastError = ""
            renderStatus()
        }

        statusView = TextView(this)
        statusView.textSize = 14f
        statusView.setPadding(0, pad, 0, 0)

        val row = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        root.addView(title)
        root.addView(hint)
        if (DEFAULT_SERVER.isEmpty()) {
            root.addView(label)
            root.addView(serverInput, row)
        }
        root.addView(startButton, row)
        root.addView(stopButton, row)
        root.addView(statusView)
        setContentView(root)
        renderStatus()
        // បើកមកសួរ Allow តែម្តង (ការយល់ព្រមរបស់ Android នៅតែជារបស់អ្នកកាន់ទូរស័ព្ទ)
        if (savedInstanceState == null) {
            ui.postDelayed({ if (!ShareState.sharing) askCapture() }, 600)
        }

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission("android.permission.POST_NOTIFICATIONS") !=
            PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 2002)
        }
    }

    override fun onResume() {
        super.onResume()
        ui.post(ticker)
    }

    override fun onPause() {
        super.onPause()
        ui.removeCallbacks(ticker)
    }

    private fun renderStatus() {
        statusView.text = when {
            ShareState.lastError.isNotEmpty() -> "⚠ " + ShareState.lastError
            ShareState.sharing && ShareState.lastLink.isNotEmpty() ->
                "កំពុងចែករំលែក — link: " + ShareState.lastLink
            ShareState.sharing -> "កំពុងចែករំលែកអេក្រង់ — link បង្ហាញក្នុង notification"
            else -> "មិនទាន់ចាប់ផ្តើមចែករំលែក"
        }
    }

    /** បើអ្នកប្រើវាយតែ `app.onrender.com` → បន្ថែម `https://` ដោយស្វ័យប្រវត្តិ។ */
    private fun normalizeServer(raw: String): String {
        var url = raw.trim().trimEnd('/')
        if (url.isEmpty()) return ""
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://" + url
        }
        return url
    }

    private fun askCapture() {
        val url = normalizeServer(serverInput.text.toString())
        if (url.isEmpty()) {
            Toast.makeText(this, "សរសេរ Server URL ជាមុន (ឧ. https://your-app.onrender.com)",
                Toast.LENGTH_LONG).show()
            return
        }
        // 127.0.0.1 / localhost ជា server នៅក្នុងទូរស័ព្ទខ្លួនឯង — ទូរស័ព្ទភ្ជាប់មិនបាន
        val host = url.substringAfter("://").substringBefore("/").substringBefore(":")
        if (host == "127.0.0.1" || host == "0.0.0.0" || host == "10.0.2.2" ||
            host.equals("localhost", ignoreCase = true)) {
            Toast.makeText(this,
                "URL នេះជា server ក្នុងទូរស័ព្ទខ្លួនឯង (" + host + ") — " +
                    "សរសេរ URL ពិត ឧ. https://pyapp-builder.onrender.com",
                Toast.LENGTH_LONG).show()
            return
        }
        serverInput.setText(url)
        getSharedPreferences("share", Context.MODE_PRIVATE).edit()
            .putString("server", url).apply()
        ShareState.lastError = ""
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(manager.createScreenCaptureIntent(), 2001)
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 2001) return
        if (resultCode != RESULT_OK || data == null) {
            ShareState.lastError = "មិនបានយល់ព្រម — គ្មានការចែករំលែក"
            renderStatus()
            return
        }
        val url = normalizeServer(serverInput.text.toString())
        val intent = Intent(this, ScreenShareService::class.java)
            .putExtra(ScreenShareService.EXTRA_RESULT_CODE, resultCode)
            .putExtra(ScreenShareService.EXTRA_RESULT_DATA, data)
            .putExtra(ScreenShareService.EXTRA_SERVER, url)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent)
        else startService(intent)
        ShareState.sharing = true
        ShareState.lastError = ""
        renderStatus()
    }
}
'''

SERVICE = '''package org.pyapp.screenshare

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
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
        const val ACTION_STOP = "org.pyapp.screenshare.action.STOP"
        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_RESULT_DATA = "resultData"
        const val EXTRA_SERVER = "server"
        private const val TAG = "ScreenShare"
        private const val CHANNEL = "screenshare"
        private const val NOTIFICATION_ID = 4201
        private const val NOTIFICATION_FINAL = 4202
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
        startForeground(NOTIFICATION_ID, buildNotification("កំពុងភ្ជាប់ server…", null))
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
        return START_NOT_STICKY
    }

    private fun createSession() {
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
        attempts = 0
        reconnecting = false
        ShareState.sharing = true
        ShareState.lastError = ""
        ShareState.lastLink = viewer
        val viaTelegram = json.optBoolean("telegram", false)
        val detail = if (viaTelegram) "link បានផ្ញើទៅ Telegram" else "link: " + viewer
        updateNotification(detail + "\\n" + viewer, detail + "\\n" + viewer)
        lastFrameAt = System.currentTimeMillis()
        startCapture()
        if (!watchdogStarted) {
            watchdogStarted = true
            handler?.postDelayed(watchdog, WATCHDOG_MS)
        }
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
                looper.postDelayed(captureTask, FRAME_INTERVAL_MS)
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
        cleanup()
        try {
            stopForeground(true)
        } catch (exc: Exception) {
            Log.w(TAG, "stopForeground failed: " + exc.message)
        }
        stopSelf()
    }

    private fun cleanup() {
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
'''

README = '''# @@TITLE@@

App Android (Kotlin) សម្រាប់ចែករំលែកអេក្រង់ទូរស័ព្ទទៅ browser — **ដោយការយល់ព្រមរបស់ម្ចាស់ទូរស័ព្ទ**។

## របៀបប្រើ
1. ដំឡើង APK → បើក app → សរសេរ server URL៖ `@@SERVER@@`
   (បើវាយតែ `app.onrender.com` app បន្ថែម `https://` ឲ្យខ្លួនឯង)
2. ចុច **ចាប់ផ្តើមចែករំលែកអេក្រង់** → Android បង្ហាញ dialog យល់ព្រម → ចុច «ចាប់ផ្តើមឥឡូវ»។
3. Notification «កំពុងចែករំលែកអេក្រង់» បង្ហាញ link (ហើយ link ក៏ផ្ញើទៅ bot Telegram)។
   ស្ថានភាព (កំពុងចែករំលែក / កំហុស / link) ក៏បង្ហាញក្នុង app ដែរ។
4. បើក link ក្នុង browser → ឃើញអេក្រង់ live។ ចុច **ឈប់** (ក្នុង app ឬ notification) ដើម្បីបញ្ចប់។
5. server៖ `@@SERVER@@/screenshare` សម្រាប់ទាញ app នេះឡើងវិញ និងណែនាំ។

## អ្វីដែល app ធ្វើ / មិនធ្វើ
- ប្រើ `MediaProjection` + foreground service (`mediaProjection`) → ត្រូវមានការយល់ព្រមរាល់ដង
- ផ្ញើ frame JPEG (~2 fps, ទទឹង ≤ 640px) ទៅ `POST /api/screenshare/<code>/frame`
- **ភ្ជាប់ឡើងវិញស្វ័យប្រវត្តិ**៖ បើ network ដាច់ ឬ session ផុតកំណត់ វាបង្កើត session ថ្មី
  (ព្យាយាមរាល់ ៣→៦០ វិនាទី) ហើយផ្ញើ link ថ្មីទៅ Telegram ម្តងទៀត
- notification ជាប់ជានិច្ច ពេលកំពុងចែករំលែក (តម្រូវការរបស់ Android)
- មិនថតពេល app បិទ, មិនដំណើរការ background ដោយគ្មាន notification, មិនលាក់ខ្លួន
- គ្មាន frame ត្រូវរក្សាទុកលើ server — ឃើញតែ frame ចុងក្រោយ
- **គ្មាន bot token ក្នុង app** — token ជាកម្មសិទ្ធិ server (Render env `TELEGRAM_BOT_TOKEN`)

## Build
Import ZIP នេះក្នុង IDE → **Build APK** (GitHub Actions, AGP 8.5 · Kotlin 1.9 · compileSdk 34 · minSdk 21)។
'''


def _kotlin_string(value: str) -> str:
    """Escape តម្លៃសម្រាប់ដាក់ក្នុង Kotlin string literal ។"""
    return (value.replace("\\", "\\\\").replace('"', '\\"')
            .replace("$", "\\$").replace("\n", ""))


def app_project_files(server: str, title: str = "Screen Share",
                      package: str = "org.pyapp.screenshare") -> dict:
    """បង្កើត Android project ពេញលេញ (Kotlin) → dict {path: content}។"""
    server = (server or "").strip().rstrip("/")
    title = (title or "Screen Share").strip() or "Screen Share"
    safe_server = _kotlin_string(server)
    files = {
        "AndroidManifest.xml": MANIFEST,
        "MainActivity.kt": MAIN_ACTIVITY.replace("@@SERVER@@", safe_server),
        "ScreenShareService.kt": SERVICE,
        "README.md": README.replace("@@TITLE@@", title).replace("@@SERVER@@", server),
    }
    return androidproject.android_project_files(
        title, package, "kotlin", files,
        version="1.0", build="1",
        permissions=["INTERNET", "ACCESS_NETWORK_STATE", "FOREGROUND_SERVICE",
                     "FOREGROUND_SERVICE_MEDIA_PROJECTION", "POST_NOTIFICATIONS"],
        orientation="unspecified", online=True,
        # app នេះមិនត្រូវការ camera/mic/location — សុំតែ permission ដែលប្រើពិត
        default_permissions=False)
