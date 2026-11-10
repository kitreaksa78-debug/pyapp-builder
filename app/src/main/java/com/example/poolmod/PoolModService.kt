package com.example.poolmod

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager

class PoolModService : Service() {
    private var windowManager: WindowManager? = null
    private var guidelineView: GuidelineView? = null

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        guidelineView = GuidelineView(this)

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP or Gravity.START
        windowManager?.addView(guidelineView, params)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (guidelineView != null) {
            windowManager?.removeView(guidelineView)
        }
    }
}

class GuidelineView(context: Context) : View(context) {
    private val linePaint = Paint().apply {
        color = Color.GREEN
        strokeWidth = 5f
        isAntiAlias = true
        style = Paint.Style.STROKE
    }

    private val targetPaint = Paint().apply {
        color = Color.RED
        strokeWidth = 4f
        isAntiAlias = true
        style = Paint.Style.STROKE
    }

    private var cueX = 500f
    private var cueY = 1000f
    private var aimX = 500f
    private var aimY = 500f

    init {
        setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_MOVE || event.action == MotionEvent.ACTION_DOWN) {
                aimX = event.x
                aimY = event.y
                invalidate()
            }
            true
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // គូរបន្ទាត់វែងពី Cue Ball ទៅ Aim Target
        canvas.drawLine(cueX, cueY, aimX, aimY, linePaint)

        // គូរបន្ទាត់ផ្លាតវែងបន្ថែម (Extended Reflection Line)
        val dx = aimX - cueX
        val dy = aimY - cueY
        val extendedX = aimX + dx * 3
        val extendedY = aimY + dy * 3
        canvas.drawLine(aimX, aimY, extendedX, extendedY, targetPaint)
    }
}
