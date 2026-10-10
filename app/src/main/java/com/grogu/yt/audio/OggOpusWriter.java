package com.grogu.yt.audio;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Writes Opus packets (as YouTube serves them inside WebM) into a standard Ogg Opus `.opus` file
 * (RFC 7845), with Vorbis-comment tags and an embedded cover (METADATA_BLOCK_PICTURE).
 * Pure Java so it behaves the same on every Android version.
 */
public final class OggOpusWriter {
    private static final int SERIAL = 0x47524F47;           // "GROG"
    private static final int[] CRC = new int[256];
    static {
        for (int i = 0; i < 256; i++) {
            int r = i << 24;
            for (int j = 0; j < 8; j++) r = (r & 0x80000000) != 0 ? (r << 1) ^ 0x04C11DB7 : r << 1;
            CRC[i] = r;
        }
    }

    private final OutputStream out;
    private int seq;
    private long granule;                                     // 48 kHz samples incl. pre-skip
    private final ByteArrayOutputStream pageData = new ByteArrayOutputStream();
    private final int[] pageSegs = new int[255];
    private int segCount;
    private boolean closed;
    private static final int MAX_PAGE_BYTES = 8 * 1024;

    /**
     * @param opusHead the 19-byte "OpusHead" identification header (csd-0 of the WebM track) or null
     */
    public OggOpusWriter(OutputStream out, byte[] opusHead, int channels, int inputRate, Meta meta) throws IOException {
        this.out = out;
        byte[] head = isOpusHead(opusHead) ? opusHead : defaultHead(channels, inputRate);
        int preSkip = (head[10] & 0xff) | ((head[11] & 0xff) << 8);
        this.granule = 0;
        this.preSkip = preSkip;
        writePage(0x02, 0, new byte[][]{head}, true);          // BOS, granule 0
        writePage(0x00, 0, new byte[][]{buildTags(meta)}, true);
    }

    private final int preSkip;

    private static boolean isOpusHead(byte[] b) {
        return b != null && b.length >= 19 && b[0] == 'O' && b[1] == 'p' && b[2] == 'u' && b[3] == 's'
                && b[4] == 'H' && b[5] == 'e' && b[6] == 'a' && b[7] == 'd';
    }

    private static byte[] defaultHead(int channels, int inputRate) {
        byte[] h = new byte[19];
        System.arraycopy(new byte[]{'O', 'p', 'u', 's', 'H', 'e', 'a', 'd'}, 0, h, 0, 8);
        h[8] = 1;                                              // version
        h[9] = (byte) channels;
        h[10] = (byte) (312 & 0xff); h[11] = (byte) (312 >> 8); // pre-skip: libopus default 6.5 ms
        h[12] = (byte) inputRate; h[13] = (byte) (inputRate >> 8); h[14] = (byte) (inputRate >> 16); h[15] = (byte) (inputRate >> 24);
        h[16] = 0; h[17] = 0;                                  // output gain
        h[18] = 0;                                             // mapping family 0 (mono/stereo)
        return h;
    }

    // ------------------------------------------------------------------ audio

    /** Duration of one Opus packet in 48 kHz samples, from its TOC byte (RFC 6716 §3.1). */
    public static int packetSamples(byte[] d, int off, int len) {
        if (len < 1) return 0;
        int toc = d[off] & 0xff;
        int cfg = toc >> 3;
        int frame;                                             // samples per frame at 48k
        if (cfg < 12) frame = new int[]{480, 960, 1920, 2880}[cfg & 3];
        else if (cfg < 16) frame = (cfg & 1) == 0 ? 480 : 960;
        else frame = new int[]{120, 240, 480, 960}[cfg & 3];
        int code = toc & 3, count;
        if (code == 0) count = 1;
        else if (code == 3) count = len >= 2 ? (d[off + 1] & 0x3f) : 1;
        else count = 2;
        return frame * count;
    }

