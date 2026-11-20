package com.example.pooloverlay;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;
import android.view.Gravity;
import android.view.WindowManager;
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
import java.nio.charset.StandardCharsets;

public class ScreenCaptureService extends Service {

    private static final String TAG = "PoolOverlay";
    private static final String CHANNEL_ID = "pool_overlay_channel";
    private static final String API_URL = "https://pool-overlay.onrender.com/api/calculate";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private WindowManager windowManager;
    private OverlayView overlayView;

    @Override
    public void onCreate() {
        super.onCreate();
        startForegroundNotification();
        setupOverlayView();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
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
                .setContentText("Overlay service is running")
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .build();

        startForeground(1001, notification);
    }

    private void setupOverlayView() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        overlayView = new OverlayView(this);

        int layoutFlag = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;

        if (windowManager != null) {
            windowManager.addView(overlayView, params);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (windowManager != null && overlayView != null) {
            windowManager.removeView(overlayView);
            overlayView = null;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public void sendToAPI(final Bitmap bitmap) {
        if (bitmap == null) {
            Log.w(TAG, "Bitmap is null, skip sending to API.");
            return;
        }

        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 75, baos);
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
                            final int startX = jsonResponse.getInt("startX");
                            final int startY = jsonResponse.getInt("startY");
                            final int endX = jsonResponse.getInt("endX");
                            final int endY = jsonResponse.getInt("endY");

                            mainHandler.post(() -> drawLineOnOverlay(startX, startY, endX, endY));
                        } else {
                            final String errorMsg = jsonResponse.optString("message", "Unknown error");
                            mainHandler.post(() -> {
                                Toast.makeText(getApplicationContext(),
                                        "API: " + errorMsg, Toast.LENGTH_SHORT).show();
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
}
