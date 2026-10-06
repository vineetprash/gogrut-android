package com.grogu.yt.core;

import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The one thing NewPipeExtractor asks the host app to provide: how to do an HTTP request. */
public final class HttpDownloader extends Downloader {
    private static final Charset UTF8 = Charset.forName("UTF-8");

    @Override
    public Response execute(Request request) throws IOException, ReCaptchaException {
        HttpURLConnection c = Http.open(request.httpMethod(), request.url(), request.headers(), request.dataToSend());
        try {
            int code = c.getResponseCode();
            if (code == 429) throw new ReCaptchaException("reCaptcha Challenge requested", request.url());
            byte[] body = Http.readAll(Http.bodyStream(c), 32L * 1024 * 1024);
            Map<String, List<String>> headers = new LinkedHashMap<String, List<String>>();
            for (Map.Entry<String, List<String>> e : c.getHeaderFields().entrySet()) {
                if (e.getKey() != null) headers.put(e.getKey(), new ArrayList<String>(e.getValue()));   // null key = status line
            }
            return new Response(code, c.getResponseMessage(), headers, new String(body, UTF8), c.getURL().toString());
        } finally {
            c.disconnect();
        }
    }
}
