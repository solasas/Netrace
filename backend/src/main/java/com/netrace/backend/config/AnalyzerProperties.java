package com.netrace.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

import java.time.Duration;

/**
 * Timeouts and safety limits for outbound analysis requests, bound
 * from netrace.analyzer.* application properties. See README for the
 * property names, defaults, and accepted formats.
 * <p>
 * allowPrivateTargets defaults to false: this project fetches
 * arbitrary user-supplied URLs server-side (see docs/security.md), so
 * by default AnalysisService and HttpAnalyzer refuse any target that
 * resolves to a loopback, private, link-local, or otherwise reserved
 * address. Setting it true disables that check entirely for every
 * request this instance handles - only appropriate for a controlled
 * testing/internal deployment that deliberately wants to analyze
 * targets on its own private network, never for anything reachable
 * from an untrusted caller.
 */
@ConfigurationProperties(prefix = "netrace.analyzer")
public record AnalyzerProperties(

        @DefaultValue("5s") Duration connectTimeout,
        @DefaultValue("10s") Duration requestTimeout,
        @DefaultValue("10MB") DataSize maxResponseSize,
        @DefaultValue("5") int maxRedirects,
        @DefaultValue("false") boolean allowPrivateTargets

) {

    @ConstructorBinding
    public AnalyzerProperties {
    }

    public AnalyzerProperties(Duration connectTimeout, Duration requestTimeout, DataSize maxResponseSize) {
        this(connectTimeout, requestTimeout, maxResponseSize, 5, false);
    }

    public AnalyzerProperties(Duration connectTimeout, Duration requestTimeout) {
        this(connectTimeout, requestTimeout, DataSize.ofMegabytes(10));
    }
}
