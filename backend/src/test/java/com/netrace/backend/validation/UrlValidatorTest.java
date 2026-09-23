package com.netrace.backend.validation;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class UrlValidatorTest {

    private final UrlValidator validator = new UrlValidator();

    @ParameterizedTest
    @ValueSource(strings = {
            "http://example.com",
            "https://example.com",
            "HTTPS://example.com",
            "http://example.com:8080/path?query=1#fragment",
            "https://sub.example.co.uk/path/to/resource",
            "http://192.168.1.1",
            "http://[::1]:8080/"
    })
    void acceptsValidHttpAndHttpsUrls(String url) {
        assertThat(validator.isValid(url)).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void rejectsAMissingUrl(String url) {
        assertThat(validator.isValid(url)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "not a url",
            "example.com",
            "http://",
            "http:///path",
            "http://:8080/path",
            "://example.com"
    })
    void rejectsAMalformedUrl(String url) {
        assertThat(validator.isValid(url)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "ftp://example.com",
            "file:///etc/passwd",
            "javascript:alert(1)",
            "ws://example.com",
            "mailto:someone@example.com"
    })
    void rejectsAnUnsupportedScheme(String url) {
        assertThat(validator.isValid(url)).isFalse();
    }
}
