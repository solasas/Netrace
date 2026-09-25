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
 * never reported here.
 */
public record HttpResult(
        String url,
        int statusCode,
        long totalTimeMs,
        long ttfbMs,
        long downloadMs,
        boolean bodyTruncated,
        String protocol) {
}
