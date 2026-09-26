package com.netrace.backend.integration;

import com.netrace.backend.dto.CompareRequest;
import com.netrace.backend.dto.CompareResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full-stack tests against the real /api/compare endpoint: real Spring
 * context, real ComparisonService/HttpAnalyzer wiring. example.com and
 * example.org are both IANA-reserved, stable real targets, matching the
 * convention used for /api/analyze's own real-network tests.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class CompareEndpointIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void comparesTwoRealUrlsSuccessfully() {
        ResponseEntity<CompareResponse> response = restTemplate.postForEntity(
                "/api/compare",
                new CompareRequest(List.of("https://example.com", "https://example.org")),
                CompareResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().results()).hasSize(2);
        assertThat(response.getBody().results()).allSatisfy(result -> {
            assertThat(result.success()).isTrue();
            assertThat(result.statusCode()).isEqualTo(200);
            assertThat(result.totalTimeMs()).isGreaterThanOrEqualTo(0);
            assertThat(result.error()).isNull();
        });
    }

    @Test
    void oneBlockedTargetDoesNotPreventResultsForTheOthers() {
        ResponseEntity<CompareResponse> response = restTemplate.postForEntity(
                "/api/compare",
                new CompareRequest(List.of("https://example.com", "http://localhost/", "https://example.org")),
                CompareResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        List<com.netrace.backend.dto.CompareResult> results = response.getBody().results();
        assertThat(results).hasSize(3);

        assertThat(results.get(0).url()).isEqualTo("https://example.com");
        assertThat(results.get(0).success()).isTrue();

        assertThat(results.get(1).url()).isEqualTo("http://localhost/");
        assertThat(results.get(1).success()).isFalse();
        assertThat(results.get(1).error()).contains("localhost");

        assertThat(results.get(2).url()).isEqualTo("https://example.org");
        assertThat(results.get(2).success()).isTrue();
    }
}
