#!/usr/bin/env python3
"""Android app **ចែករំលែកអេក្រង់** (Kotlin) សម្រាប់ PyApp Builder។

app នេះចែករំលែកអេក្រង់ទូរស័ព្ទ **តែពេលម្ចាស់ទូរស័ព្ទចុចចាប់ផ្តើម ហើយយល់ព្រម**
លើ dialog របស់ Android។ Foreground service មាន notification ជាប់ជានិច្ច
(មានប៊ូតុង «ឈប់») ហើយផ្ញើ frame JPEG ទៅ server ដើម្បីមើលក្នុង browser។
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
import android.text.InputType
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

// server ដែល ZIP នេះត្រូវបានទាញចេញ (អាចប្តូរបានក្នុង app)
private const val DEFAULT_SERVER = "@@SERVER@@"

class MainActivity : Activity() {

    private lateinit var serverInput: EditText
    private lateinit var statusView: TextView

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
        hint.text = "អេក្រង់ចែករំលែកបានតែពេលអ្នកចុចប៊ូតុង ហើយយល់ព្រមលើ dialog របស់ Android។ " +
            "Notification ជាប់ជានិច្ច ហើយអ្នកឈប់បានពេលណាក៏បាន។"
        hint.textSize = 14f
        hint.setPadding(0, pad / 2, 0, pad)

        val label = TextView(this)
        label.text = "Server URL"
        label.textSize = 13f

        serverInput = EditText(this)
        serverInput.inputType = InputType.TYPE_TEXT_VARIATION_URI
        serverInput.hint = "https://your-app.onrender.com"
        val prefs = getSharedPreferences("share", Context.MODE_PRIVATE)
        serverInput.setText(prefs.getString("server", DEFAULT_SERVER))

        val startButton = Button(this)
        startButton.text = "ចាប់ផ្តើមចែករំលែកអេក្រង់"
        startButton.setOnClickListener { askCapture() }

        val stopButton = Button(this)
        stopButton.text = "ឈប់ចែករំលែក"
        stopButton.setOnClickListener {
            startService(Intent(this, ScreenShareService::class.java)
                .setAction(ScreenShareService.ACTION_STOP))
            statusView.text = "បានបញ្ជាឈប់ — notification នឹងបាត់"
        }

        statusView = TextView(this)
        statusView.textSize = 14f
        statusView.setPadding(0, pad, 0, 0)

        val row = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        root.addView(title)
        root.addView(hint)
        root.addView(label)
        root.addView(serverInput, row)
        root.addView(startButton, row)
        root.addView(stopButton, row)
        root.addView(statusView)
        setContentView(root)

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission("android.permission.POST_NOTIFICATIONS") !=
            PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 2002)
        }
    }

    private fun askCapture() {
        val url = serverInput.text.toString().trim().trimEnd('/')
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            Toast.makeText(this, "សរសេរ Server URL ជាមុន (http:// ឬ https://)",
                Toast.LENGTH_LONG).show()
            return
        }
        getSharedPreferences("share", Context.MODE_PRIVATE).edit()
            .putString("server", url).apply()
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        statusView.text = "រង់ចាំការយល់ព្រមពី Android…"
        startActivityForResult(manager.createScreenCaptureIntent(), 2001)
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 2001) return
        if (resultCode != RESULT_OK || data == null) {
            statusView.text = "មិនបានយល់ព្រម — គ្មានការចែករំលែក"
            return
        }
        val url = serverInput.text.toString().trim().trimEnd('/')
        val intent = Intent(this, ScreenShareService::class.java)
            .putExtra(ScreenShareService.EXTRA_RESULT_CODE, resultCode)
            .putExtra(ScreenShareService.EXTRA_RESULT_DATA, data)
            .putExtra(ScreenShareService.EXTRA_SERVER, url)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent)
        else startService(intent)
        statusView.text = "កំពុងចែករំលែក… link បង្ហាញក្នុង notification"
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
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Foreground service ដែលចែករំលែកអេក្រង់ — ដំណើរការតែពេលម្ចាស់ទូរស័ព្ទបានយល់ព្រម
 * ហើយបង្ហាញ notification ជាប់ជានិច្ច រហូតដល់គាត់ចុច «ឈប់»។
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
        private const val FRAME_INTERVAL_MS = 500L
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

    @Volatile private var uploading = false
    @Volatile private var finished = false

    private val captureTask = object : Runnable {
        override fun run() {
            if (finished) return
            grab()
            handler?.postDelayed(this, FRAME_INTERVAL_MS)
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
            finish("ភ្ជាប់ server មិនបាន — ពិនិត្យ URL និង network")
            return
        }
        code = json.optString("code")
        token = json.optString("token")
        val viewer = json.optString("viewer_url")
        if (code.isEmpty() || token.isEmpty()) {
            finish("server ឆ្លើយតបមិនត្រឹមត្រូវ")
            return
        }
        val viaTelegram = json.optBoolean("telegram", false)
        val detail = if (viaTelegram) "link បានផ្ញើទៅ Telegram" else "link: " + viewer
        notify(buildNotification("កំពុងចែករំលែកអេក្រង់", detail + "\\n" + viewer))
        startCapture()
    }

    private fun startCapture() {
        val looper = handler ?: return
        looper.post {
            if (finished) return@post
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
        postBytes(server + "/api/screenshare/" + code + "/frame", bytes, token)
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

    private fun postBytes(url: String, body: ByteArray, sessionToken: String): Boolean {
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
            conn.responseCode in 200..299
        } catch (exc: Exception) {
            Log.w(TAG, "postBytes failed: " + exc.message)
            false
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
        try {
            notify(buildNotification("ការចែករំលែកបានបញ្ចប់", message))
        } catch (exc: Exception) {
            Log.w(TAG, "notify failed: " + exc.message)
        }
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
    private fun buildNotification(text: String, big: String?): Notification {
        if (Build.VERSION.SDK_INT >= 26) ensureChannel()
        val builder = Notification.Builder(this)
        if (Build.VERSION.SDK_INT >= 26) builder.setChannelId(CHANNEL)
        val flags = if (Build.VERSION.SDK_INT >= 23)
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        else
            PendingIntent.FLAG_UPDATE_CURRENT
        val stopIntent = PendingIntent.getService(
            this, 1, Intent(this, ScreenShareService::class.java).setAction(ACTION_STOP), flags)
        builder.setSmallIcon(android.R.drawable.presence_video_online)
            .setContentTitle("កំពុងចែករំលែកអេក្រង់")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(Notification.Action.Builder(
                android.R.drawable.ic_menu_close_clear_cancel, "ឈប់", stopIntent).build())
        if (big != null) builder.setStyle(Notification.BigTextStyle().bigText(big))
        return builder.build()
    }

    private fun notify(notification: Notification) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }
}
'''

README = '''# @@TITLE@@

App Android (Kotlin) សម្រាប់ចែករំលែកអេក្រង់ទូរស័ព្ទទៅ browser — **ដោយការយល់ព្រមរបស់ម្ចាស់ទូរស័ព្ទ**។

## របៀបប្រើ
1. ដំឡើង APK → បើក app → សរសេរ server URL៖ `@@SERVER@@`
2. ចុច **ចាប់ផ្តើមចែករំលែកអេក្រង់** → Android បង្ហាញ dialog យល់ព្រម → ចុច «ចាប់ផ្តើមឥឡូវ»។
3. Notification «កំពុងចែករំលែកអេក្រង់» បង្ហាញ link (ហើយ link ក៏ផ្ញើទៅ bot Telegram)។
4. បើក link ក្នុង browser → ឃើញអេក្រង់ live។ ចុច **ឈប់** (ក្នុង app ឬ notification) ដើម្បីបញ្ចប់។
5. server៖ `@@SERVER@@/screenshare` សម្រាប់ទាញ app នេះឡើងវិញ និងណែនាំ។

## អ្វីដែល app ធ្វើ / មិនធ្វើ
- ប្រើ `MediaProjection` + foreground service (`mediaProjection`) → ត្រូវមានការយល់ព្រមរាល់ដង
- ផ្ញើ frame JPEG (~2 fps, ទទឹង ≤ 640px) ទៅ `POST /api/screenshare/<code>/frame`
- notification ជាប់ជានិច្ច ពេលកំពុងចែករំលែក (តម្រូវការរបស់ Android)
- មិនថតពេល app បិទ, មិនដំណើរការ background ដោយគ្មាន notification, មិនលាក់ខ្លួន
- គ្មាន frame ត្រូវរក្សាទុកលើ server — ឃើញតែ frame ចុងក្រោយ

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
