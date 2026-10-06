package com.grogu.yt.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/** Thin wrapper over HttpURLConnection (backed by OkHttp inside Android) – no extra dependency. */
public final class Http {
    private Http() {}

    public static final String DEFAULT_UA = "Mozilla/5.0 (Windows NT 10.0; rv:140.0) Gecko/20100101 Firefox/140.0";
    public static final int CONNECT_MS = 15_000, READ_MS = 30_000;

    public static HttpURLConnection open(String method, String url, Map<String, List<String>> headers, byte[] body) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(CONNECT_MS);
        c.setReadTimeout(READ_MS);
        c.setInstanceFollowRedirects(true);
        c.setRequestMethod(method);
        boolean hasUa = false;
        if (headers != null) {
            for (Map.Entry<String, List<String>> e : headers.entrySet()) {
                if (e.getKey() == null || e.getValue() == null) continue;
                if (e.getKey().equalsIgnoreCase("user-agent")) hasUa = true;
                boolean first = true;
                for (String v : e.getValue()) {
                    if (first) c.setRequestProperty(e.getKey(), v); else c.addRequestProperty(e.getKey(), v);
                    first = false;
                }
            }
        }
        if (!hasUa) c.setRequestProperty("User-Agent", DEFAULT_UA);
        if (body != null || "POST".equals(method)) {
            byte[] b = body == null ? new byte[0] : body;
            c.setDoOutput(true);
            c.setFixedLengthStreamingMode(b.length);
            java.io.OutputStream os = c.getOutputStream();
            try { os.write(b); } finally { os.close(); }
        }
        return c;
    }

    /** Body stream, transparently un-gzipping when the server gzipped and the platform didn't. */
    public static InputStream bodyStream(HttpURLConnection c) throws IOException {
        InputStream in = c.getResponseCode() >= 400 ? c.getErrorStream() : c.getInputStream();
        if (in == null) return new java.io.ByteArrayInputStream(new byte[0]);
        String enc = c.getHeaderField("Content-Encoding");
        if (enc != null && enc.equalsIgnoreCase("gzip")) return new GZIPInputStream(in);
        return in;
    }

    public static byte[] readAll(InputStream in, long max) throws IOException {
        try {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[16 * 1024];
            int r;
            while ((r = in.read(buf)) > 0) {
                bo.write(buf, 0, r);
                if (bo.size() > max) throw new IOException("response too large");
            }
            return bo.toByteArray();
        } finally {
            in.close();
        }
    }

    /** Small GET (thumbnails etc.). Returns null on any failure. */
    public static byte[] getBytesOrNull(String url, long maxBytes) {
        try {
            HttpURLConnection c = open("GET", url, null, null);
            if (c.getResponseCode() != 200) return null;
            return readAll(bodyStream(c), maxBytes);
        } catch (Exception e) {
            return null;
        }
    }
}
