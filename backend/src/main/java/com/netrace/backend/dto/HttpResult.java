package com.netrace.backend.dto;

/**
 * downloadMs is the time spent receiving the response body after
 * headers arrived (totalTimeMs - ttfbMs) - a real derived duration
 * from two real measured timestamps, not an estimate. bodyTruncated is
 * true when the body exceeded the configured max response size and the
 * download was deliberately abandoned early to bound resource use; in
 * that case downloadMs reflects time spent downloading up to that
 * cutoff, not the time a full download would have taken. protocol is
 * the actually negotiated HTTP version for this response ("HTTP/1.1"
 * or "HTTP/2") - java.net.http.HttpClient has no HTTP/3 support at
 * all (its Version enum has only these two members), so HTTP/3 is
 * never reported here. url is already the final URL after redirects
 * are followed, not the originally requested one.
 * <p>
 * contentLength and contentType are read directly from the response
 * headers (Content-Length, Content-Type) - not derived from the body,
 * which is never buffered or otherwise stored. Both are null when the
 * server didn't send that header, which is common: chunked responses
 * routinely omit Content-Length, for example. contentLength reflects
 * what the server declared, even for a truncated download, since
 * that's what the metadata actually is - the number of body bytes
 * this analyzer happened to read before stopping is a different,
 * already-available thing (downloadMs/bodyTruncated), not this field.
 */
public record HttpResult(
        String url,
        int statusCode,
        long totalTimeMs,
        long ttfbMs,
        long downloadMs,
        boolean bodyTruncated,
        String protocol,
        Long contentLength,
        String contentType) {
}
