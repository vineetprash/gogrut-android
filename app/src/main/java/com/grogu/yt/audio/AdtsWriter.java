package com.grogu.yt.audio;

import java.io.IOException;
import java.io.OutputStream;

/** Raw `.aac`: wraps each AAC frame in a 7-byte ADTS header built from the AudioSpecificConfig. */
public final class AdtsWriter {
    private static final int[] RATES = {96000, 88200, 64000, 48000, 44100, 32000, 24000, 22050, 16000, 12000, 11025, 8000, 7350};
    private final OutputStream out;
    private final int profile, freqIdx, chanCfg;

    /** @param asc AudioSpecificConfig (csd-0). Falls back to AAC-LC with the given rate/channels if null. */
    public AdtsWriter(OutputStream out, byte[] asc, int sampleRate, int channels) {
        this.out = out;
        int objType = 2, fi = indexOf(sampleRate), cc = channels;
        if (asc != null && asc.length >= 2) {
            objType = (asc[0] & 0xff) >> 3;
            fi = ((asc[0] & 0x07) << 1) | ((asc[1] & 0xff) >> 7);
            cc = (asc[1] >> 3) & 0x0f;
            if (objType == 31 || fi == 15 || objType > 4) { objType = 2; fi = indexOf(sampleRate); cc = channels; } // exotic -> LC
        }
        this.profile = objType - 1;
        this.freqIdx = fi < 0 ? 4 : fi;
        this.chanCfg = cc;
    }

    private static int indexOf(int rate) {
        for (int i = 0; i < RATES.length; i++) if (RATES[i] == rate) return i;
        return 4;
    }

    public void write(byte[] data, int off, int len) throws IOException {
        int fl = len + 7;
        byte[] h = new byte[7];
        h[0] = (byte) 0xFF;
        h[1] = (byte) 0xF1;                                   // MPEG-4, layer 0, no CRC
        h[2] = (byte) ((profile << 6) | (freqIdx << 2) | ((chanCfg >> 2) & 1));
        h[3] = (byte) (((chanCfg & 3) << 6) | ((fl >> 11) & 3));
        h[4] = (byte) ((fl >> 3) & 0xff);
        h[5] = (byte) (((fl & 7) << 5) | 0x1F);
        h[6] = (byte) 0xFC;
        out.write(h);
        out.write(data, off, len);
    }
}
