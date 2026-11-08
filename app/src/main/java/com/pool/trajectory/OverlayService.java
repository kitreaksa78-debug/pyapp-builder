package com.pool.trajectory;

import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class OverlayService extends Service {
    private WindowManager windowManager;
    private LinearLayout panel;

    @Override
    public void onCreate() {
        super.onCreate();
        if (!Settings.canDrawOverlays(this)) {
            stopSelf();
            return;
        }

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(18, 14, 18, 14);
        panel.setBackgroundColor(0xEE20242B);

        TextView title = new TextView(this);
        title.setText("POOL TRAINING");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(14);
        panel.addView(title);

        TextView status = new TextView(this);
        status.setText("Screen capture active\nTrajectory analysis: next step");
        status.setTextColor(0xFFFFFFFF);
        status.setTextSize(12);
        panel.addView(status);

        Button hide = new Button(this);
        hide.setText("Close panel");
        hide.setOnClickListener(v -> stopSelf());
        panel.addView(hide);

        int overlayType = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                overlayType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.END;
        params.x = 20;
        params.y = 180;
        windowManager.addView(panel, params);
    }

    @Override
    public void onDestroy() {
        if (windowManager != null && panel != null) {
            try { windowManager.removeView(panel); } catch (Exception ignored) { }
        }
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
