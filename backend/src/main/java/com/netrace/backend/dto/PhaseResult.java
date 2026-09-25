package com.netrace.backend.dto;

/**
 * Result of a single measurement phase (DNS today; HTTP and eventually
 * TCP/TLS are expected to use it too): which phase it was, how long it
 * took, its status, and phase-specific data. A phase that fails still
 * short-circuits by throwing AnalysisException, the same as before this
 * type existed - Status has only SUCCESS because nothing constructs a
 * PhaseResult for a failed phase yet.
 */
public record PhaseResult<T>(String phase, long durationMs, Status status, T metadata) {

    public enum Status {
        SUCCESS
    }

    public static <T> PhaseResult<T> success(String phase, long durationMs, T metadata) {
        return new PhaseResult<>(phase, durationMs, Status.SUCCESS, metadata);
    }
}
