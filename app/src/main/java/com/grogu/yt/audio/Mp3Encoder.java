package com.grogu.yt.audio;

import net.sourceforge.lame.mp3.Lame;
import net.sourceforge.lame.mp3.LameGlobalFlags;
import net.sourceforge.lame.mp3.MPEGMode;
import net.sourceforge.lame.mp3.VbrMode;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;

/**
 * PCM (16-bit little-endian, interleaved) -> MP3 using the pure-Java LAME port.
 * Writes: [ID3v2 tag][Xing/Info frame + audio frames], and patches the Xing frame when finished so
 * players show the right duration for VBR files.
 */
public final class Mp3Encoder {
    private final Lame lame = new Lame();
    private final LameGlobalFlags flags;
    private final int channels;
    private final File file;
    private final OutputStream out;
    private final long id3Len;
    private byte[] mp3 = new byte[0];
    private boolean closed;

    /** @param bitrate kbps, or {@link Format#BEST_VBR} */
    public Mp3Encoder(File file, int sampleRate, int channels, int bitrate, Meta meta) throws IOException {
        this.file = file;
        this.channels = channels == 1 ? 1 : 2;
        this.out = new java.io.BufferedOutputStream(new java.io.FileOutputStream(file), 64 * 1024);
        byte[] id3 = Id3Writer.build(meta);
        out.write(id3);
        id3Len = id3.length;

        flags = lame.getFlags();
        flags.setInNumChannels(this.channels);
        flags.setInSampleRate(sampleRate);
        flags.setMode(this.channels == 1 ? MPEGMode.MONO : MPEGMode.JOINT_STEREO);
        if (bitrate == Format.BEST_VBR) {
            flags.setVBR(VbrMode.vbr_default);
            flags.setVBRQuality(0);                  // V0, same as `-q:a 0`
        } else {
            flags.setBitRate(bitrate);
        }
        flags.setQuality(Lame.QUALITY_MIDDLE);        // speed/quality balance that suits a phone CPU
        lame.getId3().init(flags);
        flags.setWriteId3tagAutomatic(false);         // we write our own ID3v2
        int rc = lame.initParams();
        if (rc < 0) throw new IOException("LAME rejected parameters (" + rc + ")");
    }

    /** Feed interleaved PCM16LE. */
    public void write(byte[] pcm, int off, int len) throws IOException {
        int frames = len / (2 * channels);
        if (frames <= 0) return;
        float[] l = new float[frames], r = new float[frames];
        int p = off;
        for (int i = 0; i < frames; i++) {
            // 16-bit sample moved to the top of a 32-bit int: java-lame scales by 1/65536 internally
            int s = (short) ((pcm[p] & 0xff) | (pcm[p + 1] << 8)); p += 2;
            l[i] = (float) s * 65536f;
            if (channels == 2) { int s2 = (short) ((pcm[p] & 0xff) | (pcm[p + 1] << 8)); p += 2; r[i] = (float) s2 * 65536f; }
            else r[i] = l[i];
        }
        int need = (int) (1.25 * frames + 7200);
        if (mp3.length < need) mp3 = new byte[need];
        int n = lame.encodeBuffer(l, r, frames, mp3);
        if (n < 0) throw new IOException("LAME encode error " + n);
        out.write(mp3, 0, n);
    }

    public void finish() throws IOException {
        if (closed) return;
        closed = true;
        if (mp3.length < 8192) mp3 = new byte[8192];
        int n = lame.encodeFlush(mp3);
        if (n > 0) out.write(mp3, 0, n);
        out.close();
        // Patch the (placeholder) first frame with the Xing/Info tag: gives players correct duration & seeking.
        byte[] tag = new byte[2880];
        int t = lame.getVbr().getLameTagFrame(flags, tag);
        if (t > 0 && t <= tag.length) {
            RandomAccessFile raf = new RandomAccessFile(file, "rw");
            try { raf.seek(id3Len); raf.write(tag, 0, t); } finally { raf.close(); }
        }
        lame.close();
    }
}
