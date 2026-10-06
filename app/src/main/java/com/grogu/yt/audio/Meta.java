package com.grogu.yt.audio;

/** Tags written into the output file (the Flask app used --embed-metadata --embed-thumbnail). */
public final class Meta {
    public final String title, artist, album;
    /** JPEG bytes or null. */
    public final byte[] coverJpeg;

    public Meta(String title, String artist, String album, byte[] coverJpeg) {
        this.title = title; this.artist = artist; this.album = album; this.coverJpeg = coverJpeg;
    }

    public static final Meta NONE = new Meta(null, null, null, null);

    static boolean has(String s) { return s != null && !s.isEmpty(); }
}
