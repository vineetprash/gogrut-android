package com.grogu.yt.core;

import org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException;
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException;
import org.schabi.newpipe.extractor.exceptions.GeographicRestrictionException;
import org.schabi.newpipe.extractor.exceptions.PaidContentException;
import org.schabi.newpipe.extractor.exceptions.PrivateContentException;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;
import org.schabi.newpipe.extractor.exceptions.SignInConfirmNotBotException;
import org.schabi.newpipe.extractor.exceptions.YoutubeMusicPremiumContentException;

import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

/** Turns extractor/network exceptions into the one-line messages shown in the status text. */
public final class Errors {
    private Errors() {}

    public static String friendly(Throwable t) {
        // Walk the cause chain: the extractor often wraps the interesting exception.
        for (Throwable e = t; e != null; e = e.getCause()) {
            if (e instanceof AgeRestrictedContentException) return "This video is age-restricted.";
            if (e instanceof PrivateContentException) return "This video is private.";
            if (e instanceof GeographicRestrictionException) return "This video isn't available in your country.";
            if (e instanceof YoutubeMusicPremiumContentException) return "This is YouTube Music Premium content.";
            if (e instanceof PaidContentException) return "This is paid content.";
            if (e instanceof SignInConfirmNotBotException || e instanceof ReCaptchaException)
                return "YouTube wants to confirm you're not a bot. Try again later or on another network.";
            if (e instanceof InterruptedIOException) return "Cancelled.";
        }
        for (Throwable e = t; e != null; e = e.getCause()) {
            if (e instanceof ContentNotAvailableException) return clean(e.getMessage(), "This video isn't available.");
            if (e instanceof UnknownHostException) return "No internet connection.";
            if (e instanceof SocketTimeoutException) return "The connection timed out.";
        }
        if (t instanceof java.io.IOException) return "Network error: " + clean(t.getMessage(), "download failed");
        return clean(t == null ? null : t.getMessage(), "Conversion failed.");
    }

    private static String clean(String m, String fallback) {
        if (m == null) return fallback;
        m = m.trim();
        int nl = m.indexOf('\n');
        if (nl > 0) m = m.substring(0, nl);
        return m.isEmpty() ? fallback : (m.length() > 160 ? m.substring(0, 160) + "…" : m);
    }
}
