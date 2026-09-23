package com.netrace.backend.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;

@Configuration
@EnableConfigurationProperties(AnalyzerProperties.class)
public class HttpClientConfig {

    @Bean
    public HttpClient httpClient(AnalyzerProperties analyzerProperties) {
        return HttpClient.newBuilder()
                .connectTimeout(analyzerProperties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }
}
