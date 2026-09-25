package com.netrace.backend.dto;

/**
 * Result of a single measurement phase (DNS, TCP today; HTTP and
 * eventually TLS are expected to use it too): which phase it was, how
 * long it took, its status, and phase-specific data. Some phases (DNS,
 * HTTP) still short-circuit a failure by throwing AnalysisException
 * rather than returning a FAILURE PhaseResult, when failure means no
 * meaningful measurement could be taken at all. TCP is different: a
 * refused or timed-out connection attempt still has a real, meaningful
 * duration, so it's reported as a normal FAILURE result instead.
 */
public record PhaseResult<T>(String phase, long durationMs, Status status, T metadata) {

    public enum Status {
        SUCCESS,
        FAILURE
    }

    public static <T> PhaseResult<T> success(String phase, long durationMs, T metadata) {
        return new PhaseResult<>(phase, durationMs, Status.SUCCESS, metadata);
    }

    public static <T> PhaseResult<T> failure(String phase, long durationMs, T metadata) {
        return new PhaseResult<>(phase, durationMs, Status.FAILURE, metadata);
    }
}
