package com.kitreaksa.floatingline

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * Overlay ពិតប្រាកដដែលគូរបន្ទាត់ហាត់ និងផ្ទាំងបញ្ជាលើ app ផ្សេង។
 *
 * - Android 8.0+ : TYPE_APPLICATION_OVERLAY (ប្រភេទចាំបាច់សម្រាប់ overlay)
 * - Android 8.0-: TYPE_PHONE (legacy)
 * - បន្ទាត់ជា window ដែលមិនចាប់ touch (FLAG_NOT_TOUCHABLE) ⇒ ចុចលេង app ក្រោមបានធម្មតា;
 *   អូសបន្ទាត់តាមរង្វង់ handle តូចនៅកណ្តាល។
 * - service ដំណើរការជា foreground service ដើម្បីកុំឲ្យប្រព័ន្ធសម្លាប់ពេលប្តូរ app។
 *
 * ប្រអប់បញ្ជា៖ ⠿ អូស · ⇅ បង្វិល · ＋/－ កម្រាស់ · 👁 បង្ហាញ/លាក់ · ✕ បិទ
 */
class OverlayService : Service() {

    companion object {
        const val ACTION_STOP = "com.kitreaksa.floatingline.action.STOP"
        private const val CHANNEL_ID = "floating_line"
        private const val NOTIFICATION_ID = 41
        private const val BAND_DP = 84
        private const val HANDLE_DP = 46
    }

    private lateinit var windowManager: WindowManager
    private var lineView: LineView? = null
    private var lineParams: WindowManager.LayoutParams? = null
    private var handleView: TextView? = null
    private var handleParams: WindowManager.LayoutParams? = null
    private var panelView: LinearLayout? = null
    private var vertical = false
    private var thickness = 5f

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForegroundSafely()
        try {
            addLine()
            addPanel()
        } catch (t: Throwable) {
            Toast.makeText(this, "បង្ហាញ overlay មិនបាន: ${t.message}", Toast.LENGTH_LONG).show()
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        // START_NOT_STICKY: overlay មិនគួររស់ឡើងវិញដោយខ្លួនឯងពី background
        // (Android 12+ ហាម startForegroundService ពី background ⇒ អាច crash)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        removeViewSafely(lineView)
        removeViewSafely(handleView)
        removeViewSafely(panelView)
        lineView = null
        handleView = null
        panelView = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        super.onDestroy()
    }

    // ---------------------------------------------------------------- line

    private fun addLine() {
        if (lineView != null) return
        val band = dp(BAND_DP)
        val screenW = resources.displayMetrics.widthPixels
        val screenH = resources.displayMetrics.heightPixels

        val params = lineParams ?: WindowManager.LayoutParams(
            0, 0, overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = screenH / 2 - band / 2
        }
        if (vertical) {
            params.width = band
            params.height = WindowManager.LayoutParams.MATCH_PARENT
            params.x = screenW / 2 - band / 2
            params.y = 0
        } else {
            params.width = WindowManager.LayoutParams.MATCH_PARENT
            params.height = band
            params.x = 0
        }

        val view = LineView(this).apply {
            isVertical = vertical
            lineThickness = thickness
        }
        windowManager.addView(view, params)
        lineView = view
        lineParams = params
        addHandle()
    }

