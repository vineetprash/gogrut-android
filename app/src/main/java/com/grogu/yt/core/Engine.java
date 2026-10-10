package com.grogu.yt.core;

import android.content.Context;

import com.grogu.yt.audio.Codecs;
import com.grogu.yt.audio.Format;
import com.grogu.yt.audio.Meta;
import com.grogu.yt.audio.Transcoder;

import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.AudioTrackType;
import org.schabi.newpipe.extractor.stream.DeliveryMethod;
import org.schabi.newpipe.extractor.stream.StreamInfo;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Port of server.py's POST /api/convert, with NewPipeExtractor in place of yt-dlp and Android's own
 * codecs in place of ffmpeg.
 *
 * Everything that touches the extractor lives in THIS file, so when a new extractor release changes
 * its API (rare - they fix YouTube breakage without API changes) this is the only file to adapt.
 */
public final class Engine {
    private Engine() {}

    public interface Listener { void status(String text, int percent); }

    public static final class Result {
        public final File file;
        public final String name;
        public final String mime;
        public final String title;
        public final String artist;
        Result(File f, String n, String m, String t, String a) {
            file = f; name = n; mime = m; title = t; artist = a;
        }
    }

    /** User-facing failure with a ready-to-show message. */
    public static final class Failure extends Exception {
        public Failure(String m, Throwable cause) { super(m, cause); }
    }

    private static boolean inited;

    private static synchronized void init() {
        if (inited) return;
        NewPipe.init(new HttpDownloader());
        inited = true;
    }

    private static final class Cand implements StreamPicker.Cand {
        final AudioStream s;
        Cand(AudioStream s) { this.s = s; }
        public boolean isM4a() { return s.getFormat() == org.schabi.newpipe.extractor.MediaFormat.M4A; }
        public boolean isOpus() {
            org.schabi.newpipe.extractor.MediaFormat f = s.getFormat();
            return f == org.schabi.newpipe.extractor.MediaFormat.WEBMA_OPUS || f == org.schabi.newpipe.extractor.MediaFormat.OPUS;
        }
        public int kbps() { return Math.max(0, s.getAverageBitrate()); }
    }

    public static Result convert(Context ctx, String rawUrl, Format fmt, int bitrate, Listener ui, Cancel cancel) throws Failure {
        File tmp = new File(ctx.getCacheDir(), "grogu-" + Long.toHexString(System.nanoTime()));
        try {
            String url = LinkParser.canonical(rawUrl);                     // same allowlist as server.py

            ui.status("Fetching video info…", 2);
            init();
            StreamInfo info = StreamInfo.getInfo(url);
            check(cancel);

            List<Cand> cands = candidates(info);
            StreamPicker.Plan plan = StreamPicker.plan(cands, fmt, bitrate, Codecs.canDecodeOpus(), Codecs.canEncodeOpus());
            AudioStream src = ((Cand) plan.source).s;

            tmp.mkdirs();
            File raw = new File(tmp, "source." + (plan.source.isOpus() ? "webm" : "m4a"));
            final String streamUrl = src.getContent();
            boolean vision = streamUrl.contains("c=VISIONOS");
            String ua = vision ? visionUserAgent() : Http.DEFAULT_UA;

            ui.status("Downloading…", 5);
            StreamFetcher.download(streamUrl, ua, vision, raw, new Progress() {
                public void onProgress(long done, long total) {
                    ui.status("Downloading…", total > 0 ? 5 + (int) (done * 55 / total) : 30);
                }
            }, cancel);
            check(cancel);

            Meta meta = new Meta(info.getName(), artist(info), null, cover(info));

            String base = Names.restrict(info.getName(), 150);
            File outDir = new File(ctx.getFilesDir(), "music");
            outDir.mkdirs();
            File out = LibraryStore.uniqueFile(outDir, base + "." + fmt.ext);

            boolean passthrough = plan.passthrough;
            ui.status(passthrough ? "Packaging…" : "Converting…", 62);
            Transcoder.run(raw, fmt, passthrough, passthrough ? src.getAverageBitrate() : plan.bitrate, meta, out,
                    new Progress() {
                        public void onProgress(long done, long total) {
                            ui.status("Converting…", total > 0 ? 62 + (int) (done * 37 / total) : 80);
                        }
                    }, cancel);
            LibraryStore.writeMetadata(out, info.getName(), artist(info), fmt.mime);
            return new Result(out, out.getName(), fmt.mime, info.getName(), artist(info));
        } catch (LinkParser.Bad e) {
            throw new Failure(e.getMessage(), e);
        } catch (StreamPicker.Unsupported e) {
            throw new Failure(e.getMessage(), e);
        } catch (java.io.InterruptedIOException e) {
            throw new Failure("Cancelled.", e);
        } catch (Throwable t) {
            throw new Failure(Errors.friendly(t), t);
        } finally {
            ZipDelete.rm(tmp);
        }
    }

    private static void check(Cancel c) throws java.io.InterruptedIOException {
        if (c.isCancelled()) throw new java.io.InterruptedIOException("cancelled");
    }

    /** Progressive https audio streams of the *original* language track. */
    private static List<Cand> candidates(StreamInfo info) {
        List<Cand> all = new ArrayList<Cand>();
        List<AudioStream> streams = info.getAudioStreams();
        if (streams == null) return all;
        boolean hasOriginal = false;
        for (AudioStream s : streams) {
            AudioTrackType t = s.getAudioTrackType();
            if (t == null || t == AudioTrackType.ORIGINAL) hasOriginal = true;
        }
        for (AudioStream s : streams) {
            if (s.getDeliveryMethod() != DeliveryMethod.PROGRESSIVE_HTTP || !s.isUrl()) continue;
            AudioTrackType t = s.getAudioTrackType();
            if (hasOriginal && t != null && t != AudioTrackType.ORIGINAL) continue;
            all.add(new Cand(s));
        }
        return all;
    }

    private static String artist(StreamInfo info) {
        String a = info.getUploaderName();
        if (a == null) return null;
        return a.replaceAll("\\s*-\\s*Topic$", "").trim();
    }

    private static byte[] cover(StreamInfo info) {
        List<Image> imgs = info.getThumbnails();
        if (imgs == null || imgs.isEmpty()) return null;
        Image best = null;
        for (Image i : imgs) {
            if (best == null || i.getHeight() > best.getHeight()) best = i;
        }
        return Cover.fetchJpeg(best.getUrl());
    }

    /** The extractor tells us which User-Agent the visionOS client uses; fall back to a known value. */
    private static String visionUserAgent() {
        try {
            Class<?> c = Class.forName("org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper");
            Method m = c.getMethod("getVisionOsUserAgent", Class.forName("org.schabi.newpipe.extractor.localization.Localization"));
            return (String) m.invoke(null, new Object[]{null});
        } catch (Throwable t) {
            return "com.google.visionos.youtube/1.04(RealityDevice17,1; U; CPU visionOS 26_6_0 like Mac OS X; GB)";
        }
    }

    /** tiny helper so Engine has no dependency on the UI package */
    static final class ZipDelete {
        static void rm(File f) {
            if (f == null || !f.exists()) return;
            if (f.isDirectory()) { File[] k = f.listFiles(); if (k != null) for (File c : k) rm(c); }
            f.delete();
        }
    }
}
