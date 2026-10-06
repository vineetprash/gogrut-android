package com.grogu.yt.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.widget.FrameLayout;

/**
 * A FrameLayout that paints the "hard offset shadow + thick border" look from the web UI
 * (border:3px solid ink; box-shadow: 8px 8px 0 pink, 14px 14px 0 ink). Shadows live in the
 * view's right/bottom padding; the :active state (translate + no shadow) is {@link #setPressed}.
 */
public class Brutal extends FrameLayout {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    private int face, borderColor;
    private float borderPx;
    private float[] offs = new float[0];
    private int[] cols = new int[0];
    private float maxOff;
    private boolean pressEffect, pressedLook, clipFace;

    Brutal(Context c, int face, int borderColor, float borderDp) {
        super(c);
        density = c.getResources().getDisplayMetrics().density;
        this.face = face;
        this.borderColor = borderColor;
        this.borderPx = borderDp * density;
        setWillNotDraw(false);
    }

    /** Shadows are painted in order (largest/back-most first), offsets in dp. */
    Brutal shadows(float[] dp, int[] colors) {
        offs = new float[dp.length];
        float m = 0;
        for (int i = 0; i < dp.length; i++) { offs[i] = dp[i] * density; m = Math.max(m, offs[i]); }
        cols = colors;
        maxOff = m;
        setPadding(0, 0, Math.round(m), Math.round(m));
        invalidate();
        return this;
    }

    Brutal pressEffect(boolean on) { pressEffect = on; setClickable(on); return this; }
    Brutal clipToFace(boolean on) { clipFace = on; return this; }

    void setFace(int c) { face = c; invalidate(); }
    void setBorderColor(int c) { borderColor = c; invalidate(); }
    void setShadowColors(int[] colors) { cols = colors; invalidate(); }
    float shadowExtentPx() { return maxOff; }

    @Override public void setPressed(boolean pressed) {
        super.setPressed(pressed);
        if (pressEffect && pressedLook != pressed) { pressedLook = pressed; invalidate(); }
    }

    @Override public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        setAlpha(enabled ? 1f : 0.55f);
    }

    @Override public void draw(Canvas canvas) {
        if (pressedLook) {
            canvas.save();
            canvas.translate(maxOff, maxOff);
            super.draw(canvas);
            canvas.restore();
        } else {
            super.draw(canvas);
        }
    }

    private float faceW() { return getWidth() - maxOff; }
    private float faceH() { return getHeight() - maxOff; }

    @Override protected void onDraw(Canvas canvas) {
        float fw = faceW(), fh = faceH();
        if (!pressedLook) {
            for (int i = 0; i < offs.length; i++) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(cols[i]);
                canvas.drawRect(offs[i], offs[i], offs[i] + fw, offs[i] + fh, paint);
            }
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(face);
        canvas.drawRect(0, 0, fw, fh, paint);
        if (borderPx > 0) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(borderPx);
            paint.setColor(borderColor);
            float h = borderPx / 2f;
            canvas.drawRect(h, h, fw - h, fh - h, paint);
        }
    }

    @Override protected void dispatchDraw(Canvas canvas) {
        if (clipFace) {
            canvas.save();
            canvas.clipRect(0, 0, faceW(), faceH());
            super.dispatchDraw(canvas);
            canvas.restore();
        } else {
            super.dispatchDraw(canvas);
        }
    }
}
