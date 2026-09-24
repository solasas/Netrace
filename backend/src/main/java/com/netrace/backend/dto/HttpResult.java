package com.netrace.backend.dto;

public record HttpResult(String url, int statusCode, long totalTimeMs) {
}
