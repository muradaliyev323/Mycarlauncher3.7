package com.mycar.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/** Кольцо вокруг обложки в мини-плеере: показывает, сколько трека уже сыграно. */
public class RingView extends View {

    private final Paint track = new Paint();
    private final Paint arc = new Paint();
    private final Paint dot = new Paint();
    private final RectF box = new RectF();
    private float progress;
    private float stroke = 4f;

    public RingView(Context context, AttributeSet attrs) {
        super(context, attrs);
        track.setAntiAlias(true);
        track.setStyle(Paint.Style.STROKE);
        track.setColor(0x33FFFFFF);
        arc.setAntiAlias(true);
        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeCap(Paint.Cap.ROUND);
        arc.setColor(0xFF6FC3FF);
        dot.setAntiAlias(true);
        dot.setColor(0xFF6FC3FF);
        setStroke(stroke);
    }

    void setStroke(float px) {
        stroke = px;
        track.setStrokeWidth(px);
        arc.setStrokeWidth(px);
        invalidate();
    }

    void setProgress(float p) {
        float v = Math.max(0f, Math.min(1f, p));
        if (v == progress) return;
        progress = v;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        float r = Math.min(w, h) / 2f - stroke * 1.5f;
        if (r <= 0) return;
        float cx = w / 2f;
        float cy = h / 2f;
        box.set(cx - r, cy - r, cx + r, cy + r);
        canvas.drawCircle(cx, cy, r, track);
        if (progress > 0f) {
            canvas.drawArc(box, -90f, 360f * progress, false, arc);
            double angle = Math.toRadians(-90.0 + 360.0 * progress);
            canvas.drawCircle(cx + (float) (r * Math.cos(angle)), cy + (float) (r * Math.sin(angle)),
                    stroke * 1.4f, dot);
        }
    }
}
