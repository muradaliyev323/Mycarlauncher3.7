package com.mycar.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/** Полоса перемотки в виде «волны»: пройденная часть цветная, остальное серое. Можно водить пальцем. */
public class WaveView extends View {

    interface Listener {
        void onSeek(float fraction);
    }

    private static final int BARS = 56;

    private final Paint on = new Paint();
    private final Paint off = new Paint();
    private final float[] heights = new float[BARS];
    private float progress;
    private boolean dragging;
    private Listener listener;
    private int fixedHeight;

    public WaveView(Context context, AttributeSet attrs) {
        super(context, attrs);
        on.setColor(0xFFFF5A6A);
        off.setColor(0xFF3A4150);
        for (int i = 0; i < BARS; i++) {
            double v = Math.abs(Math.sin(i * 1.7) * Math.cos(i * 0.6));
            heights[i] = (float) Math.min(0.95, 0.2 + 0.75 * v);
        }
    }

    void setListener(Listener l) {
        listener = l;
    }

    void setBarHeight(int px) {
        fixedHeight = px;
        requestLayout();
    }

    boolean isDragging() {
        return dragging;
    }

    float getProgress() {
        return progress;
    }

    void setProgress(float f) {
        if (dragging) return;
        progress = Math.max(0f, Math.min(1f, f));
        invalidate();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int w = View.MeasureSpec.getSize(widthSpec);
        setMeasuredDimension(w, fixedHeight > 0 ? fixedHeight : 40);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        float slot = (float) w / BARS;
        float bar = slot * 0.6f;
        for (int i = 0; i < BARS; i++) {
            float bh = h * heights[i];
            float left = i * slot + (slot - bar) / 2f;
            Paint p = (i + 0.5f) / BARS <= progress ? on : off;
            canvas.drawRect(left, (h - bh) / 2f, left + bar, (h + bh) / 2f, p);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int a = e.getAction();
        if (a == MotionEvent.ACTION_DOWN || a == MotionEvent.ACTION_MOVE) {
            dragging = true;
            progress = Math.max(0f, Math.min(1f, e.getX() / Math.max(1, getWidth())));
            invalidate();
            return true;
        }
        if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
            dragging = false;
            if (a == MotionEvent.ACTION_UP && listener != null) listener.onSeek(progress);
            return true;
        }
        return super.onTouchEvent(e);
    }
}
