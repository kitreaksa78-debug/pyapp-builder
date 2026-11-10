#!/usr/bin/env python3
"""បង្កើត Android Gradle project ពី file របស់ IDE។

គណៈកម្មការទទួលយកគ្រប់ app type៖
- `webview`  : file ទាំងអស់ → `app/src/main/assets/www/` ហើយ WebView បើក index.html
- `kotlin`   : `*.kt` របស់អ្នកប្រើ → compile ដោយ Kotlin (Gradle) ជា app native
- `java`     : `*.java` របស់អ្នកប្រើ → compile ដោយ Java (Gradle) ជា app native
- `ndk`      : app native + C/C++ (JNI) → NDK + CMake → `libnativeapp.so`
- `python`   : `*.py` របស់អ្នកប្រើ → Chaquopy (CPython ខ្លួនឯង) ដំណើរការលើ Android

Gradle + Android SDK/NDK compile ក្នុង GitHub Actions។
"""
from __future__ import annotations

import re
import xml.etree.ElementTree as ET

from androidutil import MAX_ICON_BYTES, launcher_icons, xml_escape

MODES = ("webview", "kotlin", "java", "ndk", "python")

# NDK/CMake version ត្រូវតែត្រូវនឹងអ្វីដែល workflow ដំឡើង (sdkmanager --install "ndk;<ver>")។
NDK_VERSION = "26.1.10909125"
CMAKE_VERSION = "3.22.1"
NATIVE_LIB = "nativeapp"

