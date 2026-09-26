package com.netrace.backend.dto;

/**
 * One URL's outcome within a comparison. success distinguishes a real
 * result from a failure at a glance; statusCode/protocol/totalTimeMs
 * are null on failure, and error is null on success - never a mix of
 * both, so a caller never has to guess which fields are meaningful.
 * dns, tcp, and tls are populated whenever those phases are analyzed,
 * including when they fail - they indicate which phase(s) succeeded
 * vs failed. tls is null for HTTP URLs. totalTimeMs is the same real,
 * observed measurement HttpAnalyzer reports for a single analysis
 * (see docs/measurement.md) - not a synthetic or averaged figure, and
 * not a claim that one URL is objectively faster than another based on
 * this one observation.
 */
public record CompareResult(
        String url,
        boolean success,
        Integer statusCode,
        String protocol,
        Long totalTimeMs,
        String error,
        DnsResult dns,
        TcpResult tcp,
        TlsResult tls
) {

    public static CompareResult success(String url, HttpResult result, DnsResult dns, TcpResult tcp, TlsResult tls) {
        return new CompareResult(url, true, result.statusCode(), result.protocol(), result.totalTimeMs(), null, dns, tcp, tls);
    }

    public static CompareResult failure(String url, String error) {
        return new CompareResult(url, false, null, null, null, error, null, null, null);
    }

    public static CompareResult failureWithPartialResults(String url, String error, DnsResult dns, TcpResult tcp, TlsResult tls) {
        return new CompareResult(url, false, null, null, null, error, dns, tcp, tls);
    }
}
