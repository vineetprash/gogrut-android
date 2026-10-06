package com.grogu.yt.core;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Downloads a googlevideo.com stream the way YouTube clients do: in ranged chunks (`&range=a-b`),
 * which avoids the slow-down YouTube applies to one giant request. Retries each chunk, and switches
 * GET <-> POST once if the first request is refused (the extractor says visionOS URLs want POST).
 */
public final class StreamFetcher {
    private StreamFetcher() {}

    public static final int CHUNK = 4 * 1024 * 1024;
    private static final Pattern CLEN = Pattern.compile("[?&]clen=(\\d+)");
    private static final Pattern CRANGE = Pattern.compile("bytes\\s+\\d+-\\d+/(\\d+)");

    public static final class HttpStatus extends IOException {
        public final int code;
        HttpStatus(int code, String url) { super("HTTP " + code); this.code = code; }
    }

    public static long contentLength(String url) {
        Matcher m = CLEN.matcher(url);
        return m.find() ? Long.parseLong(m.group(1)) : -1;
    }

    /** @return bytes written */
    public static long download(String url, String userAgent, boolean preferPost, File dest, Progress progress, Cancel cancel) throws IOException {
        long total = contentLength(url);
        long pos = 0;
        boolean post = preferPost, switched = false;
        OutputStream out = new java.io.BufferedOutputStream(new FileOutputStream(dest), 256 * 1024);
        try {
            while (total < 0 || pos < total) {
                if (cancel.isCancelled()) throw new InterruptedIOException("cancelled");
                long end = total >= 0 ? Math.min(pos + CHUNK - 1, total - 1) : pos + CHUNK - 1;
                byte[] chunk = null;
                IOException last = null;
                boolean whole = false;
                for (int attempt = 0; attempt < 4 && chunk == null; attempt++) {
                    if (cancel.isCancelled()) throw new InterruptedIOException("cancelled");
                    try {
                        Chunk c = fetch(url, pos, end, post, userAgent, cancel);
                        chunk = c.data;
                        whole = c.whole;
                        if (c.total >= 0 && total < 0) total = c.total;
                    } catch (HttpStatus e) {
                        last = e;
                        if (!switched && pos == 0 && (e.code == 403 || e.code == 405 || e.code == 400 || e.code == 404)) {
                            post = !post; switched = true; attempt--;       // try the other method once, free of charge
                            continue;
                        }
                        if (e.code == 403 || e.code == 404 || e.code == 410) throw e;   // expired / blocked: retrying won't help
                        sleep(500L << attempt);
                    } catch (InterruptedIOException e) {
                        throw e;
                    } catch (IOException e) {
                        last = e;
                        sleep(500L << attempt);
                    }
                }
                if (chunk == null) throw last != null ? last : new IOException("download failed");
                out.write(chunk);
                pos += chunk.length;
                if (progress != null) progress.onProgress(pos, total);
                if (whole) break;                                         // server ignored `range` and sent everything
                if (chunk.length == 0) break;
                if (total < 0 && chunk.length < CHUNK) break;             // short chunk = end of file
            }
        } finally {
            out.close();
        }
        if (total >= 0 && pos < total) throw new IOException("stream ended early (" + pos + "/" + total + ")");
        return pos;
    }

    private static final class Chunk { byte[] data; boolean whole; long total = -1; }

    private static Chunk fetch(String url, long start, long end, boolean post, String ua, Cancel cancel) throws IOException {
        String u = url + (url.indexOf('?') >= 0 ? "&" : "?") + "range=" + start + "-" + end;
        Map<String, List<String>> h = Collections.singletonMap("User-Agent", Collections.singletonList(ua));
        HttpURLConnection c = Http.open(post ? "POST" : "GET", u, h, post ? new byte[0] : null);
        try {
            int code = c.getResponseCode();
            if (code != 200 && code != 206) throw new HttpStatus(code, url);
            long want = end - start + 1;
            long len = c.getContentLengthLong();
            Chunk r = new Chunk();
            String cr = c.getHeaderField("Content-Range");
            if (cr != null) { Matcher m = CRANGE.matcher(cr); if (m.find()) r.total = Long.parseLong(m.group(1)); }
            r.whole = code == 200 && start == 0 && len > want;             // `range` ignored
            long cap = r.whole ? Math.max(len, want) : want;
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream((int) Math.min(cap, CHUNK));
            InputStream in = Http.bodyStream(c);
            byte[] buf = new byte[64 * 1024];
            int n;
            try {
                while ((n = in.read(buf)) > 0) {
                    if (cancel.isCancelled()) throw new InterruptedIOException("cancelled");
                    bo.write(buf, 0, n);
                    if (!r.whole && bo.size() > want) break;
                }
            } finally { in.close(); }
            r.data = bo.toByteArray();
            if (r.whole) r.total = r.data.length;
            return r;
        } finally {
            c.disconnect();
        }
    }

    private static void sleep(long ms) throws InterruptedIOException {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new InterruptedIOException("cancelled"); }
    }

}
