package org.pyapp.aiphone

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import kotlin.math.abs

/**
 * AI Bubble — ប៉ូតុង "AI" អណ្តែតលើ app ដទៃ (ដូច MOD)។
 * ចុច → បើក AI Phone ហើយចាប់ផ្តើមស្តាប់សំឡេង។
 * អូស → ផ្លាស់ទី។ សង្កត់យូរ → បិទ។
 * បង្ហាញ "auto" ពណ៌ក្រហម ពេល model ជា auto (រើស model ស្វ័យប្រវត្តិ)។
 */
class AiBubbleService : Service() {

    companion object {
        @Volatile
        var running = false
            private set
    }

    private lateinit var wm: WindowManager
    private lateinit var lp: WindowManager.LayoutParams
    private var root: LinearLayout? = null
    private var prefs: SharedPreferences? = null
    private val ui = Handler(Looper.getMainLooper())
    private var longPress: Runnable? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = true
        prefs = getSharedPreferences("aiphone", Context.MODE_PRIVATE)
        startForegroundCompat()
        showBubble()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun showBubble() {
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.gravity = Gravity.CENTER_HORIZONTAL
        box.setPadding(dp(4), dp(4), dp(4), dp(4))

        val circle = TextView(this)
        circle.text = "AI"
        circle.setTextColor(Color.WHITE)
        circle.textSize = 17f
        circle.typeface = Typeface.DEFAULT_BOLD
        circle.gravity = Gravity.CENTER
        val cg = GradientDrawable()
        cg.shape = GradientDrawable.OVAL
        cg.setColor(0xFFE8A33D.toInt())
        cg.setStroke(dp(3), 0xFFFFE0A3.toInt())
        circle.background = cg
        val csz = dp(68)
        box.addView(circle, LinearLayout.LayoutParams(csz, csz))

        val auto = TextView(this)
        auto.text = "auto"
        auto.setTextColor(Color.WHITE)
        auto.textSize = 9f
        auto.typeface = Typeface.DEFAULT_BOLD
        auto.gravity = Gravity.CENTER
        auto.setPadding(dp(9), dp(2), dp(9), dp(2))
        val ag = GradientDrawable()
        ag.cornerRadius = dp(12).toFloat()
        ag.setColor(0xFFD93025.toInt())
        auto.background = ag
        val model = prefs?.getString("model", "auto") ?: "auto"
        auto.visibility = if (model.isEmpty() || model.equals("auto", true)) View.VISIBLE else View.GONE
        box.addView(auto, LinearLayout.LayoutParams(-2, -2).also {
            it.gravity = Gravity.CENTER_HORIZONTAL
            it.topMargin = dp(3)
        })

        val type = if (Build.VERSION.SDK_INT >= 26)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            WindowManager.LayoutParams.TYPE_PHONE

        lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        lp.x = dp(10)
        lp.y = dp(260)

        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var moved = false

        box.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX
                    downY = e.rawY
                    startX = lp.x
                    startY = lp.y
                    moved = false
                    longPress?.let { ui.removeCallbacks(it) }
                    val r = Runnable { if (!moved) stopSelf() }
                    longPress = r
                    ui.postDelayed(r, 900)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - downX).toInt()
                    val dy = (e.rawY - downY).toInt()
                    if (abs(dx) > dp(6) || abs(dy) > dp(6)) moved = true
                    if (moved) {
                        lp.x = startX + dx
                        lp.y = startY + dy
                        try { wm.updateViewLayout(box, lp) } catch (_: Throwable) {}
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    longPress?.let { ui.removeCallbacks(it) }
                    longPress = null
                    if (!moved && e.actionMasked == MotionEvent.ACTION_UP) openAndListen()
                    true
                }
                else -> false
            }
        }

        root = box
        try {
            wm.addView(box, lp)
        } catch (_: Throwable) {
            stopSelf()
        }
    }

    private fun openAndListen() {
        try {
            val i = Intent(this, OverlayChatActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra("overlay", true)
            startActivity(i)
        } catch (_: Throwable) {}
    }

    private fun startForegroundCompat() {
        val ch = "ai_bubble"
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(ch, "AI Bubble", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val open = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        val pi = PendingIntent.getActivity(this, 3, open, flags)
        val n = NotificationCompat.Builder(this, ch)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("AI Phone")
            .setContentText("ចុច AI ដើម្បីនិយាយ • សង្កត់យូរដើម្បីបិទ")
            .setOngoing(true)
            .setContentIntent(pi)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(7, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(7, n)
        }
    }

    override fun onDestroy() {
        running = false
        longPress?.let { ui.removeCallbacks(it) }
        try {
            root?.let { wm.removeView(it) }
        } catch (_: Throwable) {}
        root = null
        super.onDestroy()
    }
}
