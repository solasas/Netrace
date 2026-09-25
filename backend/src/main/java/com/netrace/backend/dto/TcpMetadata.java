package com.netrace.backend.dto;

public record TcpMetadata(String host, int port, TcpFailureReason failureReason) {

    public TcpMetadata(String host, int port) {
        this(host, port, null);
    }
}
