package com.netrace.backend.dto;

/**
 * Why a TCP connection attempt failed, as far as java.net.Socket can
 * tell us - it distinguishes these based on which checked exception
 * type the JDK threw, not on any packet-level detail.
 */
public enum TcpFailureReason {
    CONNECTION_REFUSED,
    TIMEOUT,
    UNREACHABLE,
    UNKNOWN
}
