package com.netrace.backend.validation;

import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;

/**
 * Checks that a string is a syntactically well-formed http/https URL.
 * This is syntax validation only: it does not resolve the host, check
 * reachability, or guard against SSRF (private/loopback/link-local
 * targets). That is a separate, later concern.
 */
@Component
public class UrlValidator {

    private static final Set<String> SUPPORTED_SCHEMES = Set.of("http", "https");

    public boolean isValid(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }

        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            return false;
        }

        String scheme = uri.getScheme();
        if (scheme == null || !SUPPORTED_SCHEMES.contains(scheme.toLowerCase())) {
            return false;
        }

        return uri.getHost() != null && !uri.getHost().isBlank();
    }
}
