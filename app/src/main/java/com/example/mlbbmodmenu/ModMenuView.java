package com.example.mlbbmodmenu;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

public class ModMenuView extends View {
    private Paint bgPaint;
    private Paint textPaint;
    private float initialX, initialY;
    private float touchX, touchY;
    private ModService service;

    public ModMenuView(Context context) {
        super(context);
        init();
    }

    public void setService(ModService service) {
        this.service = service;
    }

    private void init() {
        bgPaint = new Paint();
        bgPaint.setColor(Color.parseColor("#CC000000")); // ពណ៌ខ្មៅស្រអាប់ (Dark Semi-transparent)
        bgPaint.setStyle(Paint.Style.FILL);
        bgPaint.setAntiAlias(true);

        textPaint = new Paint();
        textPaint.setColor(Color.GREEN);
        textPaint.setTextSize(36f);
        textPaint.setAntiAlias(true);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        // គូសផ្ទាំង Mod Menu Background រាងចតុកោណកែង
        canvas.drawRoundRect(0, 0, 500f, 600f, 25f, 25f, bgPaint);

        // បង្ហាញអត្ថបទ Menu Options ផ្សេងៗ
        canvas.drawText("=== MLBB MOD MENU ===", 30f, 60f, textPaint);
        
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(30f);
        canvas.drawText("[✓] Drone View (Cam Hack)", 30f, 140f, textPaint);
        canvas.drawText("[✓] Map Hack Radar", 30f, 220f, textPaint);
        canvas.drawText("[✓] ESP Line & Box", 30f, 300f, textPaint);
        canvas.drawText("[✓] Auto Aim Target", 30f, 380f, textPaint);

        textPaint.setColor(Color.YELLOW);
        canvas.drawText("👉 អូសទីកន្លែងនេះដើម្បីរំកិល Menu", 30f, 500f, textPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                touchX = event.getRawX();
                touchY = event.getRawY();
                return true;

            case MotionEvent.ACTION_MOVE:
                // គណនាការអូសរំកិលផ្ទាំង Menu ទៅកាន់ទីតាំងថ្មី
                float dx = event.getRawX() - touchX;
                float dy = event.getRawY() - touchY;
                // (ក្នុងករណីអនុវត្តជាក់ស្តែង អ្នកអាច updateWindowManager layout params នៅទីនេះបាន)
                return true;
        }
        return super.onTouchEvent(event);
    }
}
