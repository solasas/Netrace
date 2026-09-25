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
import java.net.ServerSocket;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Isolated in its own Spring context (separate from
 * AnalyzeEndpointIntegrationTest) because it overrides two properties:
 * netrace.analyzer.request-timeout, short enough to trigger reliably
 * against a local, deliberately slow server; and
 * netrace.analyzer.allow-private-targets=true, without which the real
 * SsrfGuard-backed target guard would refuse every test here before it
 * could reach the scenario each one is actually about (a slow server, a
 * closed port) - both of which require a real, controllable local
 * server (on a loopback address) to reproduce deterministically without
 * depending on real external network conditions. Deliberately
 * bypassing the guard is the point of this class; it is not how the
 * production default behaves, and docs/security.md documents why the
 * property exists and its risk if ever set outside a controlled
 * testing context like this one.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "netrace.analyzer.request-timeout=200ms",
                "netrace.analyzer.allow-private-targets=true"
        })
@AutoConfigureTestRestTemplate
class AnalyzeEndpointLocalTargetIntegrationTest {

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

    @Test
    void reportsAConnectionFailureForAnUnreachableHost() throws IOException {
        int freePort;
        try (ServerSocket probe = new ServerSocket(0)) {
            freePort = probe.getLocalPort();
        }

        ResponseEntity<ErrorResponse> response = restTemplate.postForEntity(
                "/api/analyze", new AnalyzeRequest("http://localhost:" + freePort + "/"), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo("CONNECTION_FAILURE");
    }
}
