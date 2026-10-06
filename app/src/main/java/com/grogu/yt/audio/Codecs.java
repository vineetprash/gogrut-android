package com.grogu.yt.audio;

import android.media.MediaCodecInfo;
import android.media.MediaCodecList;

/** Which codecs this phone's hardware/firmware offers. */
public final class Codecs {
    private Codecs() {}

    public static boolean has(String mime, boolean encoder) {
        try {
            MediaCodecInfo[] all = new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos();
            for (MediaCodecInfo i : all) {
                if (i.isEncoder() != encoder) continue;
                for (String t : i.getSupportedTypes()) if (t.equalsIgnoreCase(mime)) return true;
            }
        } catch (Throwable ignored) { }
        return false;
    }

    public static boolean canDecodeOpus() { return has("audio/opus", false); }
    public static boolean canEncodeOpus() { return has("audio/opus", true); }
}
