package org.pyapp.qaweb

import android.Manifest
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import org.json.JSONObject
import java.io.ByteArrayOutputStream

/**
 * WebView បើក UI ក្នុង app/src/main/assets/www/index.html (offline)
 * ព្រមទាំង JS↔Kotlin bridge: JS ហៅ `AppForge.xxx()` ឬ `Native.xxx()` (appforge-bridge.js)។
 *
 * សុវត្ថិភាព: bridge នេះផ្តល់សិទ្ធិ native ដល់ JS — កុំ load URL ពី internet
 * ក្នុង WebView នេះ (ប្រើ AppForge.openUrl() ដើម្បីបើក browser ខាងក្រៅវិញ)។
 */
class MainActivity : Activity() {
    private lateinit var web: WebView
    private var pendingPermId = ""
    private var pendingTitle = ""
    private var pendingBody = ""
    private val permCode = 1001
    private val pickCode = 1002

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.WHITE
        window.navigationBarColor = Color.WHITE
        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.mediaPlaybackRequiresUserGesture = false
            settings.setSupportZoom(false)
            webViewClient = WebViewClient()
            addJavascriptInterface(Bridge(), "AppForge")
            loadUrl("file:///android_asset/www/index.html")
        }
        setContentView(web)
    }

    override fun onDestroy() {
        if (::web.isInitialized) web.destroy()
        super.onDestroy()
    }

    /** ហៅ JS callback ដែលរៀបចំដោយ appforge-bridge.js */
    private fun js(expr: String) {
        if (!::web.isInitialized) return
        val code = "window.__AppForgeNative && window.__AppForgeNative." + expr
        runOnUiThread { web.evaluateJavascript(code, null) }
    }

    private fun q(s: String): String = JSONObject.quote(s)

    /** វត្ថុដែល JS ឃើញជា `window.AppForge` */
    inner class Bridge {

        @JavascriptInterface
        fun version(): Int = 1

        @JavascriptInterface
        fun toast(msg: String) {
            runOnUiThread { Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show() }
        }

        @JavascriptInterface
        fun vibrate(ms: Int) {
            val v = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
            val len = ms.coerceIn(10, 2000).toLong()
            try {
                if (Build.VERSION.SDK_INT >= 26) {
                    v.vibrate(VibrationEffect.createOneShot(len, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    v.vibrate(len)
                }
            } catch (e: Throwable) {
            }
        }

        @JavascriptInterface
        fun copy(text: String) {
            runOnUiThread {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("AppForge", text))
            }
        }

        @JavascriptInterface
        fun share(text: String, title: String) {
            runOnUiThread {
                val i = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                }
                startActivity(Intent.createChooser(i, title.ifEmpty { "ចែករំលែក" }))
            }
        }

        @JavascriptInterface
        fun openUrl(url: String) {
            runOnUiThread {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                } catch (e: Throwable) {
                }
            }
        }

        @JavascriptInterface
        fun notify(title: String, body: String) {
            if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED) {
                pendingPermId = "notify"
                pendingTitle = title
                pendingBody = body
                runOnUiThread {
                    requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), permCode)
                }
                return
            }
            post(title, body)
        }

        @JavascriptInterface
        fun info(): String = JSONObject()
            .put("brand", Build.BRAND)
            .put("model", Build.MODEL)
            .put("device", Build.DEVICE)
            .put("sdk", Build.VERSION.SDK_INT)
            .put("android", Build.VERSION.RELEASE)
            .put("package", packageName)
            .put("locale", java.util.Locale.getDefault().toString())
            .put("bridge", 1)
            .toString()

        @JavascriptInterface
        fun net(): String {
            val o = JSONObject().put("online", false).put("wifi", false).put("mobile", false)
            try {
                val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                if (Build.VERSION.SDK_INT >= 23) {
                    val n = cm.activeNetwork
                    val c = if (n != null) cm.getNetworkCapabilities(n) else null
                    if (c != null) {
                        o.put("online", c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
                        o.put("wifi", c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))
                        o.put("mobile", c.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))
                    }
                } else {
                    @Suppress("DEPRECATION")
                    val ai = cm.activeNetworkInfo
                    if (ai != null && ai.isConnected) {
                        o.put("online", true)
                        o.put("wifi", ai.type == ConnectivityManager.TYPE_WIFI)
                        o.put("mobile", ai.type == ConnectivityManager.TYPE_MOBILE)
                    }
                }
            } catch (e: Throwable) {
            }
            return o.toString()
        }

        @JavascriptInterface
        fun battery(): String {
            val o = JSONObject().put("level", -1).put("charging", false)
            try {
                val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                o.put("level", bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY))
                val st = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                    ?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
                o.put("charging", st == BatteryManager.BATTERY_STATUS_CHARGING ||
                    st == BatteryManager.BATTERY_STATUS_FULL)
            } catch (e: Throwable) {
            }
            return o.toString()
        }

        /** បើក picker រូបភាព → JS ទទួល `_pickImage(dataUrl|null)` */
        @JavascriptInterface
        fun pickImage() {
            runOnUiThread {
                val i = Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "image/*"
                    addCategory(Intent.CATEGORY_OPENABLE)
                }
                try {
                    startActivityForResult(Intent.createChooser(i, "ជ្រើសរូបភាព"), pickCode)
                } catch (e: Throwable) {
                    js("_pickImage(null)")
                }
            }
        }

        @JavascriptInterface
        fun granted(name: String): Boolean =
            Build.VERSION.SDK_INT < 23 ||
                checkSelfPermission(name) == PackageManager.PERMISSION_GRANTED

        /** ស្នើ permission ពេលដំណើរការ → JS ទទួល `_permission(id, true|false)` */
        @JavascriptInterface
        fun permission(name: String, id: String) {
            if (granted(name)) {
                js("_permission(" + q(id) + ", true)")
                return
            }
            if (Build.VERSION.SDK_INT < 23) {
                js("_permission(" + q(id) + ", false)")
                return
            }
            pendingPermId = id
            runOnUiThread { requestPermissions(arrayOf(name), permCode) }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>,
                                            grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != permCode) return
        val ok = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
        val id = pendingPermId
        pendingPermId = ""
        if (id == "notify") {
            if (ok) post(pendingTitle, pendingBody)
            return
        }
        if (id.isNotEmpty()) js("_permission(" + q(id) + ", " + ok + ")")
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != pickCode) return
        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null) {
            js("_pickImage(null)")
            return
        }
        Thread {
            val out = try {
                readImageDataUrl(uri)
            } catch (e: Throwable) {
                null
            }
            runOnUiThread { js("_pickImage(" + (if (out == null) "null" else q(out)) + ")") }
        }.start()
    }

    /** អានរូបភាព → data URL (JPEG ≤1024px) ដើម្បីប្រើក្នុង <img> បានភ្លាម */
    private fun readImageDataUrl(uri: Uri): String? {
        val cr = contentResolver
        val bounds = BitmapFactory.Options()
        bounds.inJustDecodeBounds = true
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var scale = 1
        while (bounds.outWidth / scale > 1024 || bounds.outHeight / scale > 1024) scale *= 2
        val opts = BitmapFactory.Options()
        opts.inSampleSize = scale
        val bmp: Bitmap = cr.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        } ?: return null
        val bos = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 80, bos)
        bmp.recycle()
        return "data:image/jpeg;base64," + Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
    }

    @Suppress("DEPRECATION")
    private fun post(title: String, body: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(
                "appforge", "AppForge", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), flags)
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, "appforge")
                else Notification.Builder(this)
        b.setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pi)
        nm.notify((System.currentTimeMillis() % 100000).toInt(), b.build())
    }
}
