package com.netrace.backend.dto;

/**
 * Independent, one-off diagnostic measurements of DNS resolution, TCP
 * connection establishment, and (for HTTPS) TLS handshake - each
 * performed as its own separate, dedicated operation against the
 * target, not the DNS/TCP/TLS activity that happens inside the real
 * HTTP request reported alongside this object. Their durations must
 * not be summed with each other or with the request's own timing
 * fields (ttfbMs/downloadMs/totalTimeMs) - see docs/measurement.md for
 * exactly why, including why tcp.durationMs and tls.durationMs are
 * themselves not additive.
 */
public record Probes(DnsResult dns, TcpResult tcp, TlsResult tls) {
}
