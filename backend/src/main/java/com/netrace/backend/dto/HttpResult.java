package com.netrace.backend.dto;

/**
 * downloadMs is the time spent receiving the response body after
 * headers arrived (totalTimeMs - ttfbMs) - a real derived duration
 * from two real measured timestamps, not an estimate. bodyTruncated is
 * true when the body exceeded the configured max response size and the
 * download was deliberately abandoned early to bound resource use; in
 * that case downloadMs reflects time spent downloading up to that
 * cutoff, not the time a full download would have taken.
 */
public record HttpResult(
        String url, int statusCode, long totalTimeMs, long ttfbMs, long downloadMs, boolean bodyTruncated) {
}