# ក្រប permission ពេញលេញ៖ ឈ្មោះ Android, ប្រភេទ, បរិយាបទខ្មែរ។
# dangerous = ត្រូវ runtime request (Android 6 / API 23+)
# special   = ត្រូវ special app access (មិនមែន manifest permission ទេ)
PERMISSIONS: dict[str, dict] = {
    #  connectivity
    "INTERNET":               {"kh": "អ៊ីនធឺណិត",            "kind": "normal"},
    "ACCESS_NETWORK_STATE":   {"kh": "ស្ថានភាព network",       "kind": "normal"},
    "ACCESS_WIFI_STATE":      {"kh": "ស្ថានភាព Wi-Fi",         "kind": "normal"},
    "CHANGE_WIFI_STATE":      {"kh": "ប្តូរស្ថានភាព Wi-Fi",   "kind": "normal"},
    "CHANGE_NETWORK_STATE":   {"kh": "ប្តូរស្ថានភាព network",  "kind": "normal"},
    "NEARBY_WIFI_DEVICES":    {"kh": "Wi-Fi ខាងក្រោម (API 33+)", "kind": "dangerous", "min_sdk": 33},

    #  bluetooth
    "BLUETOOTH":              {"kh": "Bluetooth",                "kind": "normal",   "dep": "android.hardware.bluetooth"},
    "BLUETOOTH_ADMIN":        {"kh": "ចម្លង Bluetooth",        "kind": "normal",   "dep": "android.hardware.bluetooth"},
    "BLUETOOTH_CONNECT":      {"kh": "ភ្ជាប់ Bluetooth",        "kind": "dangerous", "min_sdk": 31},
    "BLUETOOTH_SCAN":         {"kh": "ស្វែងរក Bluetooth",       "kind": "dangerous", "min_sdk": 31},
    "BLUETOOTH_ADVERTISE":    {"kh": "ផ្សាដូច Bluetooth",       "kind": "normal",   "min_sdk": 31},

    #  location  (dangerous — runtime request)
    "ACCESS_FINE_LOCATION":   {"kh": "ទីតាំង GPS ត្រឹមត្រូវ",     "kind": "dangerous"},
    "ACCESS_COARSE_LOCATION": {"kh": "ទីតាំងប្រហែល",           "kind": "dangerous"},
    "ACCESS_BACKGROUND_LOCATION": {"kh": "ទីតាំងភ្លក់ខ្លួន",     "kind": "dangerous", "min_sdk": 29},

    #  camera
    "CAMERA":                 {"kh": "កាមេរ៉ា",                  "kind": "dangerous", "dep": "android.hardware.camera"},

    #  audio
    "RECORD_AUDIO":           {"kh": "ថតសំឡេង",                "kind": "dangerous", "dep": "android.hardware.microphone"},

    #  storage / media
    "READ_EXTERNAL_STORAGE":  {"kh": "អាន storage",              "kind": "dangerous", "max_sdk": 32},
    "WRITE_EXTERNAL_STORAGE": {"kh": "សរសេរ storage (ចំណុចឯកជន)", "kind": "dangerous", "max_sdk": 28,
                                "extra": "android:maxSdkVersion=\"28\""},
    "READ_MEDIA_IMAGES":      {"kh": "អានរូបភាព",                "kind": "dangerous", "min_sdk": 33},
    "READ_MEDIA_VIDEO":       {"kh": "អានវីដេអូ",               "kind": "dangerous", "min_sdk": 33},
    "READ_MEDIA_AUDIO":       {"kh": "អានសំឡេង/តន្ត្រី",        "kind": "dangerous", "min_sdk": 33},
    "MANAGE_EXTERNAL_STORAGE":{"kh": "គ្រប់គ្រង storage ពេញ",      "kind": "special",   "min_sdk": 29,
                                "extra": "android:maxSdkVersion=\"28\""},

    #  notifications  (dangerous តែ API 33+)
    "POST_NOTIFICATIONS":     {"kh": "ជូនដំណឹង",                 "kind": "dangerous", "min_sdk": 33},

    #  phone / sms / call
    "READ_PHONE_STATE":       {"kh": "ស្ថានភាពទូរស័ព្ទ",        "kind": "dangerous"},
    "READ_PRIVILEGED_PHONE_STATE": {"kh": "ស្ថានភាពទូរស័ព្ទ (privileged)", "kind": "special", "min_sdk": 30},
    "CALL_PHONE":             {"kh": "ទូរស័ព្ទដោយផ្ទាល់",        "kind": "dangerous"},
    "ANSWER_PHONE_CALLS":    {"kh": "ឆ្លើយត្រូវការទូរស័ព្ទ",     "kind": "dangerous", "min_sdk": 30},
    "READ_CALL_LOG":         {"kh": "អានប្រវត្តិទូរស័ព្ទ",      "kind": "dangerous", "signature": True},
    "WRITE_CALL_LOG":        {"kh": "សរសេរប្រវត្តិទូរស័ព្ទ",      "kind": "dangerous", "signature": True},
    "ADD_VOICEMAIL":         {"kh": "បន្ថែមសំឡេងសម្គាល់",      "kind": "signature", "signature": True},
    "USE_SIP":               {"kh": "ប្រើ SIP",                  "kind": "dangerous"},
    "PROCESS_OUTGOING_CALLS":{"kh": "ដំណើរការការទូរស័ព្ទ",       "kind": "dangerous"},
    "READ_SMS":              {"kh": "អាន SMS",                   "kind": "dangerous", "signature": True},
    "SEND_SMS":              {"kh": "ផ្ញើ SMS",                  "kind": "dangerous"},
    " RECEIVE_SMS":          {"kh": "ទទួល SMS",                "kind": "dangerous", "signature": True},
    "RECEIVE_MMS":           {"kh": "ទទួល MMS",               "kind": "dangerous", "signature": True},
    "RECEIVE_WAP_PUSH":      {"kh": "ទទួល WAP push",            "kind": "dangerous", "signature": True},
    "WRITE_SMS":             {"kh": "សរសេរ SMS",                 "kind": "signature", "signature": True},
    "BROADCAST_SMS":         {"kh": "ចែករំលែក SMS",             "kind": "signature", "signature": True},

    #  contacts / calendar
    "READ_CONTACTS":         {"kh": "អានទំនាក់ទំនង",            "kind": "dangerous"},
    "WRITE_CONTACTS":        {"kh": "សរសេរទំនាក់ទំនង",          "kind": "dangerous"},
    "GET_ACCOUNTS":          {"kh": "រកគណនីអ្នកប្រើប្រាស់",      "kind": "normal"},
    "ACCOUNT_MANAGER":       {"kh": "គ្រប់គ្រងគណនី",            "kind": "signature", "signature": True},
    "READ_CALENDAR":         {"kh": "អានតាមម៉ោង",                "kind": "dangerous"},
    "WRITE_CALENDAR":        {"kh": "សរសេរតាមម៉ោង",              "kind": "dangerous"},
    "SCHEDULE_EXACT_ALARM":  {"kh": "កំណត់ម៉ោងច្បាស់លាស់",      "kind": "special",   "min_sdk": 31},
    "USE_EXACT_ALARM":       {"kh": "ប្រើម៉ោងច្បាស់លាស់",        "kind": "dangerous", "min_sdk": 31},

    #  sensors / body / activity
    "ACTIVITY_RECOGNITION":  {"kh": "សម្រាប់ការងារ/លើកចាប់ផ្តើម",  "kind": "dangerous", "min_sdk": 29},
    "BODY_SENSORS":          {"kh": "រូបិយប័ណ្ណរាងកាយ",         "kind": "dangerous", "min_sdk": 29},
    "BODY_SENSORS_BACKGROUND": {"kh": "រូបិយប័ណ្ណរាងកាយ (bg)", "kind": "dangerous", "min_sdk": 29},
    "ACTIVITY_RECOGNITION":  {"kh": "សម្រាប់ការងារ/លើកចាប់ផ្តើម",  "kind": "dangerous", "min_sdk": 29},

    #  NFC
    "NFC":                   {"kh": "NFC",                       "kind": "normal", "dep": "android.hardware.nfc"},
    "NFC_TRANSACTION_ITEM":  {"kh": "ធានា NFC transaction",     "kind": "signature", "signature": True},
    "NFC_HANDLES":           {"kh": "ប្រើ NFC handles",          "kind": "signature", "signature": True},

    #  vibration / wake
    "VIBRATE":               {"kh": "រំញ័រ",                    "kind": "normal"},
    "WAKE_LOCK":             {"kh": "បើកអេក្រង់",                "kind": "normal"},
    "REQUEST_IGNORE_BATTERY_OPTIMIZATIONS": {"kh": "មិនអនុញ្ញាត battery optimization", "kind": "normal", "min_sdk": 23},

    #  flashlight
    "FLASHLIGHT":            {"kh": "ពិល",                       "kind": "normal"},

    #  foreground / background services
    "FOREGROUND_SERVICE":    {"kh": "service ខាងក្រោយលាស់",      "kind": "normal"},
    "FOREGROUND_SERVICE_CAMERA": {"kh": "FGS កាមេរ៉ា",        "kind": "normal", "min_sdk": 34},
    "FOREGROUND_SERVICE_MICROPHONE": {"kh": "FGS ថតសំឡេង",  "kind": "normal", "min_sdk": 34},
    "FOREGROUND_SERVICE_LOCATION": {"kh": "FGS ទីតាំង",       "kind": "normal", "min_sdk": 34},
    "FOREGROUND_SERVICE_HEALTH":   {"kh": "FGS សុខភាព",     "kind": "normal", "min_sdk": 34},
    "FOREGROUND_SERVICE_MEDIA_PLAYBACK": {"kh": "FGS តន្ត្រី", "kind": "normal", "min_sdk": 34},
    "FOREGROUND_SERVICE_MEDIA_PROJECTION": {"kh": "FGS ចែកអេក្រង់", "kind": "normal", "min_sdk": 34},
    "FOREGROUND_SERVICE_PHONE_CALL": {"kh": "FGS ទូរស័ព្ទ",   "kind": "normal", "min_sdk": 34},
    "FOREGROUND_SERVICE_SPECIAL_USE": {"kh": "FGS ប្រភេទពិសេស", "kind": "normal", "min_sdk": 34},
    " FOREGROUND_SERVICE_DATA_SYNC": {"kh": "FGS ដាក់ស្តុក", "kind": "normal", "min_sdk": 34},
    "FOREGROUND_SERVICE_CONNECTED_DEVICE": {"kh": "FGS ឧបករណ៍", "kind": "normal", "min_sdk": 34},

    #  special app access (មិនមែន <uses-permission>)
    "SYSTEM_ALERT_WINDOW":   {"kh": "បង្ហាញលើ app ផ្សេង",      "kind": "special", "special_use": "overlay"},
    "PACKAGE_USAGE_STATS":   {"kh": "ប្រើប្រាស់ចំនួនការប្រើប្រាស់",  "kind": "special", "special_use": "usage_stats"},
    "QUERY_ALL_PACKAGES":    {"kh": "សួរគ្រប់ package",         "kind": "special", "min_sdk": 29},
    "READ_APP_SERVICES":     {"kh": "អាន app service",           "kind": "special"},
    "CaptivePortal":         {"kh": "បង្កើត captive portal",   "kind": "signature", "signature": True},
    "INSTALL_LOCATION":      {"kh": "ដំឡើងតំបន់",               "kind": "signature", "signature": True},
    "INSTALL_PACKAGES":      {"kh": "ដំឡើង app",                  "kind": "signature", "signature": True},
    "DELETE_PACKAGES":       {"kh": "លុប app",                    "kind": "signature", "signature": True},
    "REQUEST_INSTALL_PACKAGES": {"kh": "សុំដំឡើង app",           "kind": "normal"},
    "SYSTEM_ALERT_WINDOW":   {"kh": "បង្ហាញលើ app ផ្សេង",      "kind": "special"},
    "BIND_NOTIFICATION_LISTENER_SERVICE": {"kh": "ស្ថានីយប្រព័ន្ធជូនដំណឹង", "kind": "signature", "signature": True},
    "BIND_CARETEAM_SERVICE":  {"kh": "ភ្ជាប់ car team",          "kind": "signature", "signature": True},
}
# កែត្រចះ double-key នៅលើ PERMISSIONS ពីការកកស្រង់"
_PERMS_MAP: dict[str, dict] = {}
for _k, _v in PERMISSIONS.items():
    _PERMS_MAP[_k.strip()] = _v

