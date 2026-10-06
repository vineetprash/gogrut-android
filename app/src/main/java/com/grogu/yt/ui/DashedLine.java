package com.grogu.yt.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.view.View;

/** border-top: 3px dashed ink */
final class DashedLine extends View {
    private final Paint p = new Paint();
    DashedLine(Context c, int color, float thicknessPx) {
        super(c);
        p.setColor(color);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(thicknessPx);
        p.setPathEffect(new DashPathEffect(new float[]{thicknessPx * 2f, thicknessPx * 1.2f}, 0));
    }
    @Override protected void onDraw(Canvas canvas) {
        float y = getHeight() / 2f;
        canvas.drawLine(0, y, getWidth(), y, p);
    }
}