    private fun addHandle() {
        if (handleView != null) return
        val size = dp(HANDLE_DP)
        val handle = TextView(this).apply {
            text = "⠿"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#B300E5FF"))
                setStroke(dp(2), Color.WHITE)
            }
            elevation = dp(6).toFloat()
        }
        val params = WindowManager.LayoutParams(
            size, size, overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.START
        }
        attachDrag(handle, params)
        placeHandleCentered(params, size)
        windowManager.addView(handle, params)
        handleView = handle
        handleParams = params
    }

    /** ដាក់ handle នៅកណ្តាលបន្ទាត់។ */
    private fun placeHandleCentered(params: WindowManager.LayoutParams, size: Int) {
        val band = dp(BAND_DP)
        val screenW = resources.displayMetrics.widthPixels
        val screenH = resources.displayMetrics.heightPixels
        val line = lineParams ?: return
        if (vertical) {
            params.x = line.x + band / 2 - size / 2
            params.y = screenH / 2 - size / 2
        } else {
            params.x = screenW / 2 - size / 2
            params.y = line.y + band / 2 - size / 2
        }
    }

    private fun toggleLineVisibility() {
        if (lineView != null) {
            removeViewSafely(lineView)
            removeViewSafely(handleView)
            lineView = null
            handleView = null
        } else {
            addLine()
        }
    }

    private fun rotateLine() {
        vertical = !vertical
        removeViewSafely(lineView)
        removeViewSafely(handleView)
        lineView = null
        handleView = null
        // បង្វិលបន្ទាត់ → ដាក់កណ្តាលអេក្រង់វិញ ដើម្បីកុំឲ្យធ្លាក់ក្រៅអេក្រង់
        lineParams = null
        addLine()
    }

    private fun resizeLine(delta: Float) {
        thickness = (thickness + delta).coerceIn(2f, 32f)
        lineView?.let {
            it.lineThickness = thickness
            it.invalidate()
        }
    }

    // --------------------------------------------------------------- panel

    private fun addPanel() {
        if (panelView != null) return
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
            background = GradientDrawable().apply {
                cornerRadius = dp(20).toFloat()
                setColor(Color.parseColor("#E60D1117"))
                setStroke(dp(1), Color.parseColor("#30363D"))
            }
            elevation = dp(8).toFloat()
        }
        val handle = TextView(this).apply {
            text = "⠿"
            textSize = 18f
            setTextColor(Color.parseColor("#8B949E"))
            setPadding(dp(10), dp(6), dp(10), dp(6))
        }
        row.addView(handle)
        row.addView(panelButton("⇅") { rotateLine() })
        row.addView(panelButton("＋") { resizeLine(2f) })
        row.addView(panelButton("－") { resizeLine(-2f) })
        row.addView(panelButton("👁") { toggleLineVisibility() })
        row.addView(panelButton("✕") { stopSelf() })

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(12)
            y = dp(96)
        }
        attachDrag(handle, params)
        windowManager.addView(row, params)
        panelView = row
    }

    private fun panelButton(label: String, action: () -> Unit): TextView =
        TextView(this).apply {
            text = label
            textSize = 17f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(dp(9), dp(6), dp(9), dp(6))
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(Color.parseColor("#21262D"))
            }
            setOnClickListener { action() }
        }

    // --------------------------------------------------------------- drag

    private fun attachDrag(view: View, params: WindowManager.LayoutParams) {
        view.setOnTouchListener(object : View.OnTouchListener {
            private var startX = 0f
            private var startY = 0f
            private var originX = 0
            private var originY = 0

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = event.rawX
                        startY = event.rawY
                        originX = params.x
                        originY = params.y
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = originX + (event.rawX - startX).toInt()
                        params.y = originY + (event.rawY - startY).toInt()
                        try {
                            windowManager.updateViewLayout(v, params)
                        } catch (ignored: Throwable) {
                            // window ត្រូវបានដកចេញ — មិនមានអ្វីត្រូវធ្វើ
                        }
                        if (v === handleView) moveLineWithHandle(params)
                        return true
                    }
                }
                return true
            }
        })
    }

    /** អូស handle → បន្ទាត់ធ្វើតាម (handle នៅកណ្តាលបន្ទាត់ជានិច្ច)។ */
    private fun moveLineWithHandle(params: WindowManager.LayoutParams) {
        val line = lineParams ?: return
        val band = dp(BAND_DP)
        val size = dp(HANDLE_DP)
        if (vertical) {
            line.x = params.x + size / 2 - band / 2
            params.y = resources.displayMetrics.heightPixels / 2 - size / 2
        } else {
            line.y = params.y + size / 2 - band / 2
            params.x = resources.displayMetrics.widthPixels / 2 - size / 2
        }
        val handle = handleView ?: return
        try {
            lineView?.let { windowManager.updateViewLayout(it, line) }
            windowManager.updateViewLayout(handle, params)
        } catch (ignored: Throwable) {
            // window ត្រូវបានដកចេញ
        }
    }

    // -------------------------------------------------------- notification

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID, "Floating line", NotificationManager.IMPORTANCE_LOW)
        channel.description = "បន្ទាត់ហាត់អណ្តែត និងផ្ទាំងបញ្ជា"
        manager.createNotificationChannel(channel)
    }

    private fun notification(): Notification {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), flags)
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, OverlayService::class.java).setAction(ACTION_STOP), flags)
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setContentTitle("Floating Line Trainer")
            .setContentText("បន្ទាត់ហាត់កំពុងបង្ហាញលើ app ផ្សេង")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, "បិទ", stop)
            .build()
    }

    private fun startForegroundSafely() {
        val notification = notification()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    // --------------------------------------------------------------- utils

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

    private fun removeViewSafely(view: View?) {
        if (view == null) return
        try {
            windowManager.removeView(view)
        } catch (ignored: Throwable) {
            // មិននៅក្នុង window manager ហើយ
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}

/** បន្ទាត់ហាត់ — ខ្យល់ពណ៌ cyan ជាមួយចំណុចកណ្តាលសម្រាប់មើលការតម្រឹម។ */
class LineView(context: Context) : View(context) {
    var isVertical = false
    var lineThickness = 5f

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E5FF")
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3300E5FF")
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val thick = lineThickness * resources.displayMetrics.density
        if (isVertical) {
            val cx = width / 2f
            canvas.drawRect(cx - thick / 2f, 0f, cx + thick / 2f, height.toFloat(), linePaint)
            canvas.drawCircle(cx, height / 2f, thick * 2.2f, glowPaint)
        } else {
            val cy = height / 2f
            canvas.drawRect(0f, cy - thick / 2f, width.toFloat(), cy + thick / 2f, linePaint)
            canvas.drawCircle(width / 2f, cy, thick * 2.2f, glowPaint)
        }
    }
}
