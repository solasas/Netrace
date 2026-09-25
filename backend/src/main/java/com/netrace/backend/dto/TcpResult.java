package com.netrace.backend.dto;

public record TcpResult(String host, int port, long durationMs) {
}
