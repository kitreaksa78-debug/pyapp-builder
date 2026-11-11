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
            private float startX = 200f, startY = 200f;
                private float endX = 500f, endY = 800f;
                    private OverlayService service;
                        private boolean isDrawingMode = true; // កំណត់ថាអាចគូសបាន

                            public LineOverlayView(Context context) {
                                    super(context);
                                            init();
                                                }

                                                    public void setService(OverlayService service) {
                                                            this.service = service;
                                                                    if (service != null) {
                                                                                service.setTouchEnabled(isDrawingMode);
                                                                                        }
                                                                                            }

                                                                                                private void init() {
                                                                                                        linePaint = new Paint();
                                                                                                                linePaint.setColor(Color.CYAN);
                                                                                                                        linePaint.setStrokeWidth(8f);
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
                                                                                                                                                                                                    // គូសខ្សែបន្ទាត់
                                                                                                                                                                                                            canvas.drawLine(startX, startY, endX, endY, linePaint);
                                                                                                                                                                                                                    // គូសចំណុចចាប់ផ្តើម និងចុងបញ្ចប់
                                                                                                                                                                                                                            canvas.drawCircle(startX, startY, 25f, pointPaint);
                                                                                                                                                                                                                                    canvas.drawCircle(endX, endY, 35f, pointPaint);
                                                                                                                                                                                                                                        }

                                                                                                                                                                                                                                            @Override
                                                                                                                                                                                                                                                public boolean onTouchEvent(MotionEvent event) {
                                                                                                                                                                                                                                                        float x = event.getX();
                                                                                                                                                                                                                                                                float y = event.getY();

                                                                                                                                                                                                                                                                        switch (event.getAction()) {
                                                                                                                                                                                                                                                                                    case MotionEvent.ACTION_DOWN:
                                                                                                                                                                                                                                                                                                    // ពេលចុចប៉ះ អាចកំណត់ឱ្យចំណុចចាប់ផ្តើមរំកិលមកកន្លែងប៉ះ ឬអូសផ្លាស់ប្តូរទីតាំងចុងបន្ទាត់
                                                                                                                                                                                                                                                                                                                    endX = x;
                                                                                                                                                                                                                                                                                                                                    endY = y;
                                                                                                                                                                                                                                                                                                                                                    invalidate();
                                                                                                                                                                                                                                                                                                                                                                    return true;

                                                                                                                                                                                                                                                                                                                                                                                case MotionEvent.ACTION_MOVE:
                                                                                                                                                                                                                                                                                                                                                                                                endX = x;
                                                                                                                                                                                                                                                                                                                                                                                                                endY = y;
                                                                                                                                                                                                                                                                                                                                                                                                                                invalidate();
                                                                                                                                                                                                                                                                                                                                                                                                                                                return true;

                                                                                                                                                                                                                                                                                                                                                                                                                                                            case MotionEvent.ACTION_UP:
                                                                                                                                                                                                                                                                                                                                                                                                                                                                            return true;
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    }
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            return super.onTouchEvent(event);
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                }
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                }