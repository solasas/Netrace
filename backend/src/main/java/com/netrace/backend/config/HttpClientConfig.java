package com.netrace.backend.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;

/**
 * followRedirects is deliberately NEVER, not NORMAL: automatic
 * redirect-following would let the client silently connect to a
 * redirect target we never got a chance to run through SsrfGuard,
 * defeating the point of validating targets at all (see
 * docs/security.md). HttpAnalyzer follows redirects itself, one hop at
 * a time, validating each target before connecting to it.
 */
@Configuration
@EnableConfigurationProperties(AnalyzerProperties.class)
public class HttpClientConfig {

    @Bean
    public HttpClient httpClient(AnalyzerProperties analyzerProperties) {
        return HttpClient.newBuilder()
                .connectTimeout(analyzerProperties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }
}
