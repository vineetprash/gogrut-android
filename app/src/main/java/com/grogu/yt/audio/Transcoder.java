package com.grogu.yt.audio;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;

import com.grogu.yt.core.Cancel;
import com.grogu.yt.core.Progress;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

/**
 * All audio work, using only Android's own MediaExtractor / MediaCodec plus the small writers in this package:
 *
 *   passthrough : MediaExtractor -> (m4a | adts | ogg-opus) writer          (no quality loss, near-instant)
 *   re-encode   : MediaExtractor -> MediaCodec decoder -> PCM -> { LAME mp3 | MediaCodec AAC | MediaCodec Opus }
 */
public final class Transcoder {
    private Transcoder() {}

    public static void run(File src, Format fmt, boolean passthrough, int bitrate, Meta meta, File out,
                           Progress progress, Cancel cancel) throws IOException {
        MediaExtractor ex = new MediaExtractor();
        try {
            ex.setDataSource(src.getAbsolutePath());
            int track = -1;
            MediaFormat tf = null;
            for (int i = 0; i < ex.getTrackCount(); i++) {
                MediaFormat f = ex.getTrackFormat(i);
                String m = f.getString(MediaFormat.KEY_MIME);
                if (m != null && m.startsWith("audio/")) { track = i; tf = f; break; }
            }
            if (track < 0) throw new IOException("No audio track in the downloaded stream.");
            ex.selectTrack(track);
            long durUs = tf.containsKey(MediaFormat.KEY_DURATION) ? tf.getLong(MediaFormat.KEY_DURATION) : 0;
            int rate = tf.containsKey(MediaFormat.KEY_SAMPLE_RATE) ? tf.getInteger(MediaFormat.KEY_SAMPLE_RATE) : 44100;
            int ch = tf.containsKey(MediaFormat.KEY_CHANNEL_COUNT) ? tf.getInteger(MediaFormat.KEY_CHANNEL_COUNT) : 2;
            String mime = tf.getString(MediaFormat.KEY_MIME);

            if (passthrough) remux(ex, tf, fmt, rate, ch, bitrate, durUs, meta, out, progress, cancel);
            else reencode(ex, tf, mime, fmt, rate, ch, bitrate, durUs, meta, out, progress, cancel);
        } finally {
            ex.release();
        }
    }

    // ------------------------------------------------------------------ passthrough

    private static void remux(MediaExtractor ex, MediaFormat tf, Format fmt, int rate, int ch, int kbps, long durUs,
                              Meta meta, File out, Progress progress, Cancel cancel) throws IOException {
        byte[] csd0 = csd(tf, "csd-0");
        PacketSink sink = open(fmt, rate, ch, csd0, kbps > 0 ? kbps : 0, meta, out);
        boolean ok = false;
        try {
            ByteBuffer buf = ByteBuffer.allocate(512 * 1024);
            byte[] arr = new byte[512 * 1024];
            while (true) {
                if (cancel.isCancelled()) throw new InterruptedIOException("cancelled");
                buf.clear();
                int n = ex.readSampleData(buf, 0);
                if (n < 0) break;
                long t = ex.getSampleTime();
                buf.position(0);
                buf.get(arr, 0, n);
                sink.packet(arr, 0, n, t);
                ex.advance();
                if (progress != null && durUs > 0) progress.onProgress(Math.min(t, durUs), durUs);
            }
            sink.close();
            ok = true;
        } finally {
            if (!ok) { sink.abort(); out.delete(); }
        }
    }

    // ------------------------------------------------------------------ re-encode

