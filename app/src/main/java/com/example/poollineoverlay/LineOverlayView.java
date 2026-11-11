package com.example.poollineoverlay;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;

public class LineOverlayView extends View {
    private Paint linePaint;
    private Paint circlePaint;
    private OverlayService service;

    private float startX = 300f;
    private float startY = 500f;
    private float yellowX = 700f;
    private float yellowY = 500f;
    private float radius = 40f; // ទំហំគ្រាប់ពងក្រូចពាក់កណ្តាល/🟡

    private boolean isDragging = false;

    public LineOverlayView(Context context, OverlayService service) {
        super(context);
        this.service = service;
        init();
    }

    private void init() {
        linePaint = new Paint();
        linePaint.setColor(Color.parseColor("#00E5FF")); // ពណ៌ Cyan/Blue Line ដូចក្នុងរូប
        linePaint.setStrokeWidth(6f);
        linePaint.setAntiAlias(true);
        linePaint.setStyle(Paint.Style.STROKE);

        circlePaint = new Paint();
        circlePaint.setColor(Color.YELLOW); // គ្រាប់ចុចអូសពណ៌លឿង
        circlePaint.setAntiAlias(true);
        circlePaint.setStyle(Paint.Style.FILL);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                // ពិនិត្យថាតើប៉ះចំគ្រាប់ពណ៌លឿង (Yellow Endpoint) ដែរឬទេ
                float dx = x - yellowX;
                float dy = y - yellowY;
                if (dx * dx + dy * dy <= (radius * 2.5f) * (radius * 2.5f)) {
                    isDragging = true;
                    if (service != null) {
                        service.setTouchEnabled(true); // បើកសិទ្ធិឱ្យចាប់ Touch ពេញទំហឹងពេលអូស
                    }
                    return true;
                }
                return false; // បើមិនបានប៉ះចំកន្លែងអូស ឱ្យ Touch ធ្លាក់ឆ្លងកាត់ទៅ App ក្រោម

            case MotionEvent.ACTION_MOVE:
                if (isDragging) {
                    yellowX = x;
                    yellowY = y;
                    invalidate();
                    return true;
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (isDragging) {
                    isDragging = false;
                    if (service != null) {
                        service.setTouchEnabled(false); // បិទវិញ ដើម្បីឱ្យចុចចូល App ផ្សេងបានធម្មតា
                    }
                    return true;
                }
                break;
        }
        return super.onTouchEvent(event);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // គូរបន្ទាត់ពី Start ទៅ Yellow Endpoint
        canvas.drawLine(startX, startY, yellowX, yellowY, linePaint);

        // គូរគ្រាប់ពណ៌លឿង (Yellow Endpoint) សម្រាប់អូសប្ដូរទិសដៅ
        canvas.drawCircle(yellowX, yellowY, radius, circlePaint);
    }
}
