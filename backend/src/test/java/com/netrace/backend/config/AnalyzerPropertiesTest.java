package com.netrace.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.util.unit.DataSize;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class AnalyzerPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Test
    void defaultsToFiveSecondConnectTenSecondRequestAndTenMegabyteMaxResponseSizeWhenUnset() {
        contextRunner.run(context -> {
            AnalyzerProperties properties = context.getBean(AnalyzerProperties.class);
            assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(5));
            assertThat(properties.requestTimeout()).isEqualTo(Duration.ofSeconds(10));
            assertThat(properties.maxResponseSize()).isEqualTo(DataSize.ofMegabytes(10));
        });
    }

    @Test
    void bindsCustomDurationsAndMaxResponseSizeFromApplicationProperties() {
        contextRunner
                .withPropertyValues(
                        "netrace.analyzer.connect-timeout=2s",
                        "netrace.analyzer.request-timeout=500ms",
                        "netrace.analyzer.max-response-size=1KB")
                .run(context -> {
                    AnalyzerProperties properties = context.getBean(AnalyzerProperties.class);
                    assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(2));
                    assertThat(properties.requestTimeout()).isEqualTo(Duration.ofMillis(500));
                    assertThat(properties.maxResponseSize()).isEqualTo(DataSize.ofKilobytes(1));
                });
    }

    @EnableConfigurationProperties(AnalyzerProperties.class)
    static class TestConfig {
    }
}
