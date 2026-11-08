package com.pool.trajectory;

import android.app.Activity;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    private static final int REQUEST_OVERLAY = 10;
    private static final int REQUEST_CAPTURE = 11;
    private MediaProjectionManager projectionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        projectionManager = (MediaProjectionManager)
                getSystemService(MEDIA_PROJECTION_SERVICE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(28, 36, 28, 28);
        root.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView title = new TextView(this);
        title.setText("Pool Trajectory");
        title.setTextSize(25);
        title.setPadding(0, 0, 0, 12);

        TextView info = new TextView(this);
        info.setText("Training overlay: capture a screen with your permission, then use the floating panel. This starter project does not modify or control the game.");
        info.setTextSize(16);
        info.setPadding(0, 0, 0, 24);

        Button start = new Button(this);
        start.setText("Enable overlay and screen capture");
        start.setOnClickListener(v -> beginSetup());

        Button stop = new Button(this);
        stop.setText("Stop overlay");
        stop.setOnClickListener(v -> stopService(new Intent(this, OverlayService.class)));

        root.addView(title);
        root.addView(info);
        root.addView(start);
        root.addView(stop);
        setContentView(root);
    }

    private void beginSetup() {
        if (!Settings.canDrawOverlays(this)) {
            Intent overlayPermission = new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(overlayPermission, REQUEST_OVERLAY);
            return;
        }
        requestCapturePermission();
    }

    private void requestCapturePermission() {
        startActivityForResult(
                projectionManager.createScreenCaptureIntent(),
                REQUEST_CAPTURE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_OVERLAY) {
            if (Settings.canDrawOverlays(this)) requestCapturePermission();
            return;
        }

        if (requestCode == REQUEST_CAPTURE && resultCode == RESULT_OK && data != null) {
            Intent capture = new Intent(this, CaptureService.class);
            capture.putExtra(CaptureService.EXTRA_RESULT_CODE, resultCode);
            capture.putExtra(CaptureService.EXTRA_RESULT_DATA, data);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(capture);
            } else {
                startService(capture);
            }

            startService(new Intent(this, OverlayService.class));
        }
    }
}
