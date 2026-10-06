package com.grogu.yt.core;

public interface Cancel {
    boolean isCancelled();
    Cancel NEVER = new Cancel() { public boolean isCancelled() { return false; } };
}
