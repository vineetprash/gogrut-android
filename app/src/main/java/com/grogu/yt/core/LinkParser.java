package com.grogu.yt.core;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Same allowlist as server.py, plus video-id extraction (replaces yt-dlp's `--no-playlist`). */
public final class LinkParser {
    private LinkParser() {}

    public static final Set<String> HOSTS = new HashSet<String>(Arrays.asList(
            "youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com", "youtu.be"));

    public static final String BAD_HOST = "Paste a youtube.com, music.youtube.com or youtu.be link.";
    public static final String NO_VIDEO = "Couldn't find a video in that link.";

    private static final Pattern NETLOC = Pattern.compile("^[A-Za-z][A-Za-z0-9+.\\-]*://([^/?#]*)([^?#]*)(?:\\?([^#]*))?");
    private static final Pattern ID = Pattern.compile("^[A-Za-z0-9_-]{11}$");

    public static final class Bad extends Exception {
        public Bad(String m) { super(m); }
    }

    /** urlparse(url).hostname equivalent. */
    public static String hostOf(String url) {
        Matcher m = NETLOC.matcher(url);
        if (!m.find()) return "";
        String netloc = m.group(1);
        int at = netloc.lastIndexOf('@');
        if (at >= 0) netloc = netloc.substring(at + 1);
        if (netloc.startsWith("[")) {
            int end = netloc.indexOf(']');
            return end > 0 ? netloc.substring(1, end).toLowerCase(Locale.ROOT) : "";
        }
        int colon = netloc.indexOf(':');
        if (colon >= 0) netloc = netloc.substring(0, colon);
        return netloc.toLowerCase(Locale.ROOT);
    }

    /** @return canonical https://www.youtube.com/watch?v=ID */
    public static String canonical(String raw) throws Bad {
        String url = raw == null ? "" : raw.trim();
        String host = hostOf(url);
        if (!HOSTS.contains(host)) throw new Bad(BAD_HOST);
        Matcher m = NETLOC.matcher(url);
        m.find();
        String path = m.group(2) == null ? "" : m.group(2);
        String query = m.group(3) == null ? "" : m.group(3);
        String id = null;
        if (host.equals("youtu.be")) {
            id = firstSegment(path);
        } else {
            id = queryParam(query, "v");
            if (id == null) {
                String[] seg = path.split("/");
                for (int i = 0; i + 1 < seg.length; i++) {
                    String s = seg[i];
                    if (s.equals("shorts") || s.equals("embed") || s.equals("live") || s.equals("v")) { id = seg[i + 1]; break; }
                }
            }
        }
        if (id == null || !ID.matcher(id).matches()) throw new Bad(NO_VIDEO);
        return "https://www.youtube.com/watch?v=" + id;
    }

    private static String firstSegment(String path) {
        for (String s : path.split("/")) if (!s.isEmpty()) return s;
        return null;
    }

    private static String queryParam(String q, String key) {
        for (String kv : q.split("&")) {
            int eq = kv.indexOf('=');
            if (eq > 0 && kv.substring(0, eq).equals(key)) return kv.substring(eq + 1);
        }
        return null;
    }
}
