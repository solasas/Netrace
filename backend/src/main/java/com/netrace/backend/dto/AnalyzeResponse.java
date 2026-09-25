package com.netrace.backend.dto;

/**
 * ttfbMs, downloadMs, and totalTimeMs describe the one real HTTP
 * request whose statusCode/protocol/content* this response reports;
 * totalTimeMs == ttfbMs + downloadMs always holds - that is the only
 * arithmetic identity this model guarantees. probes holds separate,
 * independent DNS/TCP/TLS measurements that are not part of that
 * request's timeline and must not be summed with it or with each
 * other. See docs/measurement.md for the full methodology and why.
 */
public record AnalyzeResponse(

        String url,
        int statusCode,
        String protocol,
        Long contentLength,
        String contentType,
        long ttfbMs,
        long downloadMs,
        boolean bodyTruncated,
        long totalTimeMs,
        Probes probes

) {
}
