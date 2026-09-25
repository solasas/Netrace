package com.netrace.backend.dto;

/**
 * hostname is what SNI and certificate hostname verification are
 * checked against; resolvedIp is what was actually connected to -
 * they are kept separate because a resolved IP is never a valid SNI
 * value or certificate subject for a normal HTTPS host. tlsVersion,
 * cipherSuite, certificateSubject and certificateIssuer are only ever
 * populated on success (null on failure).
 */
public record TlsMetadata(

        String hostname,
        String resolvedIp,
        int port,
        String tlsVersion,
        String cipherSuite,
        String certificateSubject,
        String certificateIssuer,
        TlsFailureReason failureReason

) {

    public TlsMetadata(String hostname, String resolvedIp, int port, TlsFailureReason failureReason) {
        this(hostname, resolvedIp, port, null, null, null, null, failureReason);
    }
}
