package com.example.poolfloatingoverlay

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs

class OverlayService : Service() {
    private lateinit var wm: WindowManager
    private var bubble: View? = null
    private var panel: View? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        showBubble()
    }

    private fun params(width: Int, height: Int, gravity: Int = Gravity.TOP or Gravity.START): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            width, height,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            this.gravity = gravity
            x = 24
            y = 220
        }
    }

    private fun showBubble() {
        if (bubble != null) return
        val button = TextView(this).apply {
            text = "POOL"
            textSize = 13f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setBackgroundColor(Color.rgb(25, 110, 190))
            setPadding(18, 14, 18, 14)
            elevation = 12f
        }
        val lp = params( WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT)
        button.setOnTouchListener(object : View.OnTouchListener {
            var downX = 0f
            var downY = 0f
            var startX = 0
            var startY = 0
            var moved = false
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = event.rawX; downY = event.rawY
                        startX = lp.x; startY = lp.y; moved = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - downX).toInt()
                        val dy = (event.rawY - downY).toInt()
                        if (abs(dx) > 5 || abs(dy) > 5) moved = true
                        lp.x = startX + dx; lp.y = startY + dy
                        wm.updateViewLayout(button, lp)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!moved) togglePanel()
                        return true
                    }
                }
                return false
            }
        })
        bubble = button
        wm.addView(button, lp)
    }

    private fun togglePanel() {
        if (panel != null) {
            wm.removeView(panel)
            panel = null
            return
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 16, 18, 16)
            setBackgroundColor(Color.rgb(28, 32, 42))
            elevation = 16f
        }
        val heading = TextView(this).apply {
            text = "Practice Tools"
            textSize = 18f
            setTextColor(Color.WHITE)
            setPadding(4, 4, 4, 12)
        }
        box.addView(heading)
        addAction(box, "Aim practice: ON/OFF") {
            Toast.makeText(this, "Practice reminder only — no game overlay or auto-aim", Toast.LENGTH_SHORT).show()
        }
        addAction(box, "Power practice: ON/OFF") {
            Toast.makeText(this, "Power setting is a practice note only", Toast.LENGTH_SHORT).show()
        }
        addAction(box, "Close menu") { togglePanel() }
        val p = params(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT)
        p.x = 24; p.y = 290
        panel = box
        wm.addView(box, p)
    }

    private fun addAction(parent: LinearLayout, label: String, action: () -> Unit) {
        val item = TextView(this).apply {
            text = label
            textSize = 15f
            setTextColor(Color.WHITE)
            setPadding(12, 12, 12, 12)
            setOnClickListener { action() }
        }
        parent.addView(item)
    }

    override fun onDestroy() {
        panel?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        bubble?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        panel = null
        bubble = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
