package com.netrace.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class AnalyzerPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Test
    void defaultsToFiveSecondConnectAndTenSecondRequestTimeoutsWhenUnset() {
        contextRunner.run(context -> {
            AnalyzerProperties properties = context.getBean(AnalyzerProperties.class);
            assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(5));
            assertThat(properties.requestTimeout()).isEqualTo(Duration.ofSeconds(10));
        });
    }

    @Test
    void bindsCustomDurationsFromApplicationProperties() {
        contextRunner
                .withPropertyValues(
                        "netrace.analyzer.connect-timeout=2s",
                        "netrace.analyzer.request-timeout=500ms")
                .run(context -> {
                    AnalyzerProperties properties = context.getBean(AnalyzerProperties.class);
                    assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(2));
                    assertThat(properties.requestTimeout()).isEqualTo(Duration.ofMillis(500));
                });
    }

    @EnableConfigurationProperties(AnalyzerProperties.class)
    static class TestConfig {
    }
}