    public void writePacket(byte[] data, int off, int len) throws IOException {
        byte[] p = new byte[len];
        System.arraycopy(data, off, p, 0, len);
        int segs = len / 255 + 1;                              // lacing: len/255 full segments + remainder (0 if exact)
        if (segCount + segs > 255 || pageData.size() >= MAX_PAGE_BYTES) flushAudioPage(false);
        if (segs > 255) throw new IOException("opus packet too large");
        for (int i = 0; i < segs - 1; i++) pageSegs[segCount++] = 255;
        pageSegs[segCount++] = len % 255;
        pageData.write(p);
        granule += packetSamples(data, off, len);
    }

    public void finish() throws IOException {
        if (closed) return;
        closed = true;
        flushAudioPage(true);
        out.flush();
    }

    private void flushAudioPage(boolean eos) throws IOException {
        if (segCount == 0 && !eos) return;
        byte[] header = new byte[27 + segCount];
        header[0] = 'O'; header[1] = 'g'; header[2] = 'g'; header[3] = 'S';
        header[5] = (byte) (eos ? 0x04 : 0x00);
        long gp = granule + preSkip;
        for (int i = 0; i < 8; i++) header[6 + i] = (byte) (gp >>> (8 * i));
        for (int i = 0; i < 4; i++) header[14 + i] = (byte) (SERIAL >>> (8 * i));
        for (int i = 0; i < 4; i++) header[18 + i] = (byte) (seq >>> (8 * i));
        seq++;
        header[26] = (byte) segCount;
        for (int i = 0; i < segCount; i++) header[27 + i] = (byte) pageSegs[i];
        byte[] body = pageData.toByteArray();
        int crc = 0;
        for (byte b : header) crc = (crc << 8) ^ CRC[((crc >>> 24) ^ (b & 0xff)) & 0xff];
        for (byte b : body) crc = (crc << 8) ^ CRC[((crc >>> 24) ^ (b & 0xff)) & 0xff];
        for (int i = 0; i < 4; i++) header[22 + i] = (byte) (crc >>> (8 * i));
        out.write(header);
        out.write(body);
        pageData.reset();
        segCount = 0;
    }

    /** Header pages hold one packet each (OpusHead / OpusTags); a big OpusTags (cover art) spans pages via lacing. */
    private void writePage(int flags, long gp, byte[][] packets, boolean headerPage) throws IOException {
        byte[] pkt = packets[0];
        int offset = 0;
        boolean first = true;
        while (true) {
            int remaining = pkt.length - offset;
            boolean continues = remaining / 255 + 1 > 255;      // doesn't fit in one page's 255 segments
            int chunk = continues ? 255 * 255 : remaining;
            int segs = continues ? 255 : remaining / 255 + 1;
            byte[] header = new byte[27 + segs];
            header[0] = 'O'; header[1] = 'g'; header[2] = 'g'; header[3] = 'S';
            header[5] = (byte) (first ? (flags & 0x02) : 0x01);  // BOS on the first page, "continued packet" afterwards
            long g = continues ? -1L : gp;
            for (int i = 0; i < 8; i++) header[6 + i] = (byte) (g >>> (8 * i));
            for (int i = 0; i < 4; i++) header[14 + i] = (byte) (SERIAL >>> (8 * i));
            for (int i = 0; i < 4; i++) header[18 + i] = (byte) (seq >>> (8 * i));
            seq++;
            header[26] = (byte) segs;
            int rem = chunk;
            for (int i = 0; i < segs; i++) { int sgm = Math.min(255, rem); header[27 + i] = (byte) sgm; rem -= sgm; }
            byte[] body = new byte[chunk];
            System.arraycopy(pkt, offset, body, 0, chunk);
            int crc = 0;
            for (byte b : header) crc = (crc << 8) ^ CRC[((crc >>> 24) ^ (b & 0xff)) & 0xff];
            for (byte b : body) crc = (crc << 8) ^ CRC[((crc >>> 24) ^ (b & 0xff)) & 0xff];
            for (int i = 0; i < 4; i++) header[22 + i] = (byte) (crc >>> (8 * i));
            out.write(header);
            out.write(body);
            offset += chunk;
            first = false;
            if (!continues) break;
        }
    }

