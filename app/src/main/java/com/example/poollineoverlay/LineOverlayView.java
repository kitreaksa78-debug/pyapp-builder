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
    private Paint bouncePaint;

    // ទីតាំងចំណុចចាប់ផ្តើម និងចំណុចអូស
    private float startX = 300f, startY = 800f;
    private float endX = 600f, endY = 500f;
    private float radius = 60f;
    
    private boolean isDraggingStart = false;
    private boolean isDraggingEnd = false;
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
        linePaint.setStrokeWidth(8f);
        linePaint.setAntiAlias(true);
        linePaint.setStyle(Paint.Style.STROKE);

        bouncePaint = new Paint();
        bouncePaint.setColor(Color.MAGENTA);
        bouncePaint.setStrokeWidth(6f);
        bouncePaint.setAntiAlias(true);
        bouncePaint.setStyle(Paint.Style.STROKE);

        pointPaint = new Paint();
        pointPaint.setColor(Color.YELLOW);
        pointPaint.setAntiAlias(true);
        pointPaint.setStyle(Paint.Style.FILL);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // ១. គណនាទិសដៅ និងពង្រីកប្រវែងខ្សែបន្ទាត់ឱ្យវែងឆ្ងាយ (Extended Vector Calculation)
        float dx = endX - startX;
        float dy = endY - startY;
        float length = (float) Math.sqrt(dx * dx + dy * dy);

        if (length > 0) {
            // កំណត់ប្រវែងពង្រីក (ឧទាហរណ៍៖ ២០០០ pixel ឱ្យវែងឆ្លងអេក្រង់)
            float rayLength = 2000f;
            float extendedX = startX + (dx / length) * rayLength;
            float extendedY = startY + (dy / length) * rayLength;

            // គូសខ្សែបន្ទាត់ចម្បង
            canvas.drawLine(startX, startY, extendedX, extendedY, linePaint);

            // ២. គណនាគន្លងផ្លាតធរណីមាត្រគំរូ (Geometric Reflection Physics Simulation)
            float bounceX = extendedX + (dx / length) * 500f;
            float bounceY = extendedY - (dy / length) * 500f;
            canvas.drawLine(extendedX, extendedY, bounceX, bounceY, bouncePaint);
        }

        // គូសចំណុចបញ្ជា (Control Points)
        canvas.drawCircle(startX, startY, radius, pointPaint);
        canvas.drawCircle(endX, endY, radius, pointPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                // ពិនិត្យមើលថាតើប៉ះចំចំណុចចាប់ផ្តើម ឬចំណុចចុងបញ្ចប់
                double distStart = Math.sqrt(Math.pow(x - startX, 2) + Math.pow(y - startY, 2));
                double distEnd = Math.sqrt(Math.pow(x - endX, 2) + Math.pow(y - endY, 2));

                if (distStart <= radius + 40) {
                    isDraggingStart = true;
                    if (service != null) service.updateTouchMode(true);
                    return true;
                } else if (distEnd <= radius + 40) {
                    isDraggingEnd = true;
                    if (service != null) service.updateTouchMode(true);
                    return true;
                }
                break;

            case MotionEvent.ACTION_MOVE:
                if (isDraggingStart) {
                    startX = x;
                    startY = y;
                    invalidate();
                    return true;
                } else if (isDraggingEnd) {
                    endX = x;
                    endY = y;
                    invalidate();
                    return true;
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (isDraggingStart || isDraggingEnd) {
                    isDraggingStart = false;
                    isDraggingEnd = false;
                    if (service != null) service.updateTouchMode(false);
                    return true;
                }
                break;
        }
        return super.onTouchEvent(event);
    }
}
