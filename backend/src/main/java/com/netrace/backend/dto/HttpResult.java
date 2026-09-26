package com.netrace.backend.dto;

/**
 * ttfbMs is the Observed Time to First Byte: wall-clock time from
 * request initiation to when the response status line and headers are
 * parsed by the HTTP client, before body bytes arrive. Bundles network
 * latency, request transmission, server processing, and response
 * transmission initiation. NOT equivalent to backend processing time in
 * isolation.
 * <p>
 * downloadMs is the time spent receiving the response body after
 * headers arrived (totalTimeMs - ttfbMs) - a real derived duration
 * from two real measured timestamps, not an estimate. bodyTruncated is
 * true when the body exceeded the configured max response size and the
 * download was deliberately abandoned early to bound resource use; in
 * that case downloadMs reflects time spent downloading up to that
 * cutoff, not the time a full download would have taken.
 * <p>
 * protocol is the actually negotiated HTTP version for this response
 * ("HTTP/1.1" or "HTTP/2") - java.net.http.HttpClient has no HTTP/3
 * support at all (its Version enum has only these two members), so
 * HTTP/3 is never reported here.
 * <p>
 * url is already the final URL after redirects are followed.
 * originalUrl is the URL initially requested. redirectCount is the
 * number of redirects followed to reach the final URL (0 if no
 * redirects).
 * <p>
 * contentLength is what the server declared in the Content-Length
 * header, null if missing. responseSizeBytes is the actual number of
 * body bytes read (up to the configured size limit). Both reflect
 * response metadata but are distinct: contentLength may differ from
 * responseSizeBytes when the download is truncated due to the size
 * limit, or when Content-Length is inaccurate. contentType is read
 * directly from the Content-Type header, null if missing.
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
        String contentType,
        String originalUrl,
        int redirectCount,
        long responseSizeBytes) {
}