    // ------------------------------------------------------------------ tags

    private static byte[] buildTags(Meta m) throws IOException {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.write(new byte[]{'O', 'p', 'u', 's', 'T', 'a', 'g', 's'});
        byte[] vendor = "Gogrut".getBytes("UTF-8");
        le32(o, vendor.length); o.write(vendor);
        java.util.List<byte[]> c = new java.util.ArrayList<byte[]>();
        if (m != null) {
            if (Meta.has(m.title)) c.add(("TITLE=" + m.title).getBytes("UTF-8"));
            if (Meta.has(m.artist)) c.add(("ARTIST=" + m.artist).getBytes("UTF-8"));
            if (Meta.has(m.album)) c.add(("ALBUM=" + m.album).getBytes("UTF-8"));
            if (m.coverJpeg != null && m.coverJpeg.length > 0) {
                c.add(("METADATA_BLOCK_PICTURE=" + base64(flacPicture(m.coverJpeg))).getBytes("US-ASCII"));
            }
        }
        le32(o, c.size());
        for (byte[] b : c) { le32(o, b.length); o.write(b); }
        return o.toByteArray();
    }

    /** FLAC PICTURE block (type 3 = front cover), as required by METADATA_BLOCK_PICTURE. */
    private static byte[] flacPicture(byte[] jpeg) throws IOException {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        be32(o, 3);
        byte[] mime = "image/jpeg".getBytes("US-ASCII");
        be32(o, mime.length); o.write(mime);
        be32(o, 0);                                            // description
        int[] wh = jpegSize(jpeg);
        be32(o, wh[0]); be32(o, wh[1]); be32(o, 24); be32(o, 0);
        be32(o, jpeg.length); o.write(jpeg);
        return o.toByteArray();
    }

    static int[] jpegSize(byte[] j) {
        int i = 2;
        while (i + 9 < j.length) {
            if ((j[i] & 0xff) != 0xFF) { i++; continue; }
            int m = j[i + 1] & 0xff;
            if (m >= 0xC0 && m <= 0xCF && m != 0xC4 && m != 0xC8 && m != 0xCC) {
                return new int[]{((j[i + 7] & 0xff) << 8) | (j[i + 8] & 0xff), ((j[i + 5] & 0xff) << 8) | (j[i + 6] & 0xff)};
            }
            i += 2 + (((j[i + 2] & 0xff) << 8) | (j[i + 3] & 0xff));
        }
        return new int[]{0, 0};
    }

    private static void le32(ByteArrayOutputStream o, int v) { o.write(v); o.write(v >>> 8); o.write(v >>> 16); o.write(v >>> 24); }
    private static void be32(ByteArrayOutputStream o, int v) { o.write(v >>> 24); o.write(v >>> 16); o.write(v >>> 8); o.write(v); }

    private static final char[] B64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray();
    static String base64(byte[] d) {
        StringBuilder sb = new StringBuilder((d.length + 2) / 3 * 4);
        for (int i = 0; i < d.length; i += 3) {
            int b0 = d[i] & 0xff, b1 = i + 1 < d.length ? d[i + 1] & 0xff : 0, b2 = i + 2 < d.length ? d[i + 2] & 0xff : 0;
            sb.append(B64[b0 >> 2]).append(B64[((b0 & 3) << 4) | (b1 >> 4)]);
            sb.append(i + 1 < d.length ? B64[((b1 & 15) << 2) | (b2 >> 6)] : '=');
            sb.append(i + 2 < d.length ? B64[b2 & 63] : '=');
        }
        return sb.toString();
    }
}
