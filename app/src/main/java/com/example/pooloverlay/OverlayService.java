package com.example.pooloverlay;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class OverlayService extends Service {

    private static final String API_URL = "https://pool-027f.onrender.com/api/calculate";

    private WindowManager windowManager;
    private DrawingView drawingView;
    private Button scanButton;

    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private int screenWidth, screenHeight, screenDensity;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    public void onCreate() {
        super.onCreate();
        startForegroundNotification();

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        updateScreenMetrics();
        setupOverlayViews();
    }

    private void updateScreenMetrics() {
        DisplayMetrics metrics = new DisplayMetrics();
        if (windowManager != null && windowManager.getDefaultDisplay() != null) {
            windowManager.getDefaultDisplay().getRealMetrics(metrics);
        }
        screenWidth = metrics.widthPixels;
        screenHeight = metrics.heightPixels;
        screenDensity = metrics.densityDpi;
    }

    private void startForegroundNotification() {
        String channelId = "pool_overlay_channel";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId, "Pool Overlay Service", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, channelId)
                : new Notification.Builder(this);

        Notification notification = builder
                .setContentTitle("8 Ball Pool Robot")
                .setContentText("Ready: https://pool-027f.onrender.com/api/calculate")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .build();

        startForeground(1, notification);
    }

    private void setupOverlayViews() {
        int layoutType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        drawingView = new DrawingView(this);
        WindowManager.LayoutParams drawParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        windowManager.addView(drawingView, drawParams);

        scanButton = new Button(this);
        scanButton.setText("SCAN API");
        scanButton.setBackgroundColor(Color.parseColor("#CC00C853"));
        scanButton.setTextColor(Color.WHITE);

        WindowManager.LayoutParams btnParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        btnParams.gravity = Gravity.TOP | Gravity.END;
        btnParams.x = 30;
        btnParams.y = 120;

        scanButton.setOnClickListener(v -> captureAndSendToApi());
        windowManager.addView(scanButton, btnParams);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.hasExtra("DATA")) {
            int resultCode = intent.getIntExtra("RESULT_CODE", Activity.RESULT_CANCELED);
            Intent data = intent.getParcelableExtra("DATA");
            MediaProjectionManager mgr = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            if (mgr != null && data != null) {
                mediaProjection = mgr.getMediaProjection(resultCode, data);
                updateScreenMetrics();
                setupVirtualDisplay();
            }
        }
        return START_NOT_STICKY;
    }

    private void setupVirtualDisplay() {
        if (mediaProjection == null || screenWidth <= 0 || screenHeight <= 0) return;
        if (virtualDisplay != null) virtualDisplay.release();
        if (imageReader != null) imageReader.close();

        imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2);
        virtualDisplay = mediaProjection.createVirtualDisplay(
                "PoolScreenCapture", screenWidth, screenHeight, screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(), null, null);
    }

    private void captureAndSendToApi() {
        if (imageReader == null) {
            Toast.makeText(this, "Screen capture not ready", Toast.LENGTH_SHORT).show();
            return;
        }

        mainHandler.post(() -> scanButton.setText("SENDING..."));

        Image image = imageReader.acquireLatestImage();
        if (image == null) {
            mainHandler.post(() -> {
                scanButton.setText("SCAN API");
                Toast.makeText(this, "No frame available, move screen or try again", Toast.LENGTH_SHORT).show();
            });
            return;
        }

        int imgWidth = image.getWidth();
        int imgHeight = image.getHeight();

        Image.Plane[] planes = image.getPlanes();
        ByteBuffer buffer = planes[0].getBuffer();
        int pixelStride = planes[0].getPixelStride();
        int rowStride = planes[0].getRowStride();
        int rowPadding = rowStride - pixelStride * imgWidth;

        Bitmap rawBitmap = Bitmap.createBitmap(
                imgWidth + rowPadding / pixelStride, imgHeight, Bitmap.Config.ARGB_8888);
        rawBitmap.copyPixelsFromBuffer(buffer);
        image.close();

        Bitmap screenBitmap = Bitmap.createBitmap(rawBitmap, 0, 0, imgWidth, imgHeight);
        rawBitmap.recycle();

        executor.execute(() -> {
            try {
                ApiClient.ApiResult result = ApiClient.sendImage(API_URL, screenBitmap, imgWidth, imgHeight);
                screenBitmap.recycle();

                if (result.statusCode == 200 && result.json != null) {
                    JSONArray shots = ApiClient.extractShotsArray(result.json);
                    JSONObject lines = ApiClient.extractLinesObject(result.json);
                    final int shotCount = shots != null ? shots.length() : 0;

                    mainHandler.post(() -> {
                        Toast.makeText(OverlayService.this,
                                "API OK (HTTP 200) | shots=" + shotCount,
                                Toast.LENGTH_SHORT).show();
                        if (shots != null) {
                            drawingView.updateShots(shots);
                        } else if (lines != null) {
                            drawingView.updateFromLinesObject(lines);
                        }
                    });
                } else {
                    String msg = result.rawBody != null ? result.rawBody : "";
                    final String shortMsg = msg.length() > 100 ? msg.substring(0, 100) + "..." : msg;
                    mainHandler.post(() -> Toast.makeText(
                            OverlayService.this, "HTTP " + result.statusCode + ": " + shortMsg, Toast.LENGTH_LONG).show());
                }
            } catch (Exception e) {
                e.printStackTrace();
                mainHandler.post(() -> Toast.makeText(
                        OverlayService.this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            } finally {
                if (!screenBitmap.isRecycled()) {
                    screenBitmap.recycle();
                }
                mainHandler.post(() -> scanButton.setText("SCAN API"));
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (drawingView != null) {
            try { windowManager.removeView(drawingView); } catch (Exception ignored) {}
        }
        if (scanButton != null) {
            try { windowManager.removeView(scanButton); } catch (Exception ignored) {}
        }
        if (virtualDisplay != null) virtualDisplay.release();
        if (mediaProjection != null) mediaProjection.stop();
        executor.shutdown();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
