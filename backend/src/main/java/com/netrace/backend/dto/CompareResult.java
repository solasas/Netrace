package com.netrace.backend.dto;

/**
 * One URL's outcome within a comparison. success distinguishes a real
 * result from a failure at a glance; statusCode/protocol/totalTimeMs
 * are null on failure, and error is null on success - never a mix of
 * both, so a caller never has to guess which fields are meaningful.
 * totalTimeMs is the same real, observed measurement HttpAnalyzer
 * reports for a single analysis (see docs/measurement.md) - not a
 * synthetic or averaged figure, and not a claim that one URL is
 * objectively faster than another based on this one observation.
 */
public record CompareResult(
        String url,
        boolean success,
        Integer statusCode,
        String protocol,
        Long totalTimeMs,
        String error
) {

    public static CompareResult success(String url, HttpResult result) {
        return new CompareResult(url, true, result.statusCode(), result.protocol(), result.totalTimeMs(), null);
    }

    public static CompareResult failure(String url, String error) {
        return new CompareResult(url, false, null, null, null, error);
    }
}
