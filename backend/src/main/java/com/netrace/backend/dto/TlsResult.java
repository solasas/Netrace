package com.netrace.backend.dto;

public record TlsResult(
        String tlsVersion,
        String cipherSuite,
        String certificateSubject,
        String certificateIssuer,
        long durationMs
) {
}
