package com.grogu.yt.ui;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/** body { background: radial-gradient(circle, ink 1.3px, transparent 1.5px) 18px 18px, paper } */
final class DotBackground extends Drawable {
    private final Paint paint = new Paint();
    private final int paper;

    DotBackground(int paper, int ink, float density) {
        this.paper = paper;
        int tile = Math.max(8, Math.round(18f * density));
        Bitmap bm = Bitmap.createBitmap(tile, tile, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bm);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(ink);
        c.drawCircle(tile / 2f, tile / 2f, 1.4f * density, p);
        paint.setShader(new BitmapShader(bm, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
    }

    @Override public void draw(Canvas canvas) {
        canvas.drawColor(paper);
        canvas.drawRect(getBounds(), paint);
    }
    @Override public void setAlpha(int alpha) { }
    @Override public void setColorFilter(ColorFilter cf) { }
    @Override public int getOpacity() { return PixelFormat.OPAQUE; }
}
