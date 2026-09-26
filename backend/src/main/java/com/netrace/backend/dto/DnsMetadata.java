package com.netrace.backend.dto;

import java.util.List;

public record DnsMetadata(String hostname, List<String> resolvedIpv4, List<String> resolvedIpv6) {
}
