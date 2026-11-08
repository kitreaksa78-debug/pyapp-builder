package com.example.poolmodmenu

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
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs

class ModMenuService : Service() {
    private lateinit var wm: WindowManager
    private var floatingIcon: View? = null
    private var modMenuPanel: View? = null
    private var aimCanvasView: MultiBounceAimView? = null
    private var isAimEnabled = true

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        showFloatingIcon()
        showAimOverlay()
    }

    private fun getParams(width: Int, height: Int, touchable: Boolean = true): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val flags = if (touchable) {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        } else {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        }

        return WindowManager.LayoutParams(width, height, type, flags, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 50
            y = 150
        }
    }

    private fun showFloatingIcon() {
        val icon = TextView(this).apply {
            text = "MOD"
            textSize = 14f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#CC1E293B"))
            setPadding(26, 16, 26, 16)
            elevation = 12f
        }

        val lp = getParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT)
        icon.setOnTouchListener(object : View.OnTouchListener {
            var downX = 0f; var downY = 0f; var startX = 0; var startY = 0; var moved = false
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
                        wm.updateViewLayout(icon, lp)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!moved) toggleMenuPanel()
                        return true
                    }
                }
                return false
            }
        })
        floatingIcon = icon
        wm.addView(icon, lp)
    }

    private fun toggleMenuPanel() {
        if (modMenuPanel != null) {
            wm.removeView(modMenuPanel)
            modMenuPanel = null
            return
        }

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#EE0F172A"))
            setPadding(32, 32, 32, 32)
            elevation = 20f
        }

        val title = TextView(this).apply {
            text = "8 Ball Pool Mod Menu"
            textSize = 16f
            setTextColor(Color.parseColor("#38BDF8"))
            setPadding(0, 0, 0, 16)
        }
        panel.addView(title)

        val switchAim = Switch(this).apply {
            text = "Multi-Bounce Aim Lines"
            setTextColor(Color.WHITE)
            isChecked = isAimEnabled
            setOnCheckedChangeListener { _, isChecked ->
                isAimEnabled = isChecked
                aimCanvasView?.visibility = if (isChecked) View.VISIBLE else View.GONE
                Toast.makeText(context, "Aim Lines: ${if (isChecked) "ON" else "OFF"}", Toast.LENGTH_SHORT).show()
            }
        }
        panel.addView(switchAim)

        val closeBtn = TextView(this).apply {
            text = "Close Menu"
            setTextColor(Color.parseColor("#F87171"))
            setPadding(0, 20, 0, 0)
            setOnClickListener { toggleMenuPanel() }
        }
        panel.addView(closeBtn)

        val lp = getParams(650, 420)
        lp.x = 150; lp.y = 250
        modMenuPanel = panel
        wm.addView(panel, lp)
    }

    private fun showAimOverlay() {
        aimCanvasView = MultiBounceAimView(this)
        val lp = getParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, touchable = false)
        lp.x = 0; lp.y = 0
        wm.addView(aimCanvasView, lp)
    }

    override fun onDestroy() {
        floatingIcon?.let { wm.removeView(it) }
        modMenuPanel?.let { wm.removeView(it) }
        aimCanvasView?.let { wm.removeView(it) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
