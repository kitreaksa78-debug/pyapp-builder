package com.example.poollineoverlay;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Color;
import android.view.MotionEvent;
import android.view.View;

public class LineOverlayView extends View {
    private final Paint line = new Paint(3);
    private final Paint handle = new Paint(3);
    private float x1 = 180, y1 = 650, x2 = 600, y2 = 350;
    private boolean moveStart = false, moveEnd = false;

    public LineOverlayView(Context context) {
        super(context);
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
        line.setColor(Color.CYAN);
        line.setStrokeWidth(5f);
        line.setStyle(Paint.Style.STROKE);
        handle.setColor(Color.YELLOW);
        handle.setStyle(Paint.Style.FILL);
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        c.drawLine(x1, y1, x2, y2, line);
        c.drawCircle(x1, y1, 18, handle);
        c.drawCircle(x2, y2, 18, handle);
        Paint label = new Paint(3);
        label.setColor(Color.WHITE);
        label.setTextSize(30);
        label.setShadowLayer(4, 1, 1, Color.BLACK);
        c.drawText("Drag yellow endpoints • Tap corner X not available; hide from app", 24, 60, label);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        float x = e.getX(), y = e.getY();
        switch (e.getAction()) {
            case MotionEvent.ACTION_DOWN:
                float d1 = dist(x, y, x1, y1), d2 = dist(x, y, x2, y2);
                moveStart = d1 < 70 && d1 <= d2;
                moveEnd = d2 < 70 && d2 < d1;
                return true;
            case MotionEvent.ACTION_MOVE:
                if (moveStart) { x1 = x; y1 = y; invalidate(); }
                else if (moveEnd) { x2 = x; y2 = y; invalidate(); }
                return true;
            case MotionEvent.ACTION_UP:
                moveStart = moveEnd = false;
                return true;
        }
        return true;
    }

    private float dist(float ax, float ay, float bx, float by) {
        float dx = ax - bx, dy = ay - by;
        return (float)Math.sqrt(dx*dx + dy*dy);
    }
}
