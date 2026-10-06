package com.grogu.yt.core;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.ByteArrayOutputStream;

/** Thumbnail -> square-ish JPEG for embedding (the Flask app used --convert-thumbnails jpg). */
public final class Cover {
    private Cover() {}

    /** @return JPEG bytes, or null if anything goes wrong (cover art is optional). */
    public static byte[] fetchJpeg(String url) {
        if (url == null) return null;
        try {
            byte[] raw = Http.getBytesOrNull(url, 8L * 1024 * 1024);
            if (raw == null) return null;
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(raw, 0, raw.length, o);
            int sample = 1;
            while (o.outWidth / (sample * 2) >= 800) sample *= 2;
            o = new BitmapFactory.Options();
            o.inSampleSize = sample;
            Bitmap bmp = BitmapFactory.decodeByteArray(raw, 0, raw.length, o);
            if (bmp == null) return null;
            // 16:9 video thumbnails -> centered square, like a music cover
            int s = Math.min(bmp.getWidth(), bmp.getHeight());
            Bitmap sq = Bitmap.createBitmap(bmp, (bmp.getWidth() - s) / 2, (bmp.getHeight() - s) / 2, s, s);
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            sq.compress(Bitmap.CompressFormat.JPEG, 90, bo);
            return bo.toByteArray();
        } catch (Throwable t) {
            return null;
        }
    }
}