# permission គោលដែល app ដែលបង្កើតថ្មីត្រូវមានស្រាប់ ដើម្បីឲ្យ feature ទូទៅដំណើរការដោយមិនត្រូវកែ manifest
DEFAULT_PERMISSIONS = (
    "CAMERA", "RECORD_AUDIO", "ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION",
    "POST_NOTIFICATIONS", "VIBRATE", "READ_MEDIA_IMAGES",
    "READ_EXTERNAL_STORAGE", "WRITE_EXTERNAL_STORAGE",
)

ORIENTATIONS = {"unspecified": "", "portrait": "portrait", "landscape": "landscape",
                "sensor": "sensor", "behind": "behind", "reverse": "reverse",
                "reverse_portrait": "reversePortrait", "reverse_landscape": "reverseLandscape"}

WEBVIEW_KT = r'''package @@PKG@@

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
'''

WEBVIEW_JAVA = '''package @@PKG@@;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/** WebView បើក UI ក្នុង app/src/main/assets/www/index.html (offline)។ */
public class MainActivity extends Activity {
    private WebView web;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.WHITE);
        getWindow().setNavigationBarColor(Color.WHITE);
        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setMediaPlaybackRequiresUserGesture(false);
        web.setWebViewClient(new WebViewClient());
        web.loadUrl("file:///android_asset/www/index.html");
        setContentView(web);
    }

    @Override
    protected void onDestroy() {
        if (web != null) web.destroy();
        super.onDestroy();
    }
}
'''

NATIVE_BRIDGE_JS = r'''/* ==========================================================================
 * AppForge bridge — JS ↔ Kotlin (សម្រាប់ WebView app)
 *
 * ក្នុង app ពិត (APK):  window.Native  ហៅ Kotlin តាម AppForge bridge
 * ក្នុង browser preview: window.Native ប្រើ fallback (console / vibrate / clipboard)
 *
 *   Native.toast('សួស្តី')
 *   Native.vibrate(60)
 *   Native.copy('text')
 *   Native.share('text', 'ចែករំលែក')
 *   Native.open('https://example.com')
 *   Native.notify('ចំណងជើង', 'សារ')
 *   Native.info()        // {brand, model, sdk, android, package, locale}
 *   Native.net()         // {online, wifi, mobile}
 *   Native.battery()     // {level, charging}
 *   const url = await Native.pickImage()      // data URL ឬ null
 *   const ok  = await Native.permission('android.permission.CAMERA')
 * ========================================================================== */
(function () {
  'use strict';

  var A = window.AppForge || null;
  var has = !!(A && typeof A.version === 'function');
  var waiters = {};
  var seq = 0;

  function call(name, args) {
    if (!has) return null;
    try { return A[name].apply(A, args); } catch (e) { return null; }
  }
  function read(name) {
    if (!has) return null;
    try { return JSON.parse(A[name]()); } catch (e) { return null; }
  }
  function nextId() { seq += 1; return 'w' + seq; }
  function fallbackToast(msg) {
    if (typeof console !== 'undefined') console.log('[Native.toast] ' + msg);
  }

  var Native = {
    available: has,
    version: has ? Number(A.version()) : 0,

    toast: function (msg) {
      msg = String(msg == null ? '' : msg);
      if (has) call('toast', [msg]); else fallbackToast(msg);
    },

    vibrate: function (ms) {
      ms = Number(ms) || 40;
      if (has) call('vibrate', [ms | 0]);
      else if (navigator.vibrate) navigator.vibrate(ms);
    },

    copy: function (text) {
      text = String(text == null ? '' : text);
      if (has) { call('copy', [text]); return Promise.resolve(true); }
      if (navigator.clipboard && navigator.clipboard.writeText) {
        return navigator.clipboard.writeText(text).then(function () { return true; },
          function () { return false; });
      }
      return Promise.resolve(false);
    },

    share: function (text, title) {
      text = String(text == null ? '' : text);
      title = String(title == null ? '' : title);
      if (has) { call('share', [text, title]); return Promise.resolve(true); }
      if (navigator.share) {
        return navigator.share({ text: text, title: title }).then(function () { return true; },
          function () { return false; });
      }
      fallbackToast('share: ' + text);
      return Promise.resolve(false);
    },

    open: function (url) {
      url = String(url == null ? '' : url);
      if (has) { call('openUrl', [url]); return; }
      try { window.open(url, '_blank'); } catch (e) {}
    },

    notify: function (title, body) {
      title = String(title == null ? '' : title);
      body = String(body == null ? '' : body);
      if (has) { call('notify', [title, body]); return; }
      if (typeof Notification !== 'undefined' && Notification.permission === 'granted') {
        try { new Notification(title, { body: body }); } catch (e) {}
      } else {
        fallbackToast(title + ' — ' + body);
      }
    },

    info: function () {
      return read('info') || {
        brand: '', model: '', device: '', sdk: 0, android: '',
        package: '', locale: (navigator.language || ''), bridge: 0
      };
    },

    net: function () {
      var r = read('net');
      if (r) return r;
      return { online: !!navigator.onLine, wifi: false, mobile: false };
    },

    battery: function () { return read('battery') || { level: -1, charging: false }; },

    granted: function (name) {
      if (!has) return false;
      try { return !!A.granted(String(name)); } catch (e) { return false; }
    },

    permission: function (name) {
      name = String(name);
      if (has && Native.granted(name)) return Promise.resolve(true);
      if (!has) return Promise.resolve(false);
      return new Promise(function (resolve) {
        var id = nextId();
        waiters[id] = resolve;
        call('permission', [name, id]);
      });
    },

    pickImage: function () {
      if (has) {
        return new Promise(function (resolve) {
          waiters.pick = resolve;
          call('pickImage', []);
        });
      }
      return new Promise(function (resolve) {
        var inp = document.createElement('input');
        inp.type = 'file';
        inp.accept = 'image/*';
        inp.style.display = 'none';
        inp.onchange = function () {
          var file = inp.files && inp.files[0];
          if (!file) { resolve(null); return; }
          var fr = new FileReader();
          fr.onload = function () { resolve(String(fr.result)); };
          fr.onerror = function () { resolve(null); };
          fr.readAsDataURL(file);
        };
        document.body.appendChild(inp);
        inp.click();
        setTimeout(function () {
          if (inp.parentNode) inp.parentNode.removeChild(inp);
        }, 60000);
      });
    }
  };

  /* Kotlin ហៅមកតាម evaluateJavascript('window.__AppForgeNative._xxx(...)') */
  window.__AppForgeNative = {
    _pickImage: function (dataUrl) {
      var f = waiters.pick;
      waiters.pick = null;
      if (f) f(dataUrl || null);
    },
    _permission: function (id, ok) {
      var f = waiters[id];
      if (f) { delete waiters[id]; f(!!ok); }
    }
  };

  window.Native = Native;
  window.AppForgeJS = Native;
})();
'''

