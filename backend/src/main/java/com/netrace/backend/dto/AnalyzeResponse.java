package com.netrace.backend.dto;

public record AnalyzeResponse(

        String url,
        DnsResult dns,
        int statusCode,
        long totalTimeMs

) {
}
