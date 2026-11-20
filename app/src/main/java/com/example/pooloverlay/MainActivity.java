package com.example.pooloverlay;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final String API_URL = "https://pool-027f.onrender.com/api/calculate";
    private static final int REQUEST_OVERLAY_PERMISSION = 1001;
    private static final int REQUEST_MEDIA_PROJECTION = 1002;

    private MediaProjectionManager projectionManager;
    private TextView tvApiStatus;
    private Button btnTestApi;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        projectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        tvApiStatus = findViewById(R.id.tvApiStatus);
        btnTestApi = findViewById(R.id.btnTestApi);
        Button btnStart = findViewById(R.id.btnStart);
        Button btnStop = findViewById(R.id.btnStop);

        btnTestApi.setOnClickListener(v -> testApiConnection());
        btnStart.setOnClickListener(v -> checkPermissionsAndStart());
        btnStop.setOnClickListener(v -> {
            stopService(new Intent(this, OverlayService.class));
            Toast.makeText(this, "Overlay Stopped", Toast.LENGTH_SHORT).show();
        });
    }

    private void testApiConnection() {
        btnTestApi.setEnabled(false);
        tvApiStatus.setText("កំពុងតេស្តភ្ជាប់ទៅ API...");

        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                Bitmap dummy = Bitmap.createBitmap(128, 72, Bitmap.Config.ARGB_8888);
                dummy.eraseColor(Color.parseColor("#0B5D2E"));
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                dummy.compress(Bitmap.CompressFormat.JPEG, 70, baos);
                byte[] imageBytes = baos.toByteArray();
                dummy.recycle();

                String boundary = "----PoolTestBoundary" + System.currentTimeMillis();
                URL url = new URL(API_URL);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setConnectTimeout(20000);
                conn.setReadTimeout(20000);
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

                DataOutputStream dos = new DataOutputStream(conn.getOutputStream());
                dos.writeBytes("--" + boundary + "\r\n");
                dos.writeBytes("Content-Disposition: form-data; name=\"image\"; filename=\"test.jpg\"\r\n");
                dos.writeBytes("Content-Type: image/jpeg\r\n\r\n");
                dos.write(imageBytes);
                dos.writeBytes("\r\n--" + boundary + "--\r\n");
                dos.flush();
                dos.close();

                int code = conn.getResponseCode();
                InputStream stream = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
                String body = "";
                if (stream != null) {
                    Scanner s = new Scanner(stream).useDelimiter("\\A");
                    body = s.hasNext() ? s.next() : "";
                }

                final String preview = body.length() > 140 ? body.substring(0, 140) + "..." : body;
                runOnUiThread(() -> {
                    btnTestApi.setEnabled(true);
                    if (code == HttpURLConnection.HTTP_OK) {
                        tvApiStatus.setText("✅ ភ្ជាប់បានជោគជ័យ (HTTP 200): " + preview);
                        Toast.makeText(this, "API Connected (HTTP 200)", Toast.LENGTH_SHORT).show();
                    } else {
                        tvApiStatus.setText("⚠️ ឆ្លើយតប HTTP " + code + ": " + preview);
                        Toast.makeText(this, "HTTP " + code, Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    btnTestApi.setEnabled(true);
                    tvApiStatus.setText("❌ ភ្ជាប់មិនបាន: " + e.getMessage());
                    Toast.makeText(this, "Connection Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    private void checkPermissionsAndStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION);
        } else if (projectionManager != null) {
            startActivityForResult(projectionManager.createScreenCaptureIntent(), REQUEST_MEDIA_PROJECTION);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_OVERLAY_PERMISSION) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this) && projectionManager != null) {
                startActivityForResult(projectionManager.createScreenCaptureIntent(), REQUEST_MEDIA_PROJECTION);
            }
        } else if (requestCode == REQUEST_MEDIA_PROJECTION) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                Intent serviceIntent = new Intent(this, OverlayService.class);
                serviceIntent.putExtra("RESULT_CODE", resultCode);
                serviceIntent.putExtra("DATA", data);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent);
                } else {
                    startService(serviceIntent);
                }
                Toast.makeText(this, "Pool Robot Started! Open 8 Ball Pool", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