DEFAULT_NATIVE_KT = '''package @@PKG@@

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.widget.EditText

/**
 * App native Kotlin (Android Views) — គ្មាន WebView។
 * សរសេរកូដនៅទីនេះ ហើយ Build APK ដើម្បីទទួល APK ពិត។
 */
class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pad = (20 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(pad, pad * 2, pad, pad)
            setBackgroundColor(Color.parseColor("#0D1117"))
        }

        val title = TextView(this).apply {
            text = "សួស្តី 👋"
            textSize = 26f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }

        // ឈ្មោះមិនត្រូវត្រូវនឹង property របស់ View (ឧ. hint) — Kotlin នឹងយក local
        // val ជំនួស setter ក្នុង apply { } → "Val cannot be reassigned" ពេល build
        val subtitle = TextView(this).apply {
            text = "app នេះសរសេរដោយ Kotlin"
            textSize = 15f
            setTextColor(Color.parseColor("#8B949E"))
            gravity = Gravity.CENTER
            setPadding(0, pad / 2, 0, pad)
        }

        val name = EditText(this).apply {
            hint = "ឈ្មោះរបស់អ្នក"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#8B949E"))
        }

        val btn = Button(this).apply {
            text = "ចុចទីនេះ"
            setOnClickListener {
                val who = name.text.toString().trim()
                title.text = if (who.isEmpty()) "បានចុច 🎉" else "សួស្តី $who 🎉"
                Toast.makeText(this@MainActivity, "Kotlin + Android", Toast.LENGTH_SHORT).show()
            }
        }

        root.addView(title)
        root.addView(subtitle)
        root.addView(name, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        root.addView(btn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        setContentView(root)
    }
}
'''

DEFAULT_NATIVE_JAVA = '''package @@PKG@@;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** App native Java (Android Views) — គ្មាន WebView។ */
public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        float d = getResources().getDisplayMetrics().density;
        int pad = (int) (20 * d);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(pad, pad * 2, pad, pad);
        root.setBackgroundColor(Color.parseColor("#0D1117"));

        final TextView title = new TextView(this);
        title.setText("សួស្តី 👋");
        title.setTextSize(26f);
        title.setTextColor(Color.WHITE);
        title.setGravity(Gravity.CENTER);

        TextView hint = new TextView(this);
        hint.setText("app នេះសរសេរដោយ Java");
        hint.setTextSize(15f);
        hint.setTextColor(Color.parseColor("#8B949E"));
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, pad / 2, 0, pad);

        final EditText name = new EditText(this);
        name.setHint("ឈ្មោះរបស់អ្នក");
        name.setTextColor(Color.WHITE);
        name.setHintTextColor(Color.parseColor("#8B949E"));

        Button btn = new Button(this);
        btn.setText("ចុចទីនេះ");
        btn.setOnClickListener(v -> {
            String who = name.getText().toString().trim();
            title.setText(who.isEmpty() ? "បានចុច 🎉" : "សួស្តី " + who + " 🎉");
            Toast.makeText(MainActivity.this, "Java + Android", Toast.LENGTH_SHORT).show();
        });

        root.addView(title);
        root.addView(hint);
        root.addView(name, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(btn, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        setContentView(root);
    }
}
'''

MANIFEST = '''<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

@@FEATURES@@
@@PERMISSIONS@@
    <application
@@ICON@@        android:allowBackup="true"
        android:label="@string/app_name"
        android:supportsRtl="true"
@@CLEARTEXT@@        android:theme="@@THEME@@">
        <activity
            android:name=".MainActivity"
            android:exported="true"
@@ORIENTATION@@            android:theme="@@THEME@@">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
@@APP_EXTRA@@
@@RUNTIME_NOTE@@    </application>

</manifest>
'''

