package com.grogu.yt.audio;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;

/** Minimal ID3v2.3 tag (title, artist, album, front cover) – prepended to the MP3. */
public final class Id3Writer {
    private Id3Writer() {}

    private static final Charset UTF16 = Charset.forName("UTF-16"); // writes BOM + big endian

    public static byte[] build(Meta m) {
        if (m == null) return new byte[0];
        try {
            ByteArrayOutputStream frames = new ByteArrayOutputStream();
            if (Meta.has(m.title)) text(frames, "TIT2", m.title);
            if (Meta.has(m.artist)) text(frames, "TPE1", m.artist);
            if (Meta.has(m.album)) text(frames, "TALB", m.album);
            if (m.coverJpeg != null && m.coverJpeg.length > 0) {
                ByteArrayOutputStream p = new ByteArrayOutputStream();
                p.write(0);                                   // text encoding: ISO-8859-1 (for mime + description)
                p.write("image/jpeg".getBytes("ISO-8859-1")); p.write(0);
                p.write(3);                                   // picture type: front cover
                p.write(0);                                   // empty description
                p.write(m.coverJpeg);
                frame(frames, "APIC", p.toByteArray());
            }
            byte[] body = frames.toByteArray();
            if (body.length == 0) return new byte[0];
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write(new byte[]{'I', 'D', '3', 3, 0, 0});    // v2.3.0, no flags
            out.write(synchsafe(body.length));
            out.write(body);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);               // ByteArrayOutputStream never throws
        }
    }

    private static void text(ByteArrayOutputStream o, String id, String value) throws IOException {
        byte[] t = value.getBytes(UTF16);
        byte[] p = new byte[1 + t.length + 2];
        p[0] = 1;                                             // UTF-16 with BOM
        System.arraycopy(t, 0, p, 1, t.length);               // trailing 2 zero bytes = terminator
        frame(o, id, p);
    }

    private static void frame(ByteArrayOutputStream o, String id, byte[] payload) throws IOException {
        o.write(id.getBytes("US-ASCII"));
        o.write(new byte[]{(byte) (payload.length >>> 24), (byte) (payload.length >>> 16),
                (byte) (payload.length >>> 8), (byte) payload.length}); // v2.3: plain big-endian size
        o.write(0); o.write(0);                               // flags
        o.write(payload);
    }

    private static byte[] synchsafe(int n) {
        return new byte[]{(byte) ((n >>> 21) & 0x7f), (byte) ((n >>> 14) & 0x7f),
                (byte) ((n >>> 7) & 0x7f), (byte) (n & 0x7f)};
    }
}
