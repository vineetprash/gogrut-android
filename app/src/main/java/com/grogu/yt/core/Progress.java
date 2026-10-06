package com.grogu.yt.core;

public interface Progress {
    /** @param total bytes expected, or -1 if unknown */
    void onProgress(long done, long total);
}
