package com.example.pooloverlay;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Base64;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class MainActivity extends AppCompatActivity {

    private static final String SERVER_BASE_URL = "https://pool-027f.onrender.com";
    private static final String CALCULATE_URL = SERVER_BASE_URL + "/api/calculate";

    private TextView tvStatus;
    private Button btnScanApi, btnTestConnection, btnStartOverlay, btnStopOverlay;

    private MediaProjectionManager projectionManager;
    private boolean pendingStartAfterPermission = false;

    private final ActivityResultLauncher<Intent> screenCaptureLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    startOverlayAndCaptureService(result.getResultCode(), result.getData());
                } else {
                    Toast.makeText(this, "ត្រូវការអនុញ្ញាតថតអេក្រង់ដើម្បីដំណើរការ Overlay", Toast.LENGTH_SHORT).show();
                }
            });

    private final ActivityResultLauncher<Intent> overlayPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (canDrawOverlays()) {
                    if (pendingStartAfterPermission) {
                        pendingStartAfterPermission = false;
                        requestScreenCapturePermission();
                    }
                } else {
                    Toast.makeText(this, "សូមបើកសិទ្ធិ Draw over other apps", Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        projectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);

        tvStatus = findViewById(R.id.tvStatus);
        btnScanApi = findViewById(R.id.btnScanApi);
        btnTestConnection = findViewById(R.id.btnTestConnection);
        btnStartOverlay = findViewById(R.id.btnStartOverlay);
        btnStopOverlay = findViewById(R.id.btnStopOverlay);

        btnScanApi.setOnClickListener(v -> runScanApiTest());
        btnTestConnection.setOnClickListener(v -> testApiConnection());

        btnStartOverlay.setOnClickListener(v -> {
            if (!canDrawOverlays()) {
                pendingStartAfterPermission = true;
                requestOverlayPermission();
            } else {
                requestScreenCapturePermission();
            }
        });

        btnStopOverlay.setOnClickListener(v -> {
            Intent stopIntent = new Intent(this, ScreenCaptureService.class);
            stopService(stopIntent);
            updateStatus("⏹ បានបញ្ឈប់ Overlay");
            Toast.makeText(this, "Overlay Stopped", Toast.LENGTH_SHORT).show();
        });
    }

    private boolean canDrawOverlays() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this);
    }

    private void requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent intent = new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())
            );
            overlayPermissionLauncher.launch(intent);
        }
    }

    private void requestScreenCapturePermission() {
        Intent captureIntent = projectionManager.createScreenCaptureIntent();
        screenCaptureLauncher.launch(captureIntent);
    }

    private void startOverlayAndCaptureService(int resultCode, Intent data) {
        Intent serviceIntent = new Intent(this, ScreenCaptureService.class);
        serviceIntent.putExtra("RESULT_CODE", resultCode);
        serviceIntent.putExtra("DATA", data);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }

        updateStatus("▶ Overlay កំពុងដំណើរការ");
        Toast.makeText(this, "Overlay Started", Toast.LENGTH_SHORT).show();
    }

    private void testApiConnection() {
        updateStatus("⏳ កំពុងតភ្ជាប់ទៅ Server...");
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(SERVER_BASE_URL + "/");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);

                int code = conn.getResponseCode();
                String msg = "API Connected (HTTP " + code + ")";
                runOnUiThread(() -> {
                    updateStatus("✅ ភ្ជាប់បានជោគជ័យ (HTTP " + code + ")");
                    Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    updateStatus("❌ ភ្ជាប់មិនបាន: " + e.getMessage());
                    Toast.makeText(MainActivity.this, "Connection Failed", Toast.LENGTH_SHORT).show();
                });
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    private void runScanApiTest() {
        updateStatus("⏳ កំពុងផ្ញើរូបភាពតេស្តទៅ /api/calculate...");
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                Bitmap testBitmap = Bitmap.createBitmap(1600, 720, Bitmap.Config.ARGB_8888);
                testBitmap.eraseColor(Color.DKGRAY);

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                testBitmap.compress(Bitmap.CompressFormat.JPEG, 70, baos);
                String base64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);

                JSONObject body = new JSONObject();
                body.put("image", "data:image/jpeg;base64," + base64);

                URL url = new URL(CALCULATE_URL);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(20000);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body.toString().getBytes(StandardCharsets.UTF_8));
                    os.flush();
                }

                int code = conn.getResponseCode();
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)
                );
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                reader.close();

                final String responseText = sb.toString();
                runOnUiThread(() -> updateStatus("✅ ភ្ជាប់បានជោគជ័យ (HTTP " + code + "): " + responseText));
            } catch (Exception e) {
                runOnUiThread(() -> updateStatus("❌ SCAN API បរាជ័យ: " + e.getMessage()));
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    private void updateStatus(String text) {
        if (tvStatus != null) {
            tvStatus.setText(text);
        }
    }
}
