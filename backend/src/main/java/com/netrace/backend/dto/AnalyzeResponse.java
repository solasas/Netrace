package com.netrace.backend.dto;

public record AnalyzeResponse(

        String url,
        int statusCode,
        long totalTimeMs

) {
}
