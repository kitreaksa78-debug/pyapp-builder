package com.example.mod8pallpool;

import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private MediaProjectionManager projectionManager;

    // Launcher for requesting SYSTEM_ALERT_WINDOW via system settings
    private final ActivityResultLauncher<Intent> overlayPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (Settings.canDrawOverlays(this)) {
                    requestScreenCapturePermission();
                } else {
                    Toast.makeText(this, "Overlay permission is required to draw lines.", Toast.LENGTH_SHORT).show();
                }
            });

    // Launcher for MediaProjection screen capture consent dialog
    private final ActivityResultLauncher<Intent> screenCaptureLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    startScreenCaptureService(result.getResultCode(), result.getData());
                } else {
                    Toast.makeText(this, "Screen capture permission denied.", Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        projectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);

        // Build simple UI programmatically (no extra layout XML required)
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setGravity(Gravity.CENTER);

        Button startOverlayButton = new Button(this);
        startOverlayButton.setText("Start Overlay");

        Button stopOverlayButton = new Button(this);
        stopOverlayButton.setText("Stop Overlay");

        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        btnParams.setMargins(0, 16, 0, 16);

        container.addView(startOverlayButton, btnParams);
        container.addView(stopOverlayButton, btnParams);

        FrameLayout rootLayout = new FrameLayout(this);
        rootLayout.addView(container, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
        setContentView(rootLayout);

        startOverlayButton.setOnClickListener(v -> checkOverlayAndStartCapture());
        stopOverlayButton.setOnClickListener(v -> {
            stopService(new Intent(this, ScreenCaptureService.class));
            Toast.makeText(this, "Overlay capture stopped.", Toast.LENGTH_SHORT).show();
        });
    }

    private void checkOverlayAndStartCapture() {
        // Step 1: Verify overlay permission before requesting screen capture
        if (!Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())
            );
            overlayPermissionLauncher.launch(intent);
            return;
        }

        // Step 2: Request MediaProjection permission
        requestScreenCapturePermission();
    }

    private void requestScreenCapturePermission() {
        if (projectionManager == null) {
            projectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        }
        Intent captureIntent = projectionManager.createScreenCaptureIntent();
        screenCaptureLauncher.launch(captureIntent);
    }

    private void startScreenCaptureService(int resultCode, Intent data) {
        Intent serviceIntent = new Intent(this, ScreenCaptureService.class);
        serviceIntent.putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode);
        serviceIntent.putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, data);

        ContextCompat.startForegroundService(this, serviceIntent);
        Toast.makeText(this, "Overlay capture started.", Toast.LENGTH_SHORT).show();
    }
}
