package com.grogu.yt.audio;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Tiny AAC-in-MP4 (.m4a) muxer with iTunes-style tags + cover. Written by hand because
 * android.media.MediaMuxer cannot write tags/cover art and YouTube's DASH m4a is fragmented.
 * Output is "faststart" (moov before mdat) with a single chunk, so every player seeks it fine.
 */
public final class M4aWriter {
    private final File out;
    private final File tmp;
    private final OutputStream mdat;
    private final int sampleRate, channels, avgBitrate;
    private final byte[] asc;
    private final Meta meta;
    private final List<Integer> sizes = new ArrayList<Integer>();
    private final List<Long> ptsUs = new ArrayList<Long>();
    private long mdatBytes;

    public M4aWriter(File out, int sampleRate, int channels, byte[] asc, int avgBitrate, Meta meta) throws IOException {
        this.out = out;
        this.tmp = new File(out.getPath() + ".mdat");
        this.mdat = new java.io.BufferedOutputStream(new FileOutputStream(tmp), 64 * 1024);
        this.sampleRate = sampleRate;
        this.channels = channels;
        this.asc = asc != null ? asc : defaultAsc(sampleRate, channels);
        this.avgBitrate = avgBitrate;
        this.meta = meta == null ? Meta.NONE : meta;
    }

    private static byte[] defaultAsc(int rate, int ch) {
        int[] r = {96000, 88200, 64000, 48000, 44100, 32000, 24000, 22050, 16000, 12000, 11025, 8000, 7350};
        int fi = 4;
        for (int i = 0; i < r.length; i++) if (r[i] == rate) fi = i;
        return new byte[]{(byte) ((2 << 3) | (fi >> 1)), (byte) (((fi & 1) << 7) | (ch << 3))};
    }

    /** @param presentationUs presentation timestamp of this access unit, in microseconds */
    public void addSample(byte[] data, int off, int len, long presentationUs) throws IOException {
        mdat.write(data, off, len);
        sizes.add(len);
        ptsUs.add(presentationUs);
        mdatBytes += len;
    }

    public void finish() throws IOException {
        mdat.close();
        int n = sizes.size();
        // per-sample durations in media-timescale ticks (timescale = sample rate), from pts deltas
        long[] dur = new long[n];
        long total = 0;
        for (int i = 0; i < n; i++) {
            long d;
            if (i + 1 < n) d = Math.round((ptsUs.get(i + 1) - ptsUs.get(i)) * (double) sampleRate / 1e6);
            else d = i > 0 ? dur[i - 1] : 1024;
            if (d <= 0) d = 1024;
            dur[i] = d;
            total += d;
        }
        byte[] ftyp = box("ftyp", cat(ascii("M4A "), u32(0), ascii("M4A "), ascii("mp42"), ascii("isom"), u32(0)));
        byte[] moovNoOffset = buildMoov(dur, total, 0);
        long dataOffset = ftyp.length + moovNoOffset.length + 8L;       // +8 = mdat header
        byte[] moov = buildMoov(dur, total, dataOffset);
        OutputStream o = new java.io.BufferedOutputStream(new FileOutputStream(out), 64 * 1024);
        try {
            o.write(ftyp);
            o.write(moov);
            if (mdatBytes + 8 > 0xFFFFFFFFL) throw new IOException("m4a too large");
            o.write(u32(mdatBytes + 8));
            o.write(ascii("mdat"));
            InputStream in = new FileInputStream(tmp);
            try {
                byte[] buf = new byte[64 * 1024];
                int r;
                while ((r = in.read(buf)) > 0) o.write(buf, 0, r);
            } finally { in.close(); }
        } finally { o.close(); tmp.delete(); }
    }

    // ------------------------------------------------------------------ boxes

