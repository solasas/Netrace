package com.netrace.backend.dto;

public record AnalyzeResponse(

        String url,
        DnsResult dns,
        TcpResult tcp,
        int statusCode,
        long totalTimeMs

) {
}
