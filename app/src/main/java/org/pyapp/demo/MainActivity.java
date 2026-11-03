package org.pyapp.demo;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

public class MainActivity extends Activity {
    private WindowManager windowManager;
    private View overlayView;
    private boolean isShowing = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // ១. បង្កើតផ្ទាំង UI សាមញ្ញ
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(60, 60, 60, 60);
        layout.setGravity(Gravity.CENTER);

        Button btnStart = new Button(this);
        btnStart.setText("បើកខ្សែរត់");
        btnStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkPermissionAndStart();
            }
        });

        Button btnStop = new Button(this);
        btnStop.setText("បិទខ្សែរត់");
        btnStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopOverlay();
            }
        });

        layout.addView(btnStart);
        layout.addView(btnStop);
        setContentView(layout);
    }

    // ២. ពិនិត្យសិទ្ធិ និងបើក Overlay
    private void checkPermissionAndStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                // បើគ្មានសិទ្ធិ បើកទៅទំព័រកំណត់របស់ទូរស័ព្ទ
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivity(intent);
                Toast.makeText(this, "សូមអនុញ្ញាតឱ្យបង្ហាញលើ App ផ្សេង", Toast.LENGTH_LONG).show();
            } else {
                startOverlay();
            }
        } else {
            startOverlay();
        }
    }

    // ៣. បង្កើតខ្សែ (Overlay)
    private void startOverlay() {
        if (isShowing) return;
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        overlayView = new LineView(this);

        int layoutFlag;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            layoutFlag = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            layoutFlag = WindowManager.LayoutParams.TYPE_PHONE;
        }

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);

        params.gravity = Gravity.TOP | Gravity.START;
        windowManager.addView(overlayView, params);
        isShowing = true;
        Toast.makeText(this, "បានបើក Overlay! សូមបើកហ្គេម", Toast.LENGTH_SHORT).show();
    }

    // ៤. បិទខ្សែ
    private void stopOverlay() {
        if (overlayView != null && isShowing) {
            windowManager.removeView(overlayView);
            overlayView = null;
            isShowing = false;
            Toast.makeText(this, "បានបិទ Overlay", Toast.LENGTH_SHORT).show();
        }
    }

    // ៥. Class សម្រាប់គូរខ្សែបន្ទាត់
    private class LineView extends View {
        private Paint paint;
        public LineView(android.content.Context context) {
            super(context);
            paint = new Paint();
            paint.setColor(Color.RED); // ពណ៌ក្រហម
            paint.setStrokeWidth(10);  // កម្រាស់ខ្សែ
            paint.setStyle(Paint.Style.STROKE);
            paint.setAntiAlias(true);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            // គូរខ្សែបន្ទាត់ពីកណ្តាលអេក្រង់ទៅខាងស្តាំ
            float startX = getWidth() / 2f;
            float startY = getHeight() / 2f;
            float endX = getWidth() / 2f + 600;
            float endY = getHeight() / 2f;
            canvas.drawLine(startX, startY, endX, endY, paint);

            // គូររង្វង់ពណ៌បៃតងនៅចំនុចចាប់ផ្តើម
            Paint circlePaint = new Paint();
            circlePaint.setColor(Color.GREEN);
            circlePaint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(startX, startY, 25, circlePaint);
        }
    }
}
