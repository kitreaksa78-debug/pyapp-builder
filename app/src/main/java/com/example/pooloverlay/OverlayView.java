package com.example.pooloverlay;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

public class OverlayView extends View {

    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float startX = 0f;
    private float startY = 0f;
    private float endX = 0f;
    private float endY = 0f;
    private boolean hasLine = false;

    public OverlayView(Context context) {
        super(context);
        outlinePaint.setColor(Color.BLACK);
        outlinePaint.setStrokeWidth(10f);
        outlinePaint.setStyle(Paint.Style.STROKE);

        linePaint.setColor(Color.YELLOW);
        linePaint.setStrokeWidth(5f);
        linePaint.setStyle(Paint.Style.STROKE);

        pointPaint.setColor(Color.CYAN);
        pointPaint.setStrokeWidth(5f);
        pointPaint.setStyle(Paint.Style.STROKE);
    }

    public void setAimLine(float startX, float startY, float endX, float endY) {
        this.startX = startX;
        this.startY = startY;
        this.endX = endX;
        this.endY = endY;
        this.hasLine = (startX != 0f || startY != 0f || endX != 0f || endY != 0f);
        invalidate();
    }

    public void clearLine() {
        this.startX = 0f;
        this.startY = 0f;
        this.endX = 0f;
        this.endY = 0f;
        this.hasLine = false;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!hasLine) return;

        canvas.drawLine(startX, startY, endX, endY, outlinePaint);
        canvas.drawLine(startX, startY, endX, endY, linePaint);
        canvas.drawCircle(startX, startY, 16f, pointPaint);
        canvas.drawCircle(endX, endY, 12f, pointPaint);
    }
}
