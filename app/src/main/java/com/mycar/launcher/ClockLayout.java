package com.mycar.launcher;

import android.app.Activity;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * Дата и время по центру на фото. Цифры стоят так, что их нижняя часть уходит за крышу машины
 * (верх машины лежит отдельным слоем поверх них). Положение считается от размеров экрана.
 */
final class ClockLayout {

    // Размеры и положение подготовленного фона (см. res/drawable-nodpi/car_bg.jpg и car_fg.png)
    private static final int BG_W = 1280;
    private static final int BG_H = 720;
    private static final int ROOF_Y = 197;
    private static final int CAR_CX = 650;

    private final View root;
    private final View box;
    private final TextView clock;
    private int lastW;
    private int lastH;
    private int lastBoxH;

    ClockLayout(Activity a, float u) {
        root = a.findViewById(R.id.root);
        box = a.findViewById(R.id.clock_box);
        clock = a.findViewById(R.id.clock_top);
        TextView date = a.findViewById(R.id.date_top);

        clock.setTextSize(TypedValue.COMPLEX_UNIT_PX, 150 * u);
        clock.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        date.setTextSize(TypedValue.COMPLEX_UNIT_PX, 22 * u);
        date.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        date.setLetterSpacing(0.14f);
    }

    void update() {
        int w = root.getWidth();
        int h = root.getHeight();
        int bh = box.getHeight();
        if (w <= 0 || h <= 0 || bh <= 0 || (w == lastW && h == lastH && bh == lastBoxH)) return;
        lastW = w;
        lastH = h;
        lastBoxH = bh;

        // фон растянут по центру с обрезкой: считаем, где на экране оказалась крыша машины
        float scale = Math.max((float) w / BG_W, (float) h / BG_H);
        float dx = (w - BG_W * scale) / 2f;
        float dy = (h - BG_H * scale) / 2f;
        float roof = dy + ROOF_Y * scale;

        Paint.FontMetrics fm = clock.getPaint().getFontMetrics();
        float baseline = roof + 0.22f * clock.getTextSize();
        float bottom = baseline + fm.descent;

        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) box.getLayoutParams();
        lp.topMargin = Math.max(0, Math.round(bottom - bh));
        box.setLayoutParams(lp);
        box.setTranslationX(dx + CAR_CX * scale - w / 2f);
    }
}
