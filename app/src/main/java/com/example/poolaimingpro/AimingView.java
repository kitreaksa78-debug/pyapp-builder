package com.example.poolaimingpro;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;

public class AimingView extends View {
    private Paint mainLinePaint;
    private Paint reflectionPaint;
    private Paint targetBallPaint;
    private Paint textPaint;

    // ទីតាំងគ្រាប់បាល់ចាប់ផ្តើម (Cue Ball) និងគ្រាប់បាល់គោលដៅ (Target Ball)
    private float startX = 350f, startY = 900f;
    private float targetX = 650f, targetY = 500f;
    private float radius = 55f;
    
    private boolean isDragging = false;
    private AimService service;

    public AimingView(Context context) {
        super(context);
        init();
    }

    public void setService(AimService service) {
        this.service = service;
    }

    private void init() {
        // ខ្សែបន្ទាត់តម្រង់គោលដៅចម្បង (Cyan Line)
        mainLinePaint = new Paint();
        mainLinePaint.setColor(Color.CYAN);
        mainLinePaint.setStrokeWidth(8f);
        mainLinePaint.setAntiAlias(true);
        mainLinePaint.setStyle(Paint.Style.STROKE);

        // ខ្សែបន្ទាត់គណនាមុំផ្លាតគោលដៅ (Magenta Reflection Line)
        reflectionPaint = new Paint();
        reflectionPaint.setColor(Color.MAGENTA);
        reflectionPaint.setStrokeWidth(6f);
        reflectionPaint.setAntiAlias(true);
        reflectionPaint.setStyle(Paint.Style.STROKE);

        // គ្រាប់មូលពណ៌លឿងនៅចុងបន្ទាត់សម្រាប់អូសតម្រង់មុំ
        targetBallPaint = new Paint();
        targetBallPaint.setColor(Color.YELLOW);
        targetBallPaint.setAntiAlias(true);
        targetBallPaint.setStyle(Paint.Style.FILL);

        // អត្ថបទបង្ហាញព័ត៌មានមុំ
        textPaint = new Paint();
        textPaint.setColor(Color.GREEN);
        textPaint.setTextSize(36f);
        textPaint.setAntiAlias(true);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // ១. គណនា Vector និងមុំរវាងចំណុចទាំងពីរ
        float dx = targetX - startX;
        float dy = targetY - startY;
        float distance = (float) Math.sqrt(dx * dx + dy * dy);

        if (distance > 0) {
            // ពង្រីកប្រវែងខ្សែបន្ទាត់ចម្បងឱ្យវែងឆ្ងាយ (Extended Aim Ray)
            float rayLength = 1500f;
            float extendedX = startX + (dx / distance) * rayLength;
            float extendedY = startY + (dy / distance) * rayLength;

            canvas.drawLine(startX, startY, extendedX, extendedY, mainLinePaint);

            // ២. គណនាគន្លងមុំផ្លាតប៉ះទង្គិចធរណីមាត្រ (Cushion Reflection Physics Simulation)
            float bounceX = extendedX + (dx / distance) * 400f;
            float bounceY = extendedY - (dy / distance) * 400f;
            canvas.drawLine(extendedX, extendedY, bounceX, bounceY, reflectionPaint);

            // ៣. បង្ហាញអត្ថបទគណនាមុំប្រហាក់ប្រហែលនៅលើអេក្រង់
            double angleInDegrees = Math.toDegrees(Math.atan2(dy, dx));
            canvas.drawText("Angle: " + String.format("%.1f", angleInDegrees) + "°", 50f, 150f, textPaint);
        }

        // ៤. គូសគ្រាប់មូលបញ្ជា
        canvas.drawCircle(startX, startY, radius * 0.7f, targetBallPaint);
        canvas.drawCircle(targetX, targetY, radius, targetBallPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                // ពិនិត្យមើលថាតើប៉ះចំគ្រាប់មូលពណ៌លឿងនៅចុងបន្ទាត់ដែរឬទេ
                double dist = Math.sqrt(Math.pow(x - targetX, 2) + Math.pow(y - targetY, 2));
                if (dist <= radius + 50) {
                    isDragging = true;
                    if (service != null) {
                        service.updateTouchMode(true); // เปิดให้ลากได้
                    }
                    return true;
                }
                break;

            case MotionEvent.ACTION_MOVE:
                if (isDragging) {
                    targetX = x;
                    targetY = y;
                    invalidate(); // Update canvas view ភ្លាមៗ
                    return true;
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (isDragging) {
                    isDragging = false;
                    if (service != null) {
                        service.updateTouchMode(false); // ปิด Touch เพื่อให้กดเล่นเกมได้ปกติ
                    }
                    return true;
                }
                break;
        }
        return super.onTouchEvent(event);
    }
}