# ---------------------------------------------------------------- Python
# Chaquopy ខ្ចប់ CPython ពិតប្រាកដចូល APK (Maven Central) — គ្មាន toolchain បន្ថែមក្នុង CI។
# 15.0.1 គាំទ្រ AGP 8.1–8.5 (យើងប្រើ 8.5.2) និង Python 3.8 លើ ABI ទាំង 4។
CHAQUOPY_VERSION = "15.0.1"
CHAQUOPY_ROOT = '    id("com.chaquo.python") version "%s" apply false\n' % CHAQUOPY_VERSION
CHAQUOPY_PLUGIN = '    id("com.chaquo.python")\n'
CHAQUOPY_CONFIG = '''
chaquopy {
    defaultConfig {
        version = "3.8"
    }
}
'''

# Chaquopy បដិសេធ build បើគ្មាន ndk.abiFilters ច្បាស់លាស់ — ABI ទាំង 4 ដែលវាគាំទ្រ។
CHAQUOPY_ABI = '''
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
        }
'''

DEFAULT_MAIN_PY = '''"""app Python — MainActivity ហៅ main() តាម Chaquopy។"""


def main():
    import platform
    return "សួស្តីពី Python " + platform.python_version() + " 🐍"
'''

# MainActivity ខាងក្រោមជា host: បើក CPython ហើយបង្ហាញអ្វីដែល main.py ត្រឡប់មកវិញ។
PYTHON_KT = '''package @@PKG@@

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val message = try {
            if (!Python.isStarted()) Python.start(AndroidPlatform(this))
            Python.getInstance().getModule("main").callAttr("main").toString()
        } catch (e: Exception) {
            "Python error: " + (e.message ?: e.toString())
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(56, 140, 56, 56)
            setBackgroundColor(Color.parseColor("#0D1117"))
        }
        val label = TextView(this).apply {
            text = message
            textSize = 18f
            setTextColor(Color.WHITE)
        }
        root.addView(label)
        setContentView(root)
    }
}
'''

ROOT_GRADLE = '''plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
@@CHAQUOPY_ROOT@@}
'''

SETTINGS_GRADLE = '''pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "@@ROOT@@"
include(":app")
'''

GRADLE_PROPS = '''org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=@@USE_ANDROIDX@@
android.nonTransitiveRClass=true
kotlin.code.style=official
'''

APP_GRADLE = '''plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
@@CHAQUOPY_PLUGIN@@}

android {
    namespace = "@@PKG@@"
    compileSdk = 34
@@NDK_BLOCK@@
    defaultConfig {
        applicationId = "@@PKG@@"
        minSdk = 21
        targetSdk = 34
        versionCode = @@BUILD@@
        versionName = "@@VERSION@@"
@@NDK_CONFIG@@
    }

    signingConfigs {
        create("stable") {
            storeFile = file("../signing/debug.p12")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
            storeType = "PKCS12"
        }
    }

    buildTypes {
        getByName("debug") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("stable")
        }
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("stable")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    lint {
        abortOnError = false
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = "17"
    }
}
@@CHAQUOPY_CONFIG@@@@DEPENDENCIES@@'''

STRINGS = '''<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">@@TITLE@@</string>
</resources>
'''

GITIGNORE = '''.gradle/
build/
app/build/
local.properties
out/
*.iml
.idea/
'''

# ---------------------------------------------------------------- NDK / C++
# Block ទាំងនេះបញ្ចូលក្នង app/build.gradle.kts តែពេល project មាន C/C++ (JNI)។
NDK_GRADLE_BLOCK = '''
    ndkVersion = "@@NDK@@"

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "@@CMAKE@@"
        }
    }
'''

NDK_GRADLE_CONFIG = '''
        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
                arguments += listOf("-DANDROID_STL=c++_shared")
            }
        }
'''

CMAKE_LISTS = '''cmake_minimum_required(VERSION 3.22.1)
project("@@LIB@@")

add_library(@@LIB@@ SHARED
        @@SOURCES@@)

target_compile_features(@@LIB@@ PUBLIC cxx_std_17)

find_library(log-lib log)
target_link_libraries(@@LIB@@ ${log-lib})
'''

NATIVE_CPP = '''#include <jni.h>
#include <string>

extern "C" JNIEXPORT jstring JNICALL
Java_@@JNI@@_MainActivity_stringFromJNI(JNIEnv *env, jobject /* this */) {
    std::string hello = "Hello from C++ / NDK";
    return env->NewStringUTF(hello.c_str());
}
'''

NDK_KT = '''package @@PKG@@

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

class MainActivity : Activity() {
    // C/C++ នៅ app/src/main/cpp/ → lib@@LIB@@.so (JNI)
    private external fun stringFromJNI(): String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tv = TextView(this).apply {
            textSize = 18f
            setPadding(48, 96, 48, 48)
            text = stringFromJNI()
        }
        setContentView(tv)
    }

    companion object {
        init {
            System.loadLibrary("@@LIB@@")
        }
    }
}
'''

NDK_JAVA = '''package @@PKG@@;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

public class MainActivity extends Activity {
    private native String stringFromJNI();

    static {
        System.loadLibrary("@@LIB@@");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView tv = new TextView(this);
        tv.setTextSize(18f);
        tv.setPadding(48, 96, 48, 48);
        tv.setText(stringFromJNI());
        setContentView(tv);
    }
}
'''

NATIVE_EXTS = (".c", ".cc", ".cpp", ".cxx", ".h", ".hpp", ".hxx")

PACKAGE_RE = re.compile(r"^[ \t]*package[ \t]+([A-Za-z_][\w.]*)[ \t]*;?[ \t]*$", re.MULTILINE)
MAIN_CLASS_RE = re.compile(r"\bclass\s+MainActivity\b")


ET.register_namespace("android", "http://schemas.android.com/apk/res/android")
ANDROID_NS = "{http://schemas.android.com/apk/res/android}"
# component ដែលអនុញ្ញាតឲ្យ project ប្រកាសខ្លួនឯងក្នុង <application>
APP_EXTRA_TAGS = {"service", "receiver", "provider", "meta-data", "activity-alias"}


