package org.pyapp.demo;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
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

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 50, 50, 50);
        layout.setGravity(Gravity.CENTER);

        Button btnStart = new Button(this);
        btnStart.setText("បើក Mod Menu");
        btnStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (!Settings.canDrawOverlays(MainActivity.this)) {
                        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
                        startActivity(intent);
                        Toast.makeText(MainActivity.this, "សូមអនុញ្ញាតិ Draw over other apps", Toast.LENGTH_LONG).show();
                    } else {
                        startOverlay();
                    }
                } else {
                    startOverlay();
                }
            }
        });

        Button btnStop = new Button(this);
        btnStop.setText("បិទ Mod Menu");
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

    private void startOverlay() {
        if (isShowing) return;
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        overlayView = new ModMenuView(this);

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
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);

        params.gravity = Gravity.TOP | Gravity.START;
        windowManager.addView(overlayView, params);
        isShowing = true;
    }

    private void stopOverlay() {
        if (overlayView != null && isShowing) {
            windowManager.removeView(overlayView);
            overlayView = null;
            isShowing = false;
        }
    }

    // Class សម្រាប់គូរ UI និងខ្សែ
    private class ModMenuView extends View {
        private Paint linePaint;
        private Paint menuPaint;
        private Paint textPaint;
        private boolean guidelineEnabled = false;
        private RectF menuButtonRect;

        public ModMenuView(Context context) {
            super(context);
            
            // កំណត់ពណ៌ខ្សែរត់
            linePaint = new Paint();
            linePaint.setColor(Color.RED);
            linePaint.setStrokeWidth(8);
            linePaint.setStyle(Paint.Style.STROKE);
            linePaint.setAntiAlias(true);

            // កំណត់ពណ៌ផ្ទាំង Menu
            menuPaint = new Paint();
            menuPaint.setColor(Color.parseColor("#CC000000")); // ខ្មៅថ្លា
            menuPaint.setStyle(Paint.Style.FILL);

            // កំណត់អក្សរ
            textPaint = new Paint();
            textPaint.setColor(Color.WHITE);
            textPaint.setTextSize(50);
            textPaint.setAntiAlias(true);
            
            // ទីតាំងប៊ូតុង Menu (ជ្រុងខាងឆ្វេងខាងលើ)
            menuButtonRect = new RectF(50, 100, 450, 250);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            // ១. គូរខ្សែរត់ (បើបើក)
            if (guidelineEnabled) {
                float startX = getWidth() / 2f;
                float startY = getHeight() * 0.8f;
                float endX = getWidth() / 2f + 600; // គូរទ្រេតទៅស្តាំ
                float endY = getHeight() * 0.2f;
                canvas.drawLine(startX, startY, endX, endY, linePaint);
            }

            // ២. គូរផ្ទាំង Menu
            canvas.drawRoundRect(menuButtonRect, 30, 30, menuPaint);
            
            // ៣. សរសេរអក្សរលើ Menu
            String status = guidelineEnabled ? "Guideline: ON" : "Guideline: OFF";
            canvas.drawText(status, 100, 200, textPaint);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                float x = event.getX();
                float y = event.getY();

                // ពិនិត្យថាតើអ្នកប្រើចុចលើប៊ូតុង Menu ដែរឬទេ
                if (x >= menuButtonRect.left && x <= menuButtonRect.right &&
                    y >= menuButtonRect.top && y <= menuButtonRect.bottom) {
                    
                    // បិទ/បើក ខ្សែរត់
                    guidelineEnabled = !guidelineEnabled;
                    invalidate(); // គូរឡើងវិញ
                    return true;
                }
            }
            return false;
        }
    }
}
