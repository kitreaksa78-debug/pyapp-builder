package com.example.poollineoverlay;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.view.Gravity;

public class MainActivity extends Activity {
    private static final int REQUEST_OVERLAY = 1001;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 48, 32, 32);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView title = new TextView(this);
        title.setText("Pool Line Overlay");
        title.setTextSize(24);
        layout.addView(title);

        TextView info = new TextView(this);
        info.setText("Manual screen annotation tool. Draw and move a guide line yourself. It does not read game data or calculate/auto-aim.");
        info.setTextSize(16);
        info.setPadding(0, 24, 0, 24);
        layout.addView(info);

        Button permission = new Button(this);
        permission.setText("Grant Display Over Other Apps");
        permission.setOnClickListener(v -> {
            if (!Settings.canDrawOverlays(this)) {
                Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
                startActivityForResult(i, REQUEST_OVERLAY);
            } else startOverlay();
        });
        layout.addView(permission);

        Button start = new Button(this);
        start.setText("Show Drawing Overlay");
        start.setOnClickListener(v -> {
            if (Settings.canDrawOverlays(this)) startOverlay();
            else {
                Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
                startActivity(i);
            }
        });
        layout.addView(start);

        Button stop = new Button(this);
        stop.setText("Hide Overlay");
        stop.setOnClickListener(v -> stopService(new Intent(this, OverlayService.class)));
        layout.addView(stop);

        setContentView(layout);
    }

    private void startOverlay() {
        startService(new Intent(this, OverlayService.class));
    }
}
