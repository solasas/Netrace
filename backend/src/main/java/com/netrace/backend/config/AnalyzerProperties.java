package com.netrace.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Timeouts for outbound analysis requests, bound from
 * netrace.analyzer.* application properties. See README for the
 * property names, defaults, and accepted formats.
 */
@ConfigurationProperties(prefix = "netrace.analyzer")
public record AnalyzerProperties(

        @DefaultValue("5s") Duration connectTimeout,
        @DefaultValue("10s") Duration requestTimeout

) {
}
