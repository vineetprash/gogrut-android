package com.grogu.yt.audio;

/** Output formats offered in the UI, with the bitrate choices that make sense for each. */
public enum Format {
    //        label   ext     mime          bitrate choices (kbps); ORIGINAL / BEST_VBR are special values
    MP3 ("MP3",  "mp3",  "audio/mpeg", new int[]{Format.BEST_VBR, 128, 192, 256, 320}),
    M4A ("M4A",  "m4a",  "audio/mp4",  new int[]{Format.ORIGINAL, 128, 192, 256, 320}),
    AAC ("AAC",  "aac",  "audio/aac",  new int[]{Format.ORIGINAL, 128, 192, 256, 320}),
    OPUS("OPUS", "opus", "audio/ogg",  new int[]{Format.ORIGINAL, 64, 96, 128, 160});

    /** Keep YouTube's own stream untouched (remux only): fastest, no quality loss. */
    public static final int ORIGINAL = 0;
    /** LAME VBR V0 – what the Flask app's `--audio-quality 0` produced. */
    public static final int BEST_VBR = -1;

    public final String label, ext, mime;
    public final int[] bitrates;

    Format(String label, String ext, String mime, int[] bitrates) {
        this.label = label; this.ext = ext; this.mime = mime; this.bitrates = bitrates;
    }

    public int defaultBitrate() { return bitrates[0]; }

    public boolean supports(int bitrate) {
        for (int b : bitrates) if (b == bitrate) return true;
        return false;
    }

    public static String bitrateLabel(int bitrate) {
        if (bitrate == ORIGINAL) return "ORIG";
        if (bitrate == BEST_VBR) return "VBR";
        return String.valueOf(bitrate);
    }
}
