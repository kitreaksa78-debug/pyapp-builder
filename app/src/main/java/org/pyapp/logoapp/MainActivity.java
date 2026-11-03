package org.pyapp.logoapp;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {

    private LinearLayout root;
    private TextView overlayView;
    private TextView state;
    private boolean showing = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(48, 96, 48, 48);
        root.setBackgroundColor(Color.WHITE);

        TextView title = new TextView(this);
        title.setText("Overlay Demo");
        title.setTextSize(22);
        root.addView(title);

        state = new TextView(this);
        state.setPadding(0, 24, 0, 24);
        root.addView(state);

        Button grant = new Button(this);
        grant.setText("សុំសិទ្ធិ (Draw over apps)");
        grant.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                OverlayHelper.request(MainActivity.this);
            }
        });
        root.addView(grant);

        Button toggle = new Button(this);
        toggle.setText("បង្ហាញ / លាក់ Overlay");
        toggle.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (!OverlayHelper.has(MainActivity.this)) {
                    OverlayHelper.request(MainActivity.this);
                    return;
                }
                toggleOverlay();
            }
        });
        root.addView(toggle);

        setContentView(root);
        refreshState();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshState();
    }

    private void refreshState() {
        state.setText("សិទ្ធិ overlay: " + (OverlayHelper.has(this) ? "មាន" : "មិនមាន"));
    }

    private void toggleOverlay() {
        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (showing) {
            if (overlayView != null) {
                try {
                    wm.removeView(overlayView);
                } catch (Exception ignored) {
                }
                overlayView = null;
            }
            showing = false;
            return;
        }
        if (overlayView == null) {
            overlayView = new TextView(this);
            overlayView.setText("Overlay កំពុងដំណើរការ");
            overlayView.setTextColor(Color.WHITE);
            overlayView.setTextSize(16);
            overlayView.setBackgroundColor(Color.parseColor("#CC1F6FEB"));
            overlayView.setPadding(32, 20, 32, 20);
        }
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                OverlayHelper.layoutType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = 40;
        lp.y = 120;
        try {
            wm.addView(overlayView, lp);
            showing = true;
        } catch (Exception e) {
            showing = false;
        }
    }
}
