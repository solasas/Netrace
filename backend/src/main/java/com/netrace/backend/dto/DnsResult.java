package com.netrace.backend.dto;

import java.util.List;

public record DnsResult(String hostname, List<String> resolvedIps, long durationMs) {
}
