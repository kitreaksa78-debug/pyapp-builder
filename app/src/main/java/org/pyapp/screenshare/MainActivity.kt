package org.pyapp.screenshare

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

// server ដែល ZIP នេះត្រូវបានទាញចេញ
private const val DEFAULT_SERVER = "https://pyapp-builder.onrender.com"

/** ស្ថានភាពដែល service ចែកជាមួយ UI។ */
internal object ShareState {
    @Volatile var sharing: Boolean = false
    @Volatile var lastError: String = ""
    @Volatile var lastLink: String = ""
}

/**
 * ចូល app → អេក្រង់ទទេ → Android សួរ Allow ភ្លាម។ គ្មានអក្សរ គ្មានប៊ូតុង។
 * ពេលកំពុងចែករំលែក ការបើក app ម្តងទៀតនឹងបិទខ្លួនឯង (ឈប់តាម notification)។
 */
class MainActivity : Activity() {

    private var serverInput: EditText? = null
    private var statusView: TextView? = null
    private val ui = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(if (DEFAULT_SERVER.isEmpty()) fallbackLayout() else blankView())

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission("android.permission.POST_NOTIFICATIONS") !=
            PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 2002)
        }
        // បើកមកសួរ Allow តែម្តង (ការយល់ព្រមរបស់ Android នៅតែជារបស់អ្នកកាន់ទូរស័ព្ទ)
        if (savedInstanceState == null) ui.postDelayed({ maybeAskCapture() }, 600)
    }

    /** អេក្រង់ទទេ — ចុចលើអេក្រង់ = សួរ Allow ម្តងទៀត (បើធ្លាប់បដិសេធ)។ */
    private fun blankView(): View {
        val view = View(this)
        view.setBackgroundColor(Color.BLACK)
        view.setOnClickListener { askCapture() }
        return view
    }

    /** ករណី ZIP ដែលគ្មាន server URL បង្កប់ — ត្រូវការប្រអប់សរសេរ URL។ */
    private fun fallbackLayout(): View {
        val pad = (20 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(pad, pad, pad, pad)

        val label = TextView(this)
        label.text = "Server URL"
        label.textSize = 13f

        val input = EditText(this)
        input.inputType = InputType.TYPE_TEXT_VARIATION_URI
        input.hint = "https://your-app.onrender.com"
        input.setSingleLine(true)
        input.setText(getSharedPreferences("share", Context.MODE_PRIVATE)
            .getString("server", ""))

        val startButton = Button(this)
        startButton.text = "ចាប់ផ្តើមចែករំលែកអេក្រង់"
        startButton.setOnClickListener { askCapture() }

        val status = TextView(this)
        status.textSize = 13f
        status.setPadding(0, pad, 0, 0)

        root.addView(label)
        root.addView(input, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(startButton)
        root.addView(status)
        serverInput = input
        statusView = status
        return root
    }

    override fun onResume() {
        super.onResume()
        // កំពុងចែករំលែក → បិទ UI (ឈប់តាម notification)
        if (ShareState.sharing) finish()
        renderStatus()
    }

    private fun maybeAskCapture() {
        if (ShareState.sharing) {
            finish()
            return
        }
        askCapture()
    }

    private fun renderStatus() {
        val view = statusView ?: return
        view.text = when {
            ShareState.lastError.isNotEmpty() -> "⚠ " + ShareState.lastError
            ShareState.sharing && ShareState.lastLink.isNotEmpty() -> ShareState.lastLink
            else -> ""
        }
    }

    private fun currentServer(): String {
        val typed = serverInput?.text?.toString()?.trim().orEmpty()
        return if (typed.isNotEmpty()) typed else DEFAULT_SERVER
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
        val url = normalizeServer(currentServer())
        if (url.isEmpty()) {
            Toast.makeText(this, "សរសេរ Server URL ជាមុន", Toast.LENGTH_LONG).show()
            return
        }
        // 127.0.0.1 / localhost ជា server នៅក្នុងទូរស័ព្ទខ្លួនឯង — ទូរស័ព្ទភ្ជាប់មិនបាន
        val host = url.substringAfter("://").substringBefore("/").substringBefore(":")
        if (host == "127.0.0.1" || host == "0.0.0.0" || host == "10.0.2.2" ||
            host.equals("localhost", ignoreCase = true)) {
            Toast.makeText(this,
                "URL នេះជា server ក្នុងទូរស័ព្ទខ្លួនឯង (" + host + ")",
                Toast.LENGTH_LONG).show()
            return
        }
        getSharedPreferences("share", Context.MODE_PRIVATE).edit()
            .putString("server", url).apply()
        serverInput?.setText(url)
        ShareState.lastError = ""
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(manager.createScreenCaptureIntent(), 2001)
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 2001) return
        // បដិសេធ → ត្រឡប់ទៅអេក្រង់ទទេ; ចុចលើអេក្រង់ = សួរម្តងទៀត
        if (resultCode != RESULT_OK || data == null) return
        val url = normalizeServer(currentServer())
        val intent = Intent(this, ScreenShareService::class.java)
            .putExtra(ScreenShareService.EXTRA_RESULT_CODE, resultCode)
            .putExtra(ScreenShareService.EXTRA_RESULT_DATA, data)
            .putExtra(ScreenShareService.EXTRA_SERVER, url)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent)
        else startService(intent)
        ShareState.sharing = true
        ShareState.lastError = ""
        // បិទ UI ភ្លាម — គ្មានអក្សរបង្ហាញលើទូរស័ព្ទ
        finish()
    }
}
