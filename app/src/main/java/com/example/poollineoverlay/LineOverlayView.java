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
    private float startX = 300f, startY = 300f;
    private float endX = 500f, endY = 900f;
    private float radius = 50f;
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
        linePaint.setStrokeWidth(6f);
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
        canvas.drawLine(startX, startY, endX, endY, linePaint);
        canvas.drawCircle(endX, endY, radius, pointPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                double distance = Math.sqrt(Math.pow(x - endX, 2) + Math.pow(y - endY, 2));
                if (distance <= radius + 40) {
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