    private static void reencode(MediaExtractor ex, MediaFormat tf, String mime, Format fmt, int rate, int ch, int kbps,
                                 long durUs, Meta meta, File out, Progress progress, Cancel cancel) throws IOException {
        MediaCodec dec = null;
        PcmSink sink = null;
        boolean ok = false;
        try {
            dec = MediaCodec.createDecoderByType(mime);
            dec.configure(tf, null, null, 0);
            dec.start();

            MediaCodec.BufferInfo bi = new MediaCodec.BufferInfo();
            boolean inDone = false, outDone = false;
            byte[] pcm = new byte[64 * 1024];
            long guard = 0;

            while (!outDone) {
                if (cancel.isCancelled()) throw new InterruptedIOException("cancelled");
                if (!inDone) {
                    int ii = dec.dequeueInputBuffer(10_000);
                    if (ii >= 0) {
                        ByteBuffer ib = dec.getInputBuffer(ii);
                        int n = ex.readSampleData(ib, 0);
                        if (n < 0) {
                            dec.queueInputBuffer(ii, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inDone = true;
                        } else {
                            long t = ex.getSampleTime();
                            dec.queueInputBuffer(ii, 0, n, t, 0);
                            ex.advance();
                            if (progress != null && durUs > 0) progress.onProgress(Math.min(t, durUs), durUs);
                        }
                    }
                }
                int oi = dec.dequeueOutputBuffer(bi, 10_000);
                if (oi >= 0) {
                    if (bi.size > 0) {
                        if (sink == null) {
                            if (ch > 2 || ch < 1) throw new IOException("Unsupported channel layout (" + ch + ").");
                            sink = openPcm(fmt, rate, ch, kbps, meta, out);
                        }
                        ByteBuffer ob = dec.getOutputBuffer(oi);
                        ob.position(bi.offset);
                        ob.limit(bi.offset + bi.size);
                        if (pcm.length < bi.size) pcm = new byte[bi.size];
                        ob.get(pcm, 0, bi.size);
                        sink.write(pcm, 0, bi.size);
                    }
                    dec.releaseOutputBuffer(oi, false);
                    if ((bi.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outDone = true;
                    guard = 0;
                } else if (oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat of = dec.getOutputFormat();
                    if (of.containsKey(MediaFormat.KEY_SAMPLE_RATE)) rate = of.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    if (of.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) ch = of.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                } else if (oi == MediaCodec.INFO_TRY_AGAIN_LATER && inDone && ++guard > 600) {
                    throw new IOException("Decoder stalled.");
                }
            }
            if (sink == null) throw new IOException("Decoder produced no audio.");
            sink.finish();
            ok = true;
        } catch (MediaCodec.CodecException e) {
            throw new IOException("Codec error: " + e.getMessage());
        } finally {
            if (dec != null) { try { dec.stop(); } catch (Throwable ignored) { } try { dec.release(); } catch (Throwable ignored) { } }
            if (!ok) { if (sink != null) sink.abort(); out.delete(); }
        }
    }

    // ------------------------------------------------------------------ sinks

    /** Compressed packets -> container. */
    interface PacketSink {
        void packet(byte[] d, int off, int len, long ptsUs) throws IOException;
        void close() throws IOException;
        void abort();
    }

    /** Raw 16-bit little-endian interleaved PCM -> compressed file. */
    interface PcmSink {
        void write(byte[] pcm, int off, int len) throws IOException;
        void finish() throws IOException;
        void abort();
    }

    private static PacketSink open(Format fmt, final int rate, final int ch, byte[] csd0, final int kbps, Meta meta, File out) throws IOException {
        switch (fmt) {
            case M4A: {
                final M4aWriter w = new M4aWriter(out, rate, ch, csd0, kbps * 1000, meta);
                return new PacketSink() {
                    public void packet(byte[] d, int o, int l, long t) throws IOException { w.addSample(d, o, l, t); }
                    public void close() throws IOException { w.finish(); }
                    public void abort() { }
                };
            }
            case AAC: {
                final OutputStream os = new BufferedOutputStream(new FileOutputStream(out), 64 * 1024);
                final AdtsWriter w = new AdtsWriter(os, csd0, rate, ch);
                return new PacketSink() {
                    public void packet(byte[] d, int o, int l, long t) throws IOException { w.write(d, o, l); }
                    public void close() throws IOException { os.close(); }
                    public void abort() { try { os.close(); } catch (IOException ignored) { } }
                };
            }
            case OPUS: {
                final OutputStream os = new BufferedOutputStream(new FileOutputStream(out), 64 * 1024);
                final OggOpusWriter w = new OggOpusWriter(os, csd0, ch, rate, meta);
                return new PacketSink() {
                    public void packet(byte[] d, int o, int l, long t) throws IOException { w.writePacket(d, o, l); }
                    public void close() throws IOException { w.finish(); os.close(); }
                    public void abort() { try { os.close(); } catch (IOException ignored) { } }
                };
            }
            default:
                throw new IOException("Format " + fmt + " can't be remuxed.");
        }
    }

    private static PcmSink openPcm(Format fmt, int rate, int ch, int kbps, Meta meta, File out) throws IOException {
        if (fmt == Format.MP3) {
            final Mp3Encoder e = new Mp3Encoder(out, rate, ch, kbps, meta);
            return new PcmSink() {
                public void write(byte[] p, int o, int l) throws IOException { e.write(p, o, l); }
                public void finish() throws IOException { e.finish(); }
                public void abort() { try { e.finish(); } catch (Throwable ignored) { } }
            };
        }
        return new CodecSink(fmt, rate, ch, kbps, meta, out);
    }

    /** PCM -> MediaCodec encoder (AAC or Opus) -> container. */
    private static final class CodecSink implements PcmSink {
        private final MediaCodec enc;
        private final Format fmt;
        private final int rate, ch, kbps;
        private final Meta meta;
        private final File out;
        private final MediaCodec.BufferInfo bi = new MediaCodec.BufferInfo();
        private PacketSink sink;
        private byte[] csd0;
        private long samplesFed;
        private boolean sawConfig;

        CodecSink(Format fmt, int rate, int ch, int kbps, Meta meta, File out) throws IOException {
            this.fmt = fmt; this.rate = rate; this.ch = ch; this.kbps = kbps; this.meta = meta; this.out = out;
            String mime = fmt == Format.OPUS ? "audio/opus" : "audio/mp4a-latm";
            try {
                enc = MediaCodec.createEncoderByType(mime);
            } catch (Throwable t) {
                throw new IOException("This phone has no " + fmt.label + " encoder.");
            }
            MediaFormat f = MediaFormat.createAudioFormat(mime, rate, ch);
            f.setInteger(MediaFormat.KEY_BIT_RATE, kbps * 1000);
            if (fmt != Format.OPUS) f.setInteger(MediaFormat.KEY_AAC_PROFILE, android.media.MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            f.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 64 * 1024);
            enc.configure(f, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            enc.start();
        }

        public void write(byte[] pcm, int off, int len) throws IOException {
            int frameBytes = 2 * ch;
            len -= len % frameBytes;
            while (len > 0) {
                int ii = enc.dequeueInputBuffer(10_000);
                if (ii < 0) { drain(); continue; }
                ByteBuffer ib = enc.getInputBuffer(ii);
                int n = Math.min(len, ib.capacity() - ib.capacity() % frameBytes);
                ib.clear();
                ib.put(pcm, off, n);
                long pts = samplesFed * 1_000_000L / rate;
                enc.queueInputBuffer(ii, 0, n, pts, 0);
                samplesFed += n / frameBytes;
                off += n; len -= n;
                drain();
            }
        }

        public void finish() throws IOException {
            int ii;
            while ((ii = enc.dequeueInputBuffer(10_000)) < 0) drain();
            enc.queueInputBuffer(ii, 0, 0, samplesFed * 1_000_000L / rate, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            long deadline = System.currentTimeMillis() + 30_000;
            while (!drain()) {
                if (System.currentTimeMillis() > deadline) throw new IOException("Encoder stalled.");
            }
            if (sink != null) sink.close();
            release();
        }

        public void abort() {
            try { if (sink != null) sink.abort(); } catch (Throwable ignored) { }
            release();
        }

        private void release() {
            try { enc.stop(); } catch (Throwable ignored) { }
            try { enc.release(); } catch (Throwable ignored) { }
        }

        /** @return true once the encoder signalled end-of-stream */
        private boolean drain() throws IOException {
            while (true) {
                int oi = enc.dequeueOutputBuffer(bi, 0);
                if (oi == MediaCodec.INFO_TRY_AGAIN_LATER) return false;
                if (oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat of = enc.getOutputFormat();
                    if (csd0 == null) csd0 = csd(of, "csd-0");
                    continue;
                }
                if (oi < 0) continue;
                ByteBuffer ob = enc.getOutputBuffer(oi);
                boolean config = (bi.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0;
                if (config) {
                    if (!sawConfig && bi.size > 0) {
                        sawConfig = true;
                        csd0 = new byte[bi.size];
                        ob.position(bi.offset); ob.limit(bi.offset + bi.size); ob.get(csd0);
                    }
                } else if (bi.size > 0) {
                    if (sink == null) sink = open(fmt, rate, ch, csd0, kbps, meta, out);
                    byte[] d = new byte[bi.size];
                    ob.position(bi.offset); ob.limit(bi.offset + bi.size); ob.get(d);
                    sink.packet(d, 0, d.length, bi.presentationTimeUs);
                }
                enc.releaseOutputBuffer(oi, false);
                if ((bi.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) return true;
            }
        }
    }

    static byte[] csd(MediaFormat f, String key) {
        if (f == null || !f.containsKey(key)) return null;
        ByteBuffer b = f.getByteBuffer(key);
        if (b == null) return null;
        byte[] r = new byte[b.remaining()];
        b.duplicate().get(r);
        return r;
    }
}
