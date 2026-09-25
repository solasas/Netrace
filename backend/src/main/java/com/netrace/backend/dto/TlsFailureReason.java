package com.netrace.backend.dto;

/**
 * Why a TLS connection attempt failed, based on which exception type
 * JSSE threw. JSSE does not reliably distinguish "bad certificate"
 * from "no common protocol/cipher" - both typically surface as
 * SSLHandshakeException - so both are reported as HANDSHAKE_FAILURE
 * rather than guessed apart from exception message text.
 */
public enum TlsFailureReason {
    CONNECTION_FAILURE,
    TIMEOUT,
    HANDSHAKE_FAILURE,
    UNKNOWN
}
