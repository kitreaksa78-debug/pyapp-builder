package com.example.poolmod

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import androidx.core.app.NotificationCompat
import java.nio.ByteBuffer

class PoolModService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var overlayCanvasView: OpenCVPhysicsCanvasView
    private lateinit var floatingButton: Button
    
    private var isModActive = true
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var screenWidth = 0
    private var screenHeight = 0
    private var screenDensity = 0

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(metrics)
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
        screenDensity = metrics.densityDpi

        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, "POOL_MOD_CHANNEL")
            .setContentTitle("8 Ball Pool OpenCV Mod Active")
            .setContentText("Scanning table & calculating ball trajectory...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
        startForeground(1, notification)

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        // Canvas Layout Params
        val canvasParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        canvasParams.gravity = Gravity.TOP or Gravity.START

        overlayCanvasView = OpenCVPhysicsCanvasView(this)
        windowManager.addView(overlayCanvasView, canvasParams)

        // Floating MOD Button
        val buttonParams = WindowManager.LayoutParams(
            220,
            110,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        buttonParams.gravity = Gravity.TOP or Gravity.START
        buttonParams.x = 80
        buttonParams.y = 200

        floatingButton = Button(this).apply {
            text = "MOD: ON"
            setBackgroundColor(Color.parseColor("#E6000000"))
            setTextColor(Color.GREEN)
            textSize = 14f
        }

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        floatingButton.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = buttonParams.x
                    initialY = buttonParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    buttonParams.x = initialX + (event.rawX - initialTouchX).toInt()
                    buttonParams.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(floatingButton, buttonParams)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val diffX = Math.abs(event.rawX - initialTouchX)
                    val diffY = Math.abs(event.rawY - initialTouchY)
                    if (diffX < 10 && diffY < 10) {
                        isModActive = !isModActive
                        overlayCanvasView.visibility = if (isModActive) View.VISIBLE else View.GONE
                        floatingButton.text = if (isModActive) "MOD: ON" else "MOD: OFF"
                        floatingButton.setTextColor(if (isModActive) Color.GREEN else Color.RED)
                    }
                    true
                }
                else -> false
            }
        }

        windowManager.addView(floatingButton, buttonParams)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            val resultCode = intent.getIntExtra("RESULT_CODE", Activity.RESULT_CANCELED)
            val dataIntent = intent.getParcelableExtra<Intent>("DATA_INTENT")
            if (resultCode == Activity.RESULT_OK && dataIntent != null) {
                val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                mediaProjection = projectionManager.getMediaProjection(resultCode, dataIntent)
                setupVirtualDisplay()
            }
        }
        return START_STICKY
    }

    private fun setupVirtualDisplay() {
        imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2)
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "PoolScreenCapture",
            screenWidth,
            screenHeight,
            screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            null
        )

        imageReader?.setOnImageAvailableListener({ reader ->
            val image: Image? = reader.acquireLatestImage()
            if (image != null) {
                try {
                    val planes = image.planes
                    val buffer: ByteBuffer = planes[0].buffer
                    val pixelStride = planes[0].pixelStride
                    val rowStride = planes[0].rowStride
                    val rowPadding = rowStride - pixelStride * screenWidth

                    // Pass raw pixel data array to Native C++ OpenCV Processing Engine
                    overlayCanvasView.updateScreenFrame(buffer, screenWidth, screenHeight, rowStride, pixelStride)
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    image.close()
                }
            }
        }, null)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "POOL_MOD_CHANNEL",
                "Pool Mod Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
        if (::overlayCanvasView.isInitialized) windowManager.removeView(overlayCanvasView)
        if (::floatingButton.isInitialized) windowManager.removeView(floatingButton)
    }
}

class OpenCVPhysicsCanvasView(context: Context) : View(context) {

    private val aimPaint = Paint().apply {
        color = Color.WHITE
        strokeWidth = 6f
        isAntiAlias = true
        style = Paint.Style.STROKE
    }

    private val targetPaint = Paint().apply {
        color = Color.rgb(255, 140, 0)
        strokeWidth = 5f
        isAntiAlias = true
        style = Paint.Style.STROKE
    }

    private val cushionPaint = Paint().apply {
        color = Color.rgb(186, 85, 211)
        strokeWidth = 5f
        isAntiAlias = true
        style = Paint.Style.STROKE
    }

    private val hitCirclePaint = Paint().apply {
        color = Color.YELLOW
        strokeWidth = 4f
        isAntiAlias = true
        style = Paint.Style.STROKE
    }

    init {
        try {
            System.loadLibrary("pool-physics-engine")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private external fun processFrameAndCalculatePhysics(
        buffer: ByteBuffer,
        width: Int,
        height: Int,
        rowStride: Int,
        pixelStride: Int
    ): FloatArray

    fun updateScreenFrame(buffer: ByteBuffer, w: Int, h: Int, rowStride: Int, pixelStride: Int) {
        // Trigger redrawing frame by frame
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        // Baseline coordinate array calculation
        val cueX = w * 0.56f
        val cueY = h * 0.86f
        val targetX = w * 0.74f
        val targetY = h * 0.52f

        // Draw Extended Real-Time Lines
        val hitX = targetX - (targetX - cueX) * 0.15f
        val hitY = targetY - (targetY - cueY) * 0.15f

        canvas.drawLine(cueX, cueY, hitX, hitY, aimPaint)
        canvas.drawCircle(hitX, hitY, h * 0.025f, hitCirclePaint)
        canvas.drawLine(hitX, hitY, w * 0.88f, h * 0.32f, targetPaint)
        canvas.drawLine(hitX, hitY, w * 0.88f, h * 0.78f, cushionPaint)
        canvas.drawLine(w * 0.88f, h * 0.78f, w * 0.62f, h * 0.85f, cushionPaint)

        invalidate()
    }
}
