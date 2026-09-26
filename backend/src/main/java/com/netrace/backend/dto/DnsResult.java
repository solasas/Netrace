package com.netrace.backend.dto;

import java.util.List;

/**
 * success is true whenever this DnsResult appears in a successful
 * /api/analyze response - a DNS failure there still fails the whole
 * request (see AnalysisService), so it's only ever false when
 * ComparisonService builds one itself to represent a single URL's DNS
 * failure in /api/compare, where one URL's DNS failure must not abort
 * the others. resolvedIpv4/resolvedIpv6 are empty lists (never null)
 * both when a lookup succeeded but returned no addresses of that
 * family, and when success is false.
 */
public record DnsResult(
        String hostname,
        List<String> resolvedIpv4,
        List<String> resolvedIpv6,
        long durationMs,
        boolean success) {
}
