package com.grogu.yt.ui;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;

import java.io.*;

/** "Download" button: copies the finished file into the public Downloads folder. */
final class Saver {
    private Saver() {}

    static boolean needsLegacyPermission() { return Build.VERSION.SDK_INT < 29; }

    static String save(Context ctx, File src, String name, String mime) throws IOException {
        if (Build.VERSION.SDK_INT >= 29) {
            ContentResolver cr = ctx.getContentResolver();
            ContentValues v = new ContentValues();
            v.put("_display_name", name);
            v.put("mime_type", mime);
            v.put("relative_path", Environment.DIRECTORY_DOWNLOADS);
            v.put("is_pending", 1);
            Uri uri = cr.insert(Uri.parse("content://media/external/downloads"), v);
            if (uri == null) throw new IOException("Couldn't create file in Downloads.");
            OutputStream out = cr.openOutputStream(uri);
            if (out == null) throw new IOException("Couldn't open file in Downloads.");
            copy(src, out);
            ContentValues done = new ContentValues();
            done.put("is_pending", 0);
            cr.update(uri, done, null, null);
            return Environment.DIRECTORY_DOWNLOADS + "/" + name;
        }
        File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("Downloads folder unavailable.");
        File dest = new File(dir, name);
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name, ext = dot > 0 ? name.substring(dot) : "";
        for (int i = 1; dest.exists(); i++) dest = new File(dir, base + " (" + i + ")" + ext);
        copy(src, new FileOutputStream(dest));
        MediaScannerConnection.scanFile(ctx, new String[]{dest.getAbsolutePath()}, new String[]{mime}, null);
        return Environment.DIRECTORY_DOWNLOADS + "/" + dest.getName();
    }

    private static void copy(File src, OutputStream out) throws IOException {
        InputStream in = new FileInputStream(src);
        try {
            byte[] b = new byte[64 * 1024]; int r;
            while ((r = in.read(b)) > 0) out.write(b, 0, r);
        } finally { in.close(); out.close(); }
    }
}
