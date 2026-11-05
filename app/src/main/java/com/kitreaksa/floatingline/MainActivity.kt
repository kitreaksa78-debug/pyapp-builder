package com.kitreaksa.floatingline

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * ផ្ទាំងគ្រប់គ្រង Floating Line Trainer។
 *
 * Overlay ត្រូវការ permission ពិសេស «Display over other apps» ដែល Android មិនអនុញ្ញាត
 * ឲ្យ app បើកដោយខ្លួនឯងទេ — app គ្រាន់តែបើក Settings ហើយអ្នកប្រើត្រូវបើកដោយដៃ។
 */
class MainActivity : Activity() {

    private var statusText: TextView? = null
    private var startButton: Button? = null
    private var stopButton: Button? = null
    private var pendingStart = false
    private var askedNotifications = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(28), dp(20), dp(28))
            setBackgroundColor(Color.parseColor("#0D1117"))
        }

        root.addView(TextView(this).apply {
            text = "Floating Line Trainer"
            textSize = 24f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        })

        root.addView(TextView(this).apply {
            text = "បន្ទាត់ហាត់ផ្ដេកអណ្ដែតលើ app ផ្សេង — អូសបាន បង្វិលបាន បង្ហាញ/លាក់បាន។"
            textSize = 14f
            setTextColor(Color.parseColor("#8B949E"))
            setPadding(0, dp(6), 0, dp(18))
        })

        statusText = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.WHITE)
            setPadding(dp(14), dp(10), dp(14), dp(10))
        }
        root.addView(statusText, matchWidth())

        startButton = primaryButton("បើក overlay") { startOverlay() }
        root.addView(startButton, matchWidthTop(14))

        stopButton = secondaryButton("បិទ overlay") { stopOverlay() }
        root.addView(stopButton, matchWidthTop(8))

        root.addView(secondaryButton("ការកំណត់ battery / ស្ថិរភាព") { openBatterySettings() },
            matchWidthTop(8))

        root.addView(TextView(this).apply {
            text = "បើ overlay បាត់ពេលបិទអេក្រង់ ឬប្តូរ app៖ បើក Settings → Apps → " +
                "Floating Line Trainer → Battery → «Unrestricted»។ នៅ Huawei/Honor " +
                "បើក «Manual launch» និង «Allow background activity» ក្នុង App launch។"
            textSize = 13f
            setTextColor(Color.parseColor("#8B949E"))
            setPadding(0, dp(22), 0, 0)
        })

        setContentView(ScrollView(this).apply { addView(root) })
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
        if (pendingStart && hasOverlayPermission()) {
            pendingStart = false
            startOverlay()
        }
        requestNotificationPermissionOnce()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_OVERLAY) {
            refresh()
            if (!hasOverlayPermission()) {
                toast("«Display over other apps» មិនទាន់បើក — overlay មិនអាចបង្ហាញបានទេ")
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>,
                                            grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_NOTIFICATIONS) {
            // notification ជាការបង្ហាញប៉ុណ្ណោះ — service នៅដំណើរការបានទោះបដិសេធ
            refresh()
        }
    }

    private fun refresh() {
        val granted = hasOverlayPermission()
        statusText?.text = if (granted) {
            "✅ អនុញ្ញាតរួច — overlay អាចបង្ហាញលើ app ផ្សេង"
        } else {
            "⛔ មិនទាន់អនុញ្ញាត — ចុច «បើក overlay» ដើម្បីបើក Settings"
        }
        statusText?.background = rounded(
            Color.parseColor(if (granted) "#12351F" else "#3A1D1D"),
            Color.parseColor(if (granted) "#238636" else "#DA3633"))
        startButton?.isEnabled = granted
        startButton?.alpha = if (granted) 1f else 0.5f
        stopButton?.isEnabled = granted
    }

    private fun startOverlay() {
        if (!hasOverlayPermission()) {
            pendingStart = true
            requestOverlayPermission()
            return
        }
        val intent = Intent(this, OverlayService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent)
            else startService(intent)
            toast("overlay កំពុងបង្ហាញ — ត្រឡប់ទៅ app ណាមួយបាន")
        } catch (t: Throwable) {
            toast("បើក overlay មិនបាន: ${t.message ?: t.javaClass.simpleName}")
        }
        refresh()
    }

    private fun stopOverlay() {
        stopService(Intent(this, OverlayService::class.java))
        refresh()
    }

    private fun hasOverlayPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)

    private fun requestOverlayPermission() {
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName"))
        try {
            startActivityForResult(intent, REQ_OVERLAY)
        } catch (t: Throwable) {
            // ឧបករណ៍ខ្លះមិនគាំទ្រ package URI — បើកទំព័រទូទៅជំនួស
            try {
                startActivityForResult(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION), REQ_OVERLAY)
            } catch (t2: Throwable) {
                toast("បើក Settings មិនបាន — សូមបើក «Display over other apps» ដោយដៃ")
            }
        }
    }

    private fun requestNotificationPermissionOnce() {
        if (askedNotifications || Build.VERSION.SDK_INT < 33) return
        if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED) return
        askedNotifications = true
        requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIFICATIONS)
    }

    private fun openBatterySettings() {
        val app = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        try {
            startActivity(app)
            return
        } catch (ignored: Throwable) {
            // បន្តទៅ app details
        }
        try {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:$packageName")))
        } catch (t: Throwable) {
            toast("បើក Settings មិនបាន")
        }
    }

    private fun primaryButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        textSize = 16f
        setTextColor(Color.WHITE)
        background = rounded(Color.parseColor("#1F6FEB"), Color.parseColor("#1F6FEB"))
        setOnClickListener { action() }
    }

    private fun secondaryButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        textSize = 15f
        setTextColor(Color.WHITE)
        background = rounded(Color.parseColor("#21262D"), Color.parseColor("#30363D"))
        setOnClickListener { action() }
    }

    private fun rounded(fill: Int, stroke: Int) = GradientDrawable().apply {
        cornerRadius = dp(12).toFloat()
        setColor(fill)
        setStroke(dp(1), stroke)
    }

    private fun matchWidth() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)

    private fun matchWidthTop(top: Int) = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
        topMargin = dp(top)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    companion object {
        private const val REQ_OVERLAY = 1001
        private const val REQ_NOTIFICATIONS = 1002
    }
}
