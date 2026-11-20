package com.example.pooloverlay;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class DrawingView extends View {

    private final Paint cueLinePaint;
    private final Paint targetLinePaint;
    private final Paint circlePaint;
    private final List<ShotLine> shotLines = new ArrayList<>();

    public static class ShotLine {
        float cueX, cueY, ghostX, ghostY, targetX, targetY, pocketX, pocketY;
        boolean isPrimary;
    }

    public DrawingView(Context context) {
        super(context);

        cueLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        cueLinePaint.setStrokeWidth(5f);
        cueLinePaint.setStyle(Paint.Style.STROKE);

        targetLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        targetLinePaint.setColor(Color.RED);
        targetLinePaint.setStrokeWidth(5f);
        targetLinePaint.setStyle(Paint.Style.STROKE);

        circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        circlePaint.setColor(Color.WHITE);
        circlePaint.setStrokeWidth(3f);
        circlePaint.setStyle(Paint.Style.STROKE);
    }

    public void updateShots(JSONArray shotsArray) {
        shotLines.clear();
        if (shotsArray == null) {
            postInvalidate();
            return;
        }
        try {
            for (int i = 0; i < shotsArray.length(); i++) {
                JSONObject obj = shotsArray.getJSONObject(i);
                JSONArray cue = obj.optJSONArray("cue_ball");
                JSONArray ghost = obj.optJSONArray("ghost_ball");
                JSONArray target = obj.optJSONArray("target_ball");
                JSONArray pocket = obj.optJSONArray("pocket");

                if (cue == null || ghost == null || target == null || pocket == null) continue;

                ShotLine line = new ShotLine();
                line.cueX = (float) cue.getDouble(0);
                line.cueY = (float) cue.getDouble(1);
                line.ghostX = (float) ghost.getDouble(0);
                line.ghostY = (float) ghost.getDouble(1);
                line.targetX = (float) target.getDouble(0);
                line.targetY = (float) target.getDouble(1);
                line.pocketX = (float) pocket.getDouble(0);
                line.pocketY = (float) pocket.getDouble(1);
                line.isPrimary = (i == 0);
                shotLines.add(line);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        postInvalidate();
    }

    public void updateFromLinesObject(JSONObject linesObj) {
        shotLines.clear();
        if (linesObj == null) {
            postInvalidate();
            return;
        }
        try {
            ShotLine line = new ShotLine();
            line.isPrimary = true;

            if (linesObj.has("cue_to_ghost")) {
                JSONArray c2g = linesObj.getJSONArray("cue_to_ghost");
                JSONArray p1 = c2g.getJSONArray(0);
                JSONArray p2 = c2g.getJSONArray(1);
                line.cueX = (float) p1.getDouble(0);
                line.cueY = (float) p1.getDouble(1);
                line.ghostX = (float) p2.getDouble(0);
                line.ghostY = (float) p2.getDouble(1);
            }

            if (linesObj.has("target_to_pocket")) {
                JSONArray t2p = linesObj.getJSONArray("target_to_pocket");
                JSONArray p3 = t2p.getJSONArray(0);
                JSONArray p4 = t2p.getJSONArray(1);
                line.targetX = (float) p3.getDouble(0);
                line.targetY = (float) p3.getDouble(1);
                line.pocketX = (float) p4.getDouble(0);
                line.pocketY = (float) p4.getDouble(1);
            }

            shotLines.add(line);
        } catch (Exception e) {
            e.printStackTrace();
        }
        postInvalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        for (ShotLine s : shotLines) {
            cueLinePaint.setColor(s.isPrimary ? Color.GREEN : Color.YELLOW);
            canvas.drawLine(s.cueX, s.cueY, s.ghostX, s.ghostY, cueLinePaint);
            canvas.drawCircle(s.ghostX, s.ghostY, 22f, circlePaint);
            canvas.drawLine(s.targetX, s.targetY, s.pocketX, s.pocketY, targetLinePaint);
        }
    }
}
