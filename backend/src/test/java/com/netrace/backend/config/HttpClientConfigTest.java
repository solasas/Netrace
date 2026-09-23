package com.netrace.backend.config;

import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class HttpClientConfigTest {

    @Test
    void appliesTheConfiguredConnectTimeoutToTheBuiltClient() {
        AnalyzerProperties properties = new AnalyzerProperties(Duration.ofSeconds(3), Duration.ofSeconds(7));

        HttpClient client = new HttpClientConfig().httpClient(properties);

        assertThat(client.connectTimeout()).contains(Duration.ofSeconds(3));
    }
}
