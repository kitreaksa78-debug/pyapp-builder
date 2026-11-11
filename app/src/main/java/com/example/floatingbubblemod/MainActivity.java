package com.example.floatingbubblemod;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        Button btn = new Button(this);
        btn.setText("Start Floating Bubble Service");
        setContentView(btn);

        btn.setOnClickListener(v -> {
            if (checkOverlayPermission()) {
                startFloatingService();
            }
        });
    }

    private boolean checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Intent intent = new Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())
                );
                startActivityForResult(intent, 1234);
                Toast.makeText(this, "សូមផ្តល់សិទ្ធិ Display over other apps", Toast.LENGTH_SHORT).show();
                return false;
            }
        }
        return true;
    }

    private void startFloatingService() {
        Intent intent = new Intent(this, FloatingService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
        Toast.makeText(this, "Floating Bubble បានបើកដំណើរការ!", Toast.LENGTH_SHORT).show();
        finish();
    }
}
