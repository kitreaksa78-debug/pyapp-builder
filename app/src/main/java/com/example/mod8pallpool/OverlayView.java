package com.example.mod8pallpool;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

public class OverlayView extends View {

    private final Paint linePaint;

    private float startX = 0f;
    private float startY = 0f;
    private float endX = 0f;
    private float endY = 0f;

    public OverlayView(Context context) {
        super(context);
        linePaint = createLinePaint();
    }

    public OverlayView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        linePaint = createLinePaint();
    }

    public OverlayView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        linePaint = createLinePaint();
    }

    private Paint createLinePaint() {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.RED);
        paint.setStrokeWidth(6f);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        return paint;
    }

    /**
     * Updates the line endpoints and schedules a redraw of this view.
     */
    public void updateCoordinates(float sX, float sY, float eX, float eY) {
        this.startX = sX;
        this.startY = sY;
        this.endX = eX;
        this.endY = eY;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawLine(startX, startY, endX, endY, linePaint);
    }
}
