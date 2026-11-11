package com.example.poollineoverlay;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;

public class LineOverlayView extends View {
    private Paint linePaint;
    private Paint pointPaint;
    // ធ្វើឱ្យខ្សែបន្ទាត់វែងជាងមុន (startX, startY ដល់ endX, endY)
    private float startX = 200f, startY = 200f;
    private float endX = 800f, endY = 1600f;
    private float radius = 60f;
    private boolean isDragging = false;
    private OverlayService service;

    public LineOverlayView(Context context) {
        super(context);
        init();
    }

    public void setService(OverlayService service) {
        this.service = service;
    }

    private void init() {
        linePaint = new Paint();
        linePaint.setColor(Color.CYAN);
        linePaint.setStrokeWidth(10f); // បន្ថែមទំហំខ្សែឱ្យធំនិងងាយមើលច្បាស់
        linePaint.setAntiAlias(true);
        linePaint.setStyle(Paint.Style.STROKE);

        pointPaint = new Paint();
        pointPaint.setColor(Color.YELLOW);
        pointPaint.setAntiAlias(true);
        pointPaint.setStyle(Paint.Style.FILL);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        // គូសខ្សែបន្ទាត់វែងពីចំណុចចាប់ផ្តើមទៅចុងបញ្ចប់
        canvas.drawLine(startX, startY, endX, endY, linePaint);
        // គូសគ្រាប់មូលពណ៌លឿងនៅចុងបន្ទាត់សម្រាប់ងាយស្រួលអូស
        canvas.drawCircle(endX, endY, radius, pointPaint);
        canvas.drawCircle(startX, startY, 30f, pointPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                // ពិនិត្យមើលថាតើប៉ះចំគ្រាប់មូលពណ៌លឿងនៅចុងបន្ទាត់ដែរឬទេ
                double distance = Math.sqrt(Math.pow(x - endX, 2) + Math.pow(y - endY, 2));
                if (distance <= radius + 60) {
                    isDragging = true;
                    if (service != null) {
                        service.updateTouchMode(true);
                    }
                    return true;
                }
                break;

            case MotionEvent.ACTION_MOVE:
                if (isDragging) {
                    endX = x;
                    endY = y;
                    invalidate();
                    return true;
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (isDragging) {
                    isDragging = false;
                    if (service != null) {
                        service.updateTouchMode(false);
                    }
                    return true;
                }
                break;
        }
        return super.onTouchEvent(event);
    }
}