    private byte[] buildMoov(long[] dur, long totalTicks, long chunkOffset) throws IOException {
        int n = sizes.size();
        long durMs = Math.round(totalTicks * 1000.0 / sampleRate);

        byte[] mvhd = box("mvhd", cat(u32(0), u32(0), u32(0), u32(1000), u32(durMs), u32(0x00010000), u16(0x0100), new byte[10],
                matrix(), new byte[24], u32(2)));
        byte[] tkhd = box("tkhd", cat(u32(0x00000003), u32(0), u32(0), u32(1), u32(0), u32(durMs), new byte[8],
                u16(0), u16(0), u16(0x0100), u16(0), matrix(), u32(0), u32(0)));
        byte[] mdhd = box("mdhd", cat(u32(0), u32(0), u32(0), u32(sampleRate), u32(totalTicks), u16(0x55C4), u16(0)));
        byte[] hdlr = box("hdlr", cat(u32(0), u32(0), ascii("soun"), new byte[12], ascii("SoundHandler"), new byte[]{0}));
        byte[] smhd = box("smhd", cat(u32(0), u16(0), u16(0)));
        byte[] dinf = box("dinf", box("dref", cat(u32(0), u32(1), box("url ", u32(1)))));

        // esds
        ByteArrayOutputStream dsi = new ByteArrayOutputStream();
        dsi.write(0x05); dsi.write(asc.length); dsi.write(asc);
        byte[] dsiB = dsi.toByteArray();
        byte[] dcd = cat(new byte[]{0x40, 0x15}, new byte[]{0, 0x18, 0}, u32(avgBitrate > 0 ? avgBitrate : 0), u32(avgBitrate > 0 ? avgBitrate : 0), dsiB);
        byte[] dcdD = cat(new byte[]{0x04, (byte) dcd.length}, dcd);
        byte[] sl = new byte[]{0x06, 0x01, 0x02};
        byte[] esBody = cat(u16(0), new byte[]{0}, dcdD, sl);
        byte[] esds = box("esds", cat(u32(0), new byte[]{0x03, (byte) esBody.length}, esBody));
        byte[] mp4a = box("mp4a", cat(new byte[6], u16(1), new byte[8], u16(channels), u16(16), u16(0), u16(0), u32((long) sampleRate << 16), esds));
        byte[] stsd = box("stsd", cat(u32(0), u32(1), mp4a));

        // stts: run-length of durations
        ByteArrayOutputStream stts = new ByteArrayOutputStream();
        int runs = 0;
        ByteArrayOutputStream sb = new ByteArrayOutputStream();
        int i = 0;
        while (i < n) {
            int j = i;
            while (j < n && dur[j] == dur[i]) j++;
            sb.write(u32(j - i)); sb.write(u32(dur[i]));
            runs++;
            i = j;
        }
        byte[] sttsB = box("stts", cat(u32(0), u32(runs), sb.toByteArray()));
        byte[] stsc = box("stsc", cat(u32(0), u32(1), u32(1), u32(n), u32(1)));
        ByteArrayOutputStream sz = new ByteArrayOutputStream();
        for (int s : sizes) sz.write(u32(s));
        byte[] stsz = box("stsz", cat(u32(0), u32(0), u32(n), sz.toByteArray()));
        byte[] stco = box("stco", cat(u32(0), u32(1), u32(chunkOffset)));

        byte[] stbl = box("stbl", cat(stsd, sttsB, stsc, stsz, stco));
        byte[] minf = box("minf", cat(smhd, dinf, stbl));
        byte[] mdia = box("mdia", cat(mdhd, hdlr, minf));
        byte[] trak = box("trak", cat(tkhd, mdia));
        byte[] udta = buildUdta();
        return box("moov", cat(mvhd, trak, udta));
    }

    private byte[] buildUdta() throws IOException {
        ByteArrayOutputStream ilst = new ByteArrayOutputStream();
        if (Meta.has(meta.title)) ilst.write(textItem(new byte[]{(byte) 0xA9, 'n', 'a', 'm'}, meta.title));
        if (Meta.has(meta.artist)) ilst.write(textItem(new byte[]{(byte) 0xA9, 'A', 'R', 'T'}, meta.artist));
        if (Meta.has(meta.album)) ilst.write(textItem(new byte[]{(byte) 0xA9, 'a', 'l', 'b'}, meta.album));
        if (meta.coverJpeg != null && meta.coverJpeg.length > 0) {
            ilst.write(box("covr", box("data", cat(u32(13), u32(0), meta.coverJpeg))));
        }
        if (ilst.size() == 0) return new byte[0];
        byte[] mhdlr = box("hdlr", cat(u32(0), u32(0), ascii("mdir"), ascii("appl"), u32(0), u32(0), new byte[]{0}));
        byte[] meta_ = box("meta", cat(u32(0), mhdlr, box("ilst", ilst.toByteArray())));
        return box("udta", meta_);
    }

    private static byte[] textItem(byte[] type, String value) throws IOException {
        byte[] v = value.getBytes("UTF-8");
        byte[] data = box("data", cat(u32(1), u32(0), v));
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.write(u32(8 + data.length));
        o.write(type);
        o.write(data);
        return o.toByteArray();
    }

    private static byte[] matrix() {
        return cat(u32(0x00010000), u32(0), u32(0), u32(0), u32(0x00010000), u32(0), u32(0), u32(0), u32(0x40000000));
    }

    private static byte[] box(String type, byte[]... parts) {
        byte[] body = cat(parts);
        return cat(u32(8 + body.length), ascii(type), body);
    }

    private static byte[] ascii(String s) { try { return s.getBytes("ISO-8859-1"); } catch (IOException e) { throw new IllegalStateException(e); } }
    private static byte[] u32(long v) { return new byte[]{(byte) (v >>> 24), (byte) (v >>> 16), (byte) (v >>> 8), (byte) v}; }
    private static byte[] u16(int v) { return new byte[]{(byte) (v >>> 8), (byte) v}; }
    private static byte[] cat(byte[]... p) {
        int n = 0; for (byte[] b : p) n += b.length;
        byte[] r = new byte[n]; int o = 0;
        for (byte[] b : p) { System.arraycopy(b, 0, r, o, b.length); o += b.length; }
        return r;
    }
}
