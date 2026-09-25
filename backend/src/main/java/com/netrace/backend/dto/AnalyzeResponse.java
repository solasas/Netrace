package com.netrace.backend.dto;

public record AnalyzeResponse(

        String url,
        DnsResult dns,
        TcpResult tcp,
        TlsResult tls,
        int statusCode,
        String protocol,
        long totalTimeMs

) {
}
