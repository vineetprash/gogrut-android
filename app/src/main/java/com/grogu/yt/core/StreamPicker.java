package com.grogu.yt.core;

import com.grogu.yt.audio.Format;

import java.util.List;

/**
 * Decides which YouTube audio stream to fetch and whether it can be passed straight through.
 * YouTube serves AAC (m4a, ~128 kbps) and Opus (webm, ~50-160 kbps); "original" keeps those untouched.
 */
public final class StreamPicker {
    private StreamPicker() {}

    public interface Cand {
        boolean isM4a();
        boolean isOpus();
        /** Average bitrate in kbit/s (0 if unknown). */
        int kbps();
    }

    public static final class Plan {
        public final Cand source;
        /** true: just remux the stream into the target container, no decode/encode. */
        public final boolean passthrough;
        public final Format format;
        /** target kbps for re-encoding (or {@link Format#BEST_VBR}); ignored on passthrough. */
        public final int bitrate;
        Plan(Cand s, boolean p, Format f, int b) { source = s; passthrough = p; format = f; bitrate = b; }
    }

    public static final class Unsupported extends Exception {
        public Unsupported(String m) { super(m); }
    }

    public static Plan plan(List<? extends Cand> all, Format fmt, int bitrate,
                            boolean canDecodeOpus, boolean canEncodeOpus) throws Unsupported {
        if (all == null || all.isEmpty()) throw new Unsupported("No audio streams found for this video.");

        Cand bestM4a = best(all, true, false), bestOpus = best(all, false, true);

        if (bitrate == Format.ORIGINAL) {
            if (fmt == Format.OPUS) {
                if (bestOpus == null) throw new Unsupported("This video has no Opus audio. Pick M4A, AAC or MP3.");
                return new Plan(bestOpus, true, fmt, bitrate);
            }
            if (fmt == Format.M4A || fmt == Format.AAC) {
                if (bestM4a != null) return new Plan(bestM4a, true, fmt, bitrate);
                if (bestOpus != null && canDecodeOpus) return new Plan(bestOpus, false, fmt, 128);   // rare: no AAC offered
                throw new Unsupported("No AAC audio available for this video.");
            }
        }

        if (fmt == Format.OPUS) {                                    // re-encode to a chosen Opus bitrate
            if (!canEncodeOpus) throw new Unsupported("This phone can't encode Opus at a custom bitrate. Choose ORIGINAL.");
            if (bestOpus == null || !canDecodeOpus) throw new Unsupported("No Opus source available to re-encode.");
            return new Plan(bestOpus, false, fmt, bitrate);
        }

        // MP3 / AAC / M4A at a chosen bitrate: re-encode from the best source this phone can decode
        Cand src = bestM4a;
        if (canDecodeOpus && bestOpus != null && (src == null || bestOpus.kbps() > src.kbps())) src = bestOpus;
        if (src == null) throw new Unsupported("No audio source this phone can decode.");
        return new Plan(src, false, fmt, bitrate);
    }

    private static Cand best(List<? extends Cand> all, boolean m4a, boolean opus) {
        Cand b = null;
        for (Cand c : all) {
            if ((m4a && !c.isM4a()) || (opus && !c.isOpus())) continue;
            if (b == null || c.kbps() > b.kbps()) b = c;
        }
        return b;
    }
}
