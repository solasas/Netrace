package com.netrace.backend.analyzer;

public class AnalysisException extends RuntimeException {

    public enum Reason {
        CONNECTION_FAILURE,
        DNS_FAILURE,
        TIMEOUT,
        INVALID_RESPONSE,
        BLOCKED_TARGET
    }

    private final Reason reason;

    public AnalysisException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
