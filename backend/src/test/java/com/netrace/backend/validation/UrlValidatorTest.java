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
            "http://example.com/path?query=1#fragment",
            "https://sub.example.co.uk/path/to/resource",
            "http://192.168.1.1",
            "http://[::1]/",
            // An explicit port is accepted only when it matches the
            // scheme's own standard port - equivalent to not specifying
            // one at all, never a different, arbitrary destination.
            "http://example.com:80/",
            "https://example.com:443/"
    })
    void acceptsValidHttpAndHttpsUrls(String url) {
        assertThat(validator.isValid(url)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // This project makes a real outbound TCP connection to
            // whatever port a URL names - accepting an arbitrary
            // explicit port here would let Netrace be used as a
            // generic TCP port prober against any host, not just an
            // HTTP(S) server on its standard port. See docs/security.md.
            "http://example.com:8080/",
            "https://example.com:8443/",
            "http://example.com:22/",
            // Right scheme, but the *other* scheme's standard port -
            // still not this scheme's own standard port, so still
            // rejected.
            "http://example.com:443/",
            "https://example.com:80/"
    })
    void rejectsAnExplicitNonStandardPort(String url) {
        assertThat(validator.isValid(url)).isFalse();
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