def _runtime_permission_note(perms: list[str], full_perms: dict[str, dict] | None = None) -> str:
    """Generate XML comment អំពី runtime permission request សម្រាប់ dangerous permission។

    មិនដាក់ក្នុង manifest — គ្រាន់តែជូនដំណឹងដល់អ្នកប្រើ ថា permission ទាំងនេះ
    ត្រូវការ runtime request នៅលើឧបករណ៍។
    """
    active = full_perms if full_perms is not None else _PERMS_MAP
    dangerous = []
    for p in perms:
        p = p.strip()
        info = active.get(p)
        if info and info.get("kind") == "dangerous":
            dangerous.append(p)
    if not dangerous:
        return ""
    # ចែកជាក្រុមតាមប្រភេទ
    groups: dict[str, list[str]] = {}
    for p in dangerous:
        cat = (active[p].get("kh", p)).split()[0] if p in active else p
        groups.setdefault(cat, []).append(p)
    body = "Manually request these dangerous permissions at runtime (Android 6+, API 23+):\n"
    for p in sorted(dangerous):
        name = active[p]["kh"] if p in active else p
        body += f"  - {name} ({p})\n"
    body += "\nNo permission is granted silently. The user must approve on their device."
    return "    <!--\n        " + body.replace("\n", "\n        ") + "\n    -->\n"


def _user_manifest(files: dict) -> tuple[list[str], str]:
    """អាន `AndroidManifest.xml` របស់ project → (permissions, application children)។

    IDE បង្កើត manifest ដោយស្វ័យប្រវត្តិ ដូច្នេះ project ដែលត្រូវការ `<service>`
    (ឧ. overlay) ឬ permission ពិសេស អាចដាក់ manifest ខ្លួនឯងក្នុង project ហើយ
    builder បញ្ចូលតែអ្វីដែលចាំបាច់ — label/icon/launcher នៅដើមដដែល។
    """
    text = None
    for path in sorted(files, key=len):
        if path.rsplit("/", 1)[-1] == "AndroidManifest.xml":
            content = files[path]
            if isinstance(content, str) and "<manifest" in content:
                text = content
                break
    if text is None:
        return [], ""
    try:
        root = ET.fromstring(text)
    except ET.ParseError as exc:
        raise ValueError(f"AndroidManifest.xml ខូច: {exc}") from exc
    if root.tag != "manifest":
        raise ValueError("AndroidManifest.xml ត្រូវមាន root <manifest>")

    perms = []
    for el in root:
        if el.tag in ("uses-permission", "uses-permission-sdk-23"):
            name = el.get(ANDROID_NS + "name")
            if name:
                # លុប prefix permission ឱ្យបានតែ base name (ឧ. android.permission.CAMERA → CAMERA)
                if name.startswith("android.permission."):
                    name = name[len("android.permission."):]
                elif name.startswith("android:"):
                    continue
                perms.append(name)

    blocks = []
    app = root.find("application")
    if app is not None:
        for el in app:
            if el.tag not in APP_EXTRA_TAGS:
                continue
            name = el.get(ANDROID_NS + "name") or ""
            # launcher activity ត្រូវបានបង្កើតដោយ builder — មិនបញ្ចូលម្តងទៀត
            if el.tag.startswith("activity") and name.endswith("MainActivity"):
                continue
            ET.indent(el, space="    ")
            xml = ET.tostring(el, encoding="unicode").strip()
            xml = xml.replace(' xmlns:android="http://schemas.android.com/apk/res/android"', "")
            blocks.append("\n".join("        " + ln for ln in xml.splitlines()))
    return perms, ("\n".join(blocks) + "\n" if blocks else "")


def _permission_blocks(perms: list[str], online: bool,
                         full_perms: dict[str, dict] | None = None) -> str:
    """Generate <uses-permission> lines.

    - បញ្ចូល INTERNET ប្រសិនបើ app អាចប្រើ network (online ឬមាន permission ដែលទាក់ទងនឹង network)
    - បញ្ចូល ACCESS_NETWORK_STATE ប្រសិនបើមាន permission network ណាមួយ ឬ CAMERA/RECORD_AUDIO
      (ដែលពិតជាត្រូវការវាស់ signal)
    - ចែកចាយ permission ដោយ জ Categories: normal / dangerous តែប៉ុណ្ណោះ (special និង signature
      permission មិនដាក់ក្នុង manifest)
    - ការពារកំហុស៖ បិទឈ្មោះ permission ដែលមិនត្រឹមត្រូវ ឬឈ្មោះខុសគ្នា (ឧ. RECEIVE_SMS មាន space)
    """
    active = full_perms if full_perms is not None else _PERMS_MAP
    names: list[str] = []
    if online:
        names.append("android.permission.INTERNET")
    # ACCESS_NETWORK_STATE មកជាមួយ INTERNET ព្រោះកូដ bridge ប្រើវាដើម្បីដឹងស្ថានភាព network
    # (app ដែលជ្រើស Offline មិនត្រូវមាន permission network ទេ)
    perms_set = set(perms)
    if online or "ACCESS_NETWORK_STATE" in perms_set:
        names.append("android.permission.ACCESS_NETWORK_STATE")
    seen = set(names)
    for name in perms:
        name = name.strip()
        info = active.get(name)
        if info is None:
            continue
        kind = info.get("kind", "normal")
        if kind in ("normal", "dangerous"):
            pname = f"android.permission.{name}"
            if pname not in seen:
                seen.add(pname)
                names.append(pname)
    lines = []
    for name in names:
        extra = active.get(name.split(".")[-1], {}).get("extra")
        if extra:
            lines.append(f'    <uses-permission android:name="{name}" {extra} />')
        else:
            lines.append(f'    <uses-permission android:name="{name}" />')
    return "\n".join(lines) + "\n"


def _source_package(source: str, default: str) -> str:
    m = PACKAGE_RE.search(source)
    return m.group(1) if m else default


