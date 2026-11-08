package org.pyapp.screenshare

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
private const val DEFAULT_SERVER = "https://pyapp-builder.onrender.com"

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
