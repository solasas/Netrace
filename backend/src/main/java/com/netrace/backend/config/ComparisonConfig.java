package com.netrace.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A single, application-wide bounded thread pool for running the
 * per-URL analyses within a /api/compare request concurrently.
 * Deliberately fixed-size (matching CompareRequest's own max of 5
 * URLs) rather than one thread per URL with no cap: several concurrent
 * /api/compare requests share this same pool, so the number of
 * outbound analyses running at once across the whole application stays
 * bounded regardless of how many comparison requests arrive together.
 */
@Configuration
public class ComparisonConfig {

    private static final int COMPARISON_EXECUTOR_POOL_SIZE = 5;

    @Bean(destroyMethod = "shutdown")
    public ExecutorService comparisonExecutor() {
        return Executors.newFixedThreadPool(COMPARISON_EXECUTOR_POOL_SIZE);
    }
}
