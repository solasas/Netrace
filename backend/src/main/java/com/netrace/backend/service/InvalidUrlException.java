package com.netrace.backend.service;

public class InvalidUrlException extends RuntimeException {

    public InvalidUrlException(String url) {
        super("Not a valid http/https URL: " + url);
    }
}
