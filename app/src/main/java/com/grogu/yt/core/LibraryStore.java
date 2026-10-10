package com.grogu.yt.core;

import android.content.Context;

import java.io.*;
import java.util.*;

/** File-backed library. The audio file is the source of truth; sidecars keep the UI metadata. */
public final class LibraryStore {
    private LibraryStore() {}

    public static File directory(Context c) {
        File dir = new File(c.getFilesDir(), "music");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public static List<LibraryTrack> list(Context c) {
        File[] files = directory(c).listFiles();
        ArrayList<LibraryTrack> tracks = new ArrayList<LibraryTrack>();
        if (files == null) return tracks;
        for (File f : files) {
            if (!f.isFile() || f.getName().endsWith(".properties")) continue;
            Properties p = readMetadata(f);
            String title = p.getProperty("title", displayName(f));
            long added = f.lastModified();
            try { added = Long.parseLong(p.getProperty("added", String.valueOf(added))); } catch (NumberFormatException ignored) { }
            tracks.add(new LibraryTrack(f, title, p.getProperty("artist", ""), p.getProperty("mime", "audio/*"), added));
        }
        Collections.sort(tracks, new Comparator<LibraryTrack>() {
            public int compare(LibraryTrack a, LibraryTrack b) { return Long.compare(b.addedAt, a.addedAt); }
        });
        return tracks;
    }

    static File uniqueFile(File dir, String name) {
        File out = new File(dir, name);
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        for (int i = 2; out.exists(); i++) out = new File(dir, base + "_" + i + ext);
        return out;
    }

    static void writeMetadata(File audio, String title, String artist, String mime) throws IOException {
        Properties p = new Properties();
        p.setProperty("title", title == null ? displayName(audio) : title);
        p.setProperty("artist", artist == null ? "" : artist);
        p.setProperty("mime", mime == null ? "audio/*" : mime);
        p.setProperty("added", String.valueOf(System.currentTimeMillis()));
        FileOutputStream out = new FileOutputStream(sidecar(audio));
        try { p.store(out, "Gogrut library metadata"); } finally { out.close(); }
    }

    public static void remove(LibraryTrack track) {
        if (track == null) return;
        track.file.delete();
        sidecar(track.file).delete();
    }

    private static Properties readMetadata(File audio) {
        Properties p = new Properties();
        File f = sidecar(audio);
        if (!f.exists()) return p;
        try { FileInputStream in = new FileInputStream(f); try { p.load(in); } finally { in.close(); } }
        catch (IOException ignored) { }
        return p;
    }

    private static File sidecar(File audio) { return new File(audio.getPath() + ".properties"); }
    private static String displayName(File f) {
        String n = f.getName(); int dot = n.lastIndexOf('.'); return dot > 0 ? n.substring(0, dot).replace('_', ' ') : n;
    }
}