def android_project_files(title: str, package: str, mode: str,
                          files: dict, *, icon: bytes | None = None,
                          version: str = "1.0", build: str = "1",
                          permissions=(), orientation: str = "unspecified",
                          online: bool = True,
                          default_permissions: bool = True) -> dict:
    """បង្កើត file ទាំងអស់របស់ Android project ជា dict {path: bytes|str}។

    គណៈកម្មការទទួលយកគ្រប់ app type៖ webview, kotlin, java។
    permission ទាំងអស់ត្រូវបានបញ្ចូលដោយស្វ័យប្រវត្តិ ដោយផ្អែកលើប្រភេទ។
    dangerous permission ត្រូវបានជូនដំណឹងដល់អ្នកប្រើតាម comment ក្នុង manifest។
    """
    package = package.strip()
    mode = (mode or "webview").strip().lower()
    if mode not in MODES:
        raise ValueError("mode មិនត្រឹមត្រូវ — ត្រូវជា webview, kotlin, java, ndk ឬ python")
    if not re.match(r"^[a-z][a-z0-9_]{1,30}(\.[a-z][a-z0-9_]{1,30}){1,3}$", package):
        raise ValueError("package មិនត្រឹមត្រូវ — ឧ. org.pyapp.demo")
    orientation = (orientation or "unspecified").strip().lower()
    if orientation not in ORIENTATIONS:
        orientation = "unspecified"
    # Normalize permission names — លុប space, តែងតែ lowercase ក្នុង manifest
    perms_raw = [str(p).strip() for p in (permissions or ())]
    # បញ្ចប់ permission ដែលមិនត្រឹមត្រូវ (ជាក់លាក់) ដើម្បីការពារ manifest parse error
    invalid = [p for p in perms_raw if p not in _PERMS_MAP]
    if invalid:
        raise ValueError(f"permission មិនត្រឹមត្រូវ: {', '.join(invalid)}")
    # រួមវិញ permission ពី project manifest ដែលអ្នកប្រើដាក់ដោយផ្ទាល់
    manifest_perms, app_extra = _user_manifest(files or {})
    all_perms = list(dict.fromkeys(perms_raw + manifest_perms))  # order + លុប duplicate
    # bridge (JS↔Kotlin) ត្រូវការ permission ទាំងនេះ — normal + notification
    if mode == "webview":
        bridge_perms = ["VIBRATE", "POST_NOTIFICATIONS"]
        if online:
            bridge_perms.append("ACCESS_NETWORK_STATE")
        for _bp in bridge_perms:
            if _bp in _PERMS_MAP and _bp not in all_perms:
                all_perms.append(_bp)
    # permission គោល — app ដែលបង្កើតថ្មីមានសិទ្ធិទាំងនេះស្រាប់
    # (`default_permissions=False` សម្រាប់ app ដែលត្រូវការតែ permission ជាក់លាក់)
    if default_permissions:
        for _dp in DEFAULT_PERMISSIONS:
            if _dp in _PERMS_MAP and _dp not in all_perms:
                all_perms.append(_dp)
    icons = launcher_icons(icon) if icon else {}

    path = package.replace(".", "/")
    src = {}
    for name, content in (files or {}).items():
        if isinstance(content, (bytes, bytearray)):
            continue
        src[name] = content

    py_files: dict = {}
    if mode == "webview":
        html = next((c for n, c in src.items() if n.endswith("index.html")), None)
        if html is None:
            raise ValueError("រកមិនឃើញ index.html — WebView app ត្រូវការ index.html")
        sources = {f"app/src/main/java/{path}/MainActivity.kt":
                   WEBVIEW_KT.replace("@@PKG@@", package)}
    elif mode == "python":
        sources = {f"app/src/main/java/{path}/MainActivity.kt":
                   PYTHON_KT.replace("@@PKG@@", package)}
        for n, c in src.items():
            if n.lower().endswith(".py"):
                py_files[n.rsplit("/", 1)[-1]] = c
        # MainActivity ហៅ `main` module — ត្រូវមាន main.py ជានិច្ច
        if "main.py" not in py_files:
            py_files["main.py"] = DEFAULT_MAIN_PY
    else:
        # `ndk` អាចប្រើ Kotlin ឬ Java ជា host — ជ្រើសតាម file ដែលអ្នកប្រើដាក់
        if mode == "java":
            ext = ".java"
        elif mode == "ndk":
            has_kt = any(n.endswith(".kt") for n in src)
            has_java = any(n.endswith(".java") for n in src)
            ext = ".java" if (has_java and not has_kt) else ".kt"
        else:
            ext = ".kt"
        picks = {n: c for n, c in src.items() if n.endswith(ext)}
        if not any(MAIN_CLASS_RE.search(c) for c in picks.values()):
            if mode == "ndk":
                tpl = NDK_KT if ext == ".kt" else NDK_JAVA
                picks[f"MainActivity{ext}"] = (tpl.replace("@@PKG@@", package)
                                               .replace("@@LIB@@", NATIVE_LIB))
            else:
                default = DEFAULT_NATIVE_KT if mode == "kotlin" else DEFAULT_NATIVE_JAVA
                picks[f"MainActivity{ext}"] = default.replace("@@PKG@@", package)
        sources = {}
        for n, c in picks.items():
            filename = n.rsplit("/", 1)[-1]
            # package ត្រូវដូច applicationId ដើម្បីឲ្យ manifest `.MainActivity` ដំណើរការ
            decl = "package " + package + (";" if mode == "java" else "")
            body = (PACKAGE_RE.sub(decl, c, count=1)
                    if PACKAGE_RE.search(c) else decl + "\n\n" + c)
            sources[f"app/src/main/java/{path}/{filename}"] = body

    # ---- C/C++ (NDK + CMake) ---------------------------------------------
    # `ndk` mode ឬ project ណាដែលមាន .c/.cpp → compile ជា lib@@LIB@@.so
    native_files: dict = {}
    for n, c in src.items():
        if not n.lower().endswith(NATIVE_EXTS):
            continue
        base = n.rsplit("/", 1)[-1]
        if base in native_files and native_files[base] != c:
            # CMake តម្រូវឈ្មោះ file តែមួយក្នុង folder ដូច្នេះបដិសេធជាជាងបាត់ file ស្ងាត់
            raise ValueError(
                f"មាន C/C++ file ឈ្មោះដូចគ្នា ({base}) — ប្តូរឈ្មោះមួយសិន")
        native_files[base] = c
    user_cmake = next((c for n, c in src.items()
                      if n.rsplit("/", 1)[-1] == "CMakeLists.txt"), None)
    want_native = mode == "ndk" or bool(native_files) or user_cmake is not None
    native_extra: dict = {}
    if want_native:
        cpp_dir = "app/src/main/cpp"
        for name, content in native_files.items():
            native_extra[f"{cpp_dir}/{name}"] = content
        if not any(n.lower().endswith((".c", ".cc", ".cpp", ".cxx"))
                   for n in native_files):
            native_extra[f"{cpp_dir}/native-lib.cpp"] = NATIVE_CPP.replace(
                "@@JNI@@", package.replace(".", "_"))
        if user_cmake is not None:
            # CMakeLists របស់អ្នកប្រើ — រក្សាដើម មិន override
            native_extra[f"{cpp_dir}/CMakeLists.txt"] = user_cmake
        else:
            cpp_names = sorted(n.rsplit("/", 1)[-1] for n in native_extra
                               if n.lower().endswith((".c", ".cc", ".cpp", ".cxx")))
            native_extra[f"{cpp_dir}/CMakeLists.txt"] = (CMAKE_LISTS
                .replace("@@LIB@@", NATIVE_LIB)
                .replace("@@SOURCES@@", "\n        ".join(cpp_names)))
    ndk_block = (NDK_GRADLE_BLOCK.replace("@@NDK@@", NDK_VERSION)
                 .replace("@@CMAKE@@", CMAKE_VERSION)) if want_native else ""
    ndk_config = (NDK_GRADLE_CONFIG if want_native
                  else (CHAQUOPY_ABI if mode == "python" else ""))

    # Project ដែលប្រើ AndroidX (AppCompatActivity, NotificationCompat…) ត្រូវការ
    # dependency + theme ត្រឹមត្រូវ បើមិនដូច្នេះ Kotlin compile បរាជ័យ ឬ crash ពេលបើក app។
    code = "\n".join(c for c in sources.values() if isinstance(c, str))
    needs_appcompat = "AppCompatActivity" in code
    needs_androidx = needs_appcompat or "androidx." in code
    theme = "@android:style/Theme.Material.NoActionBar"
    dependencies = ""
    if needs_appcompat:
        theme = "@style/Theme.AppCompat.DayNight.NoActionBar"
        dependencies = ('\ndependencies {\n'
                        '    implementation("androidx.appcompat:appcompat:1.6.1")\n'
                        '}\n')
    elif needs_androidx:
        dependencies = ('\ndependencies {\n'
                        '    implementation("androidx.core:core-ktx:1.13.1")\n'
                        '}\n')

    icon_attr = ""
    if icons:
        icon_attr = ('        android:icon="@mipmap/ic_launcher"\n'
                     '        android:roundIcon="@mipmap/ic_launcher_round"\n')

    # Permissions ពិតប្រាកដពី Google សម្រាប់ការប្រើ API
    # API lint: បញ្ចូល uses-feature តាមឱកាស (camera, bluetooth, nfc, microphone)
    uses_features = []
    for p in all_perms:
        info = _PERMS_MAP.get(p.strip())
        if info and "dep" in info:
            feat = info["dep"].split(".")[-1]  # e.g. android.hardware.camera → camera
            uses_features.append(f'    <uses-feature android:name="{info["dep"]}" />')
    features_block = "\n".join(sorted(set(uses_features))) + "\n" if uses_features else ""

    # Runtime permission reminder (dangerous permission)
    runtime_note = _runtime_permission_note(all_perms)

    out = {
        "settings.gradle.kts": SETTINGS_GRADLE.replace("@@ROOT@@", package.split(".")[-1]),
        "build.gradle.kts": ROOT_GRADLE.replace(
            "@@CHAQUOPY_ROOT@@", CHAQUOPY_ROOT if mode == "python" else ""),
        "gradle.properties": GRADLE_PROPS.replace(
            "@@USE_ANDROIDX@@", "true" if needs_androidx else "false"),
        "app/build.gradle.kts": (APP_GRADLE.replace("@@PKG@@", package)
                                 .replace("@@VERSION@@", xml_escape(version))
                                 .replace("@@BUILD@@", str(build))
                                 .replace("@@NDK_BLOCK@@", ndk_block)
                                 .replace("@@NDK_CONFIG@@", ndk_config)
                                 .replace("@@CHAQUOPY_PLUGIN@@",
                                          CHAQUOPY_PLUGIN if mode == "python" else "")
                                 .replace("@@CHAQUOPY_CONFIG@@",
                                          CHAQUOPY_CONFIG if mode == "python" else "")
                                 .replace("@@DEPENDENCIES@@", dependencies)),
        "app/src/main/AndroidManifest.xml": (MANIFEST
            .replace("@@FEATURES@@", features_block)
            .replace("@@PERMISSIONS@@", _permission_blocks(all_perms, online))
            .replace("@@APP_EXTRA@@", app_extra)
            .replace("@@ICON@@", icon_attr)
            .replace("@@THEME@@", theme)
            .replace("@@CLEARTEXT@@",
                     '        android:usesCleartextTraffic="true"\n' if online else "")
            .replace("@@ORIENTATION@@",
                     (f'            android:screenOrientation="{ORIENTATIONS[orientation]}"\n'
                      if ORIENTATIONS[orientation] else ""))
            .replace("@@RUNTIME_NOTE@@", runtime_note)),
        "app/src/main/res/values/strings.xml": STRINGS.replace("@@TITLE@@", xml_escape(title)),
        ".gitignore": GITIGNORE,
    }
    out.update(sources)
    out.update(native_extra)
    for name, content in py_files.items():
        out[f"app/src/main/python/{name}"] = content
    if mode == "webview":
        for name, content in files.items():
            out[f"app/src/main/assets/www/{name}"] = content
        # JS bridge — ដាក់ដោយស្វ័យប្រវត្តិ (បើអ្នកប្រើមិនបានដាក់ខ្លួនឯង)
        bridge_rel = "appforge-bridge.js"
        if not any(k.endswith("/" + bridge_rel) for k in out):
            out[f"app/src/main/assets/www/{bridge_rel}"] = NATIVE_BRIDGE_JS
        # បញ្ចូល <script> ដើម្បីឱ្យ index.html ហៅ Native.* បានភ្លាម
        for key in [k for k in out
                    if k.startswith("app/src/main/assets/www/")
                    and k.endswith("/index.html")]:
            html = out.get(key)
            if not isinstance(html, str) or bridge_rel in html:
                continue
            tag = f'<script src="{bridge_rel}"></script>'
            at = html.lower().rfind("</body>")
            out[key] = (html[:at] + "  " + tag + "\n" + html[at:]) if at >= 0 \
                else html + "\n" + tag + "\n"
            break
    out.update(icons)
    return out
