package com.netrace.backend.integration;

import com.netrace.backend.dto.AnalyzeRequest;
import com.netrace.backend.dto.ErrorResponse;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Isolated in its own Spring context (separate from
 * AnalyzeEndpointIntegrationTest) because it overrides
 * netrace.analyzer.request-timeout to a value short enough to trigger
 * reliably against a local, deliberately slow server, without affecting
 * the real-network HTTPS success case in the other test class.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "netrace.analyzer.request-timeout=200ms")
@AutoConfigureTestRestTemplate
class AnalyzeEndpointTimeoutIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void reportsAGatewayTimeoutWhenTheTargetIsSlowerThanTheConfiguredTimeout() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/slow", exchange -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            try {
                exchange.sendResponseHeaders(200, -1);
            } finally {
                exchange.close();
            }
        });
        server.start();
        String url = "http://localhost:" + server.getAddress().getPort() + "/slow";

        ResponseEntity<ErrorResponse> response = restTemplate.postForEntity(
                "/api/analyze", new AnalyzeRequest(url), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo("TIMEOUT");
    }
}
