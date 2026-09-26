package com.netrace.backend.dto;

/**
 * success distinguishes a real TCP connection result from a failure at a
 * glance; failureReason is present (non-null) only when success is false.
 * durationMs is real, observed wall-clock time around Socket.connect(),
 * meaningful for both success and failure - a timed-out connection still
 * has a real elapsed duration.
 */
public record TcpResult(String host, int port, long durationMs, boolean success, TcpFailureReason failureReason) {

    public static TcpResult success(String host, int port, long durationMs) {
        return new TcpResult(host, port, durationMs, true, null);
    }

    public static TcpResult failure(String host, int port, long durationMs, TcpFailureReason reason) {
        return new TcpResult(host, port, durationMs, false, reason);
    }
}
