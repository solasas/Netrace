package com.netrace.backend.validation;

import com.netrace.backend.config.AnalyzerProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.Set;

/**
 * Checks that a string is a syntactically well-formed http/https URL.
 * This is syntax validation only: it does not resolve the host or
 * check reachability - see SsrfGuard for the separate,
 * resolved-address-based check that handles private/loopback/
 * link-local targets.
 * <p>
 * It does, however, restrict the destination <b>port</b> to exactly
 * the requesting scheme's own standard port (80 for http, 443 for
 * https) - an explicit port is accepted only when it matches that
 * standard port, which is equivalent to not specifying one at all.
 * This project makes a real outbound TCP connection to whatever port a
 * URL names; without this restriction, an otherwise-public,
 * non-SSRF-blocked host with an arbitrary explicit port (e.g.
 * "https://some-public-host.com:22/") would let this service be used
 * as a generic TCP port prober against any host, not just an HTTP(S)
 * server on its standard port. See docs/security.md.
 * <p>
 * The no-arg constructor (used directly by UrlValidatorTest) always
 * enforces this; the AnalyzerProperties-aware constructor Spring wires
 * in production relaxes it when netrace.analyzer.allow-private-targets
 * is set, the same property (and for the same "controlled testing/
 * internal context only" reason) HttpAnalyzer and AnalysisService use
 * to relax their own SsrfGuard-backed address checks - a real local
 * test server is always both a private address and a non-standard
 * port, so in practice the two are relaxed together.
 */
@Component
public class UrlValidator {

    private static final Set<String> SUPPORTED_SCHEMES = Set.of("http", "https");
    private static final Map<String, Integer> STANDARD_PORTS = Map.of("http", 80, "https", 443);

    private final boolean allowNonStandardPorts;

    public UrlValidator() {
        this.allowNonStandardPorts = false;
    }

    @Autowired
    public UrlValidator(AnalyzerProperties analyzerProperties) {
        this.allowNonStandardPorts = analyzerProperties.allowPrivateTargets();
    }

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

        if (uri.getHost() == null || uri.getHost().isBlank()) {
            return false;
        }

        if (allowNonStandardPorts) {
            return true;
        }
        int port = uri.getPort();
        return port == -1 || port == STANDARD_PORTS.get(scheme.toLowerCase());
    }
}
