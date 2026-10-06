package com.grogu.yt.core;

import java.nio.charset.Charset;

/** yt-dlp `--restrict-filenames` + `%(title).150B` equivalent. */
public final class Names {
    private Names() {}

    /** ASCII only, spaces -> '_', junk -> '_', no leading/trailing dots/underscores, <= maxBytes. */
    public static String restrict(String title, int maxBytes) {
        if (title == null) title = "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < title.length(); i++) {
            char c = title.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '-' || c == '.' || c == '_') sb.append(c);
            else if (c == ' ' || c == '\t') sb.append('_');
            else if (c == '&') sb.append("and");
            else sb.append('_');
        }
        String s = sb.toString().replaceAll("_{2,}", "_").replaceAll("^[._-]+|[._]+$", "");
        if (s.length() > maxBytes) s = s.substring(0, maxBytes).replaceAll("[._-]+$", "");
        return s.isEmpty() ? "audio" : s;
    }
}
