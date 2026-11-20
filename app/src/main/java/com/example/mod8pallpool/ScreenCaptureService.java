package com.example.mod8pallpool;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
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
import android.view.WindowManager;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class ScreenCaptureService extends Service {

    private static final String TAG = "ScreenCaptureService";
    private static final String CHANNEL_ID = "screen_capture_channel";
    private static final int NOTIFICATION_ID = 1001;
    private static final String API_URL = "https://pool-027f.onrender.com/api/calculate";
    private static final long CAPTURE_INTERVAL_MS = 1500L; // ~1.5 seconds between captures

    public static final String EXTRA_RESULT_CODE = "extra_result_code";
    public static final String EXTRA_RESULT_DATA = "extra_result_data";

    private WindowManager windowManager;
    private OverlayView overlayView;

    private MediaProjectionManager projectionManager;
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;

    private int screenWidth;
    private int screenHeight;
    private int screenDensity;

    private HandlerThread captureThread;
    private Handler captureHandler;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService networkExecutor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean isRequestInFlight = new AtomicBoolean(false);
    private boolean isRunning = false;

    private final MediaProjection.Callback projectionCallback = new MediaProjection.Callback() {
        @Override
        public void onStop() {
            super.onStop();
            stopCaptureLoop();
            cleanupProjection();
            stopSelf();
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        projectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        initScreenMetrics();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForegroundWithNotification();

        if (intent == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, -1);
        Intent resultData = intent.getParcelableExtra(EXTRA_RESULT_DATA);

        if (resultCode == 0 || resultData == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        if (!isRunning) {
            setupOverlayView();
            setupMediaProjection(resultCode, resultData);
            startCaptureLoop();
            isRunning = true;
        }

        return START_STICKY;
    }

    private void initScreenMetrics() {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        screenWidth = metrics.widthPixels;
        screenHeight = metrics.heightPixels;
        screenDensity = metrics.densityDpi;
    }

    private void startForegroundWithNotification() {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager != null) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Screen Overlay Capture",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Captures screen frames for overlay calculation");
            manager.createNotificationChannel(channel);
        }

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Overlay Active")
                .setContentText("Capturing screen and updating overlay...")
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setOngoing(true)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            );
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void setupOverlayView() {
        if (overlayView != null) return;

        overlayView = new OverlayView(this);

        // FLAG_NOT_FOCUSABLE & FLAG_NOT_TOUCHABLE let touches pass through to games/apps underneath
        int layoutFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                layoutFlags,
                PixelFormat.TRANSLUCENT
        );

        windowManager.addView(overlayView, params);
    }

    private void setupMediaProjection(int resultCode, Intent resultData) {
        cleanupProjection();

        mediaProjection = projectionManager.getMediaProjection(resultCode, resultData);
        if (mediaProjection == null) {
            stopSelf();
            return;
        }

        mediaProjection.registerCallback(projectionCallback, mainHandler);

        // ImageReader holds frames written by the VirtualDisplay
        imageReader = ImageReader.newInstance(
                screenWidth,
                screenHeight,
                PixelFormat.RGBA_8888,
                2
        );

        virtualDisplay = mediaProjection.createVirtualDisplay(
                "ScreenOverlayVirtualDisplay",
                screenWidth,
                screenHeight,
                screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(),
                null,
                null
        );
    }

    private void startCaptureLoop() {
        captureThread = new HandlerThread("ScreenCaptureWorker");
        captureThread.start();
        captureHandler = new Handler(captureThread.getLooper());

        captureHandler.post(captureRunnable);
    }

    private final Runnable captureRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isRunning) return;

            try {
                captureAndSendFrame();
            } catch (Exception e) {
                Log.e(TAG, "Error during capture cycle", e);
            }

            if (isRunning && captureHandler != null) {
                captureHandler.postDelayed(this, CAPTURE_INTERVAL_MS);
            }
        }
    };

    /**
     * IMAGE CONVERSION PIPELINE:
     * 1. Acquires the newest Image from ImageReader (RGBA_8888).
     * 2. Accounts for rowStride vs pixelStride padding so rows don't shear/skew.
     * 3. Copies into a Bitmap, compresses to JPEG at 30% quality for low latency,
     *    and encodes as a non-wrapped Base64 string.
     */
    private void captureAndSendFrame() {
        if (imageReader == null) return;
        if (isRequestInFlight.get()) {
            // Skip frame if previous network request is still in flight
            return;
        }

        Image image = null;
        Bitmap rawBitmap = null;
        Bitmap trimmedBitmap = null;

        try {
            image = imageReader.acquireLatestImage();
            if (image == null) return;

            Image.Plane[] planes = image.getPlanes();
            if (planes.length == 0) return;

            ByteBuffer buffer = planes[0].getBuffer();
            int pixelStride = planes[0].getPixelStride();
            int rowStride = planes[0].getRowStride();
            int rowPadding = rowStride - pixelStride * screenWidth;

            // Allocate width including row padding, then crop to exact screenWidth
            int paddedWidth = screenWidth + rowPadding / pixelStride;
            rawBitmap = Bitmap.createBitmap(paddedWidth, screenHeight, Bitmap.Config.ARGB_8888);
            buffer.rewind();
            rawBitmap.copyPixelsFromBuffer(buffer);

            trimmedBitmap = Bitmap.createBitmap(rawBitmap, 0, 0, screenWidth, screenHeight);

            ByteArrayOutputStream jpegStream = new ByteArrayOutputStream();
            trimmedBitmap.compress(Bitmap.CompressFormat.JPEG, 30, jpegStream);
            byte[] jpegBytes = jpegStream.toByteArray();

            String base64Jpeg = Base64.encodeToString(jpegBytes, Base64.NO_WRAP);
            String dataUri = "data:image/jpeg;base64," + base64Jpeg;

            sendFrameToApi(dataUri);
        } finally {
            if (image != null) {
                image.close();
            }
            if (trimmedBitmap != null && !trimmedBitmap.isRecycled()) {
                trimmedBitmap.recycle();
            }
            if (rawBitmap != null && !rawBitmap.isRecycled()) {
                rawBitmap.recycle();
            }
        }
    }

    /**
     * NETWORKING & JSON PARSING:
     * Sends a JSON POST payload {"image": "data:image/jpeg;base64,<...>"} off the main thread,
     * parses {"startX": ..., "startY": ..., "endX": ..., "endY": ...}, and updates OverlayView
     * on the UI thread.
     */
    private void sendFrameToApi(String base64DataUri) {
        isRequestInFlight.set(true);

        networkExecutor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(API_URL);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(8000);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                connection.setRequestProperty("Accept", "application/json");

                JSONObject payload = new JSONObject();
                payload.put("image", base64DataUri);

                byte[] postBytes = payload.toString().getBytes(StandardCharsets.UTF_8);
                connection.setFixedLengthStreamingMode(postBytes.length);

                try (OutputStream os = connection.getOutputStream()) {
                    os.write(postBytes);
                    os.flush();
                }

                int responseCode = connection.getResponseCode();
                InputStream responseStream = (responseCode >= 200 && responseCode < 300)
                        ? connection.getInputStream()
                        : connection.getErrorStream();

                String responseBody = readStream(responseStream);
                if (responseCode >= 200 && responseCode < 300 && responseBody != null) {
                    parseAndApplyCoordinates(responseBody);
                } else {
                    Log.w(TAG, "API returned HTTP " + responseCode + ": " + responseBody);
                }
            } catch (IOException | JSONException e) {
                Log.e(TAG, "Failed to send frame or parse API response", e);
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
                isRequestInFlight.set(false);
            }
        });
    }

    private void parseAndApplyCoordinates(String jsonString) throws JSONException {
        JSONObject json = new JSONObject(jsonString);

        // Extract line endpoints returned by the API
        final float startX = (float) json.getDouble("startX");
        final float startY = (float) json.getDouble("startY");
        final float endX = (float) json.getDouble("endX");
        final float endY = (float) json.getDouble("endY");

        // View updates must happen on the main/UI thread
        mainHandler.post(() -> {
            if (overlayView != null) {
                overlayView.updateCoordinates(startX, startY, endX, endY);
            }
        });
    }

    private String readStream(@Nullable InputStream inputStream) throws IOException {
        if (inputStream == null) return null;
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }

    private void stopCaptureLoop() {
        isRunning = false;
        if (captureHandler != null) {
            captureHandler.removeCallbacksAndMessages(null);
        }
        if (captureThread != null) {
            captureThread.quitSafely();
            captureThread = null;
            captureHandler = null;
        }
    }

    private void cleanupProjection() {
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
        if (mediaProjection != null) {
            mediaProjection.unregisterCallback(projectionCallback);
            mediaProjection.stop();
            mediaProjection = null;
        }
    }

    private void removeOverlayView() {
        if (overlayView != null && windowManager != null) {
            try {
                windowManager.removeView(overlayView);
            } catch (IllegalArgumentException ignored) {
                // View was already detached
            }
            overlayView = null;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopCaptureLoop();
        cleanupProjection();
        removeOverlayView();
        networkExecutor.shutdownNow();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
