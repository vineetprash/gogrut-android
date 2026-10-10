package com.grogu.yt.core;

import java.io.File;

/** A durable, locally playable download. UI code never needs to parse filenames. */
public final class LibraryTrack {
    public final File file;
    public final String title, artist, mime;
    public final long addedAt;

    public LibraryTrack(File f, String t, String a, String m, long added) {
        file = f; title = t; artist = a; mime = m; addedAt = added;
    }

    public String subtitle() { return artist == null || artist.length() == 0 ? "LOCAL AUDIO" : artist; }
}
