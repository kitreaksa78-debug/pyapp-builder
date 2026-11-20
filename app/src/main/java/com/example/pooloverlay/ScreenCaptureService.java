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
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

public class ScreenCaptureService extends Service {

    private static final String TAG = "PoolOverlay";
    private static final String CHANNEL_ID = "pool_overlay_channel";
    private static final String API_URL = "https://pool-027f.onrender.com/api/calculate";
    private static final long AUTO_SCAN_INTERVAL_MS = 1500L;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private HandlerThread captureThread;
    private Handler captureHandler;

    private WindowManager windowManager;
    private OverlayView overlayView;
    private Button floatingScanBtn;

    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;

    private int captureWidth = 1600;
    private int captureHeight = 720;
    private int screenDensity = 320;

    private final AtomicBoolean isRequestInFlight = new AtomicBoolean(false);
    private boolean isAutoScanRunning = false;

    private final Runnable autoScanRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isAutoScanRunning) return;
            captureFrameAndSend(false);
            mainHandler.postDelayed(this, AUTO_SCAN_INTERVAL_MS);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        captureThread = new HandlerThread("ScreenCaptureThread");
        captureThread.start();
        captureHandler = new Handler(captureThread.getLooper());

        startForegroundNotification();
        setupOverlayViews();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && mediaProjection == null) {
            int resultCode = intent.getIntExtra("RESULT_CODE", Activity.RESULT_CANCELED);
            Intent data = intent.getParcelableExtra("DATA");
            if (resultCode == Activity.RESULT_OK && data != null) {
                startMediaProjection(resultCode, data);
            } else {
                Log.e(TAG, "Missing MediaProjection resultCode or data Intent");
            }
        }
        return START_STICKY;
    }

    private void startForegroundNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Pool Overlay Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Pool Overlay Robot")
                .setContentText("កំពុងថតអេក្រង់ និងគណនាគន្លងបាញ់")
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .build();

        startForeground(1001, notification);
    }

    private void setupOverlayViews() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (windowManager == null) return;

        int layoutFlag = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        // ១. ផ្ទាំង Overlay ថ្លាពេញអេក្រង់សម្រាប់គូរបន្ទាត់ (មិនរារាំងការចុចលេងហ្គេម)
        overlayView = new OverlayView(this);
        WindowManager.LayoutParams lineParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
        lineParams.gravity = Gravity.TOP | Gravity.START;
        windowManager.addView(overlayView, lineParams);

        // ២. ប៊ូតុង SCAN អណ្តែតខាងស្តាំលើ (សម្រាប់ចុច Scan ភ្លាមៗក្នុងហ្គេម)
        floatingScanBtn = new Button(this);
        floatingScanBtn.setText("SCAN API");
        floatingScanBtn.setTextColor(Color.WHITE);
        floatingScanBtn.setBackgroundColor(Color.parseColor("#16A34A"));
        floatingScanBtn.setPadding(28, 14, 28, 14);
        floatingScanBtn.setOnClickListener(v -> captureFrameAndSend(true));

        WindowManager.LayoutParams btnParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
        btnParams.gravity = Gravity.TOP | Gravity.END;
        btnParams.x = 24;
        btnParams.y = 80;
        windowManager.addView(floatingScanBtn, btnParams);
    }

    private void startMediaProjection(int resultCode, Intent data) {
        MediaProjectionManager projectionManager =
                (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        if (projectionManager == null) return;

        mediaProjection = projectionManager.getMediaProjection(resultCode, data);
        if (mediaProjection == null) return;

        mediaProjection.registerCallback(new MediaProjection.Callback() {
            @Override
            public void onStop() {
                super.onStop();
                stopAutoScan();
                releaseVirtualDisplay();
            }
        }, mainHandler);

        initVirtualDisplay();
        startAutoScan();
    }

    private void initVirtualDisplay() {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        screenDensity = metrics.densityDpi;
        int w = metrics.widthPixels;
        int h = metrics.heightPixels;

        // ធានាថា Landscape (ទទឹង > បណ្តោយ) ឬប្រើទំហំអេក្រង់ពិត
        captureWidth = w;
        captureHeight = h;

        imageReader = ImageReader.newInstance(captureWidth, captureHeight, PixelFormat.RGBA_8888, 2);
        virtualDisplay = mediaProjection.createVirtualDisplay(
                "PoolScreenCapture",
                captureWidth,
                captureHeight,
                screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(),
                null,
                captureHandler
        );
    }

    private void startAutoScan() {
        if (!isAutoScanRunning) {
            isAutoScanRunning = true;
            mainHandler.postDelayed(autoScanRunnable, 800L);
        }
    }

    private void stopAutoScan() {
        isAutoScanRunning = false;
        mainHandler.removeCallbacks(autoScanRunnable);
    }

    public void captureFrameAndSend(final boolean showToastOnError) {
        if (imageReader == null) {
            if (showToastOnError) {
                Toast.makeText(getApplicationContext(), "Screen capture មិនទាន់រួចរាល់", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        if (!isRequestInFlight.compareAndSet(false, true)) {
            // កំពុងផ្ញើ frame មុនទៅ Server នៅឡើយ សូមរង់ចាំបន្តិច
            return;
        }

        captureHandler.post(() -> {
            Image image = null;
            Bitmap cleanBitmap = null;
            try {
                image = imageReader.acquireLatestImage();
                if (image == null) {
                    isRequestInFlight.set(false);
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
                        imgWidth + rowPadding / pixelStride,
                        imgHeight,
                        Bitmap.Config.ARGB_8888
                );
                rawBitmap.copyPixelsFromBuffer(buffer);
                cleanBitmap = Bitmap.createBitmap(rawBitmap, 0, 0, imgWidth, imgHeight);
                rawBitmap.recycle();

                // ប្តូរទំហំទៅ 1600x720 ឱ្យត្រូវនឹងការកំណត់ OpenCV ក្នុង app.py (បើអេក្រង់ជា Landscape)
                final int sentW = (imgWidth >= imgHeight) ? 1600 : 720;
                final int sentH = (imgWidth >= imgHeight) ? 720 : 1600;
                Bitmap scaledBitmap = Bitmap.createScaledBitmap(cleanBitmap, sentW, sentH, true);
                if (scaledBitmap != cleanBitmap) {
                    cleanBitmap.recycle();
                }

                sendToAPI(scaledBitmap, sentW, sentH, showToastOnError);
            } catch (Exception e) {
                Log.e(TAG, "captureFrameAndSend error: " + e.getMessage(), e);
                isRequestInFlight.set(false);
            } finally {
                if (image != null) {
                    image.close();
                }
            }
        });
    }

    public void sendToAPI(final Bitmap bitmap) {
        sendToAPI(bitmap, bitmap != null ? bitmap.getWidth() : 1600, bitmap != null ? bitmap.getHeight() : 720, true);
    }

    public void sendToAPI(final Bitmap bitmap, final int sentWidth, final int sentHeight, final boolean showToastOnError) {
        if (bitmap == null) {
            isRequestInFlight.set(false);
            return;
        }

        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 75, baos);
                bitmap.recycle();
                String base64Image = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);

                JSONObject payload = new JSONObject();
                payload.put("image", "data:image/jpeg;base64," + base64Image);

                URL url = new URL(API_URL);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(20000);
                conn.setDoOutput(true);
                conn.setDoInput(true);
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setRequestProperty("Accept", "application/json");

                byte[] inputBytes = payload.toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(inputBytes);
                    os.flush();
                }

                int responseCode = conn.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    InputStream is = conn.getInputStream();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();

                    try {
                        JSONObject jsonResponse = new JSONObject(response.toString());

                        if ("success".equals(jsonResponse.getString("status"))) {
                            final double rawStartX = jsonResponse.getDouble("startX");
                            final double rawStartY = jsonResponse.getDouble("startY");
                            final double rawEndX = jsonResponse.getDouble("endX");
                            final double rawEndY = jsonResponse.getDouble("endY");

                            mainHandler.post(() -> {
                                // គណនា Scale ពីទំហំរូបភាពដែលផ្ញើទៅ (1600x720) មកទំហំ OverlayView ពិតលើអេក្រង់
                                float viewW = (overlayView != null && overlayView.getWidth() > 0) ? overlayView.getWidth() : sentWidth;
                                float viewH = (overlayView != null && overlayView.getHeight() > 0) ? overlayView.getHeight() : sentHeight;
                                float scaleX = viewW / (float) sentWidth;
                                float scaleY = viewH / (float) sentHeight;

                                int startX = Math.round((float) rawStartX * scaleX);
                                int startY = Math.round((float) rawStartY * scaleY);
                                int endX = Math.round((float) rawEndX * scaleX);
                                int endY = Math.round((float) rawEndY * scaleY);

                                drawLineOnOverlay(startX, startY, endX, endY);
                            });
                        } else {
                            final String errorMsg = jsonResponse.optString("message", "Unknown error");
                            mainHandler.post(() -> {
                                if (showToastOnError) {
                                    Toast.makeText(getApplicationContext(),
                                            "API: " + errorMsg, Toast.LENGTH_SHORT).show();
                                }
                                drawLineOnOverlay(0, 0, 0, 0);
                            });
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else {
                    Log.e(TAG, "HTTP Error Code: " + responseCode);
                }

            } catch (Exception e) {
                Log.e(TAG, "sendToAPI failed: " + e.getMessage(), e);
            } finally {
                isRequestInFlight.set(false);
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }).start();
    }

    private void drawLineOnOverlay(int startX, int startY, int endX, int endY) {
        Log.d(TAG, "Draw line from (" + startX + ", " + startY + ") to (" + endX + ", " + endY + ")");
        if (overlayView != null) {
            if (startX == 0 && startY == 0 && endX == 0 && endY == 0) {
                overlayView.clearLine();
            } else {
                overlayView.setAimLine(startX, startY, endX, endY);
            }
        }
    }

    private void releaseVirtualDisplay() {
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopAutoScan();
        releaseVirtualDisplay();
        if (mediaProjection != null) {
            mediaProjection.stop();
            mediaProjection = null;
        }
        if (windowManager != null) {
            if (overlayView != null) {
                windowManager.removeView(overlayView);
                overlayView = null;
            }
            if (floatingScanBtn != null) {
                windowManager.removeView(floatingScanBtn);
                floatingScanBtn = null;
            }
        }
        if (captureThread != null) {
            captureThread.quitSafely();
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
