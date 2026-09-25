package com.netrace.backend.integration;

import com.netrace.backend.dto.AnalyzeRequest;
import com.netrace.backend.dto.AnalyzeResponse;
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
 * Full-stack tests against the real /api/analyze endpoint: real Spring
 * context, real validation/analyzer/service wiring, over a real embedded
 * HTTP server. Only the HTTPS success case reaches the public internet,
 * and it targets example.com specifically because it's IANA-reserved for
 * this purpose and has none of the instability a random public site
 * would. Every other case uses a local, fully controlled server.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class AnalyzeEndpointIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    private HttpServer localServer;

    @AfterEach
    void stopLocalServer() {
        if (localServer != null) {
            localServer.stop(0);
        }
    }

    @Test
    void analyzesARealHttpsUrlSuccessfully() {
        ResponseEntity<AnalyzeResponse> response = restTemplate.postForEntity(
                "/api/analyze", new AnalyzeRequest("https://example.com"), AnalyzeResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().statusCode()).isEqualTo(200);
        assertThat(response.getBody().url()).startsWith("https://example.com");
        assertThat(response.getBody().totalTimeMs()).isGreaterThanOrEqualTo(0);
        assertThat(response.getBody().dns()).isNotNull();
        assertThat(response.getBody().dns().hostname()).isEqualTo("example.com");
        assertThat(response.getBody().dns().resolvedIps()).isNotEmpty();
        assertThat(response.getBody().dns().durationMs()).isGreaterThanOrEqualTo(0);
        assertThat(response.getBody().tcp()).isNotNull();
        assertThat(response.getBody().tcp().host()).isEqualTo(response.getBody().dns().resolvedIps().get(0));
        assertThat(response.getBody().tcp().port()).isEqualTo(443);
        assertThat(response.getBody().tcp().durationMs()).isGreaterThanOrEqualTo(0);
        assertThat(response.getBody().tls()).isNotNull();
        assertThat(response.getBody().tls().tlsVersion()).startsWith("TLSv1");
        assertThat(response.getBody().tls().cipherSuite()).isNotBlank();
        assertThat(response.getBody().tls().certificateSubject()).isNotBlank();
        assertThat(response.getBody().tls().certificateIssuer()).isNotBlank();
        assertThat(response.getBody().tls().durationMs()).isGreaterThanOrEqualTo(0);
        assertThat(response.getBody().protocol()).isIn("HTTP/1.1", "HTTP/2");
        // Not asserting an exact contentLength here: whether Cloudflare
        // sends a Content-Length or uses chunked transfer for this page
        // isn't something this test should depend on.
        assertThat(response.getBody().contentType()).isNotBlank();
    }

    @Test
    void analyzesALocalHttpUrlSuccessfully() throws IOException {
        byte[] body = "hello from a local http server".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        localServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        localServer.createContext("/ok", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "text/plain");
            exchange.sendResponseHeaders(200, body.length);
            try (java.io.OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        localServer.start();
        String url = "http://localhost:" + localServer.getAddress().getPort() + "/ok";

        ResponseEntity<AnalyzeResponse> response = restTemplate.postForEntity(
                "/api/analyze", new AnalyzeRequest(url), AnalyzeResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().statusCode()).isEqualTo(200);
        assertThat(response.getBody().url()).isEqualTo(url);
        assertThat(response.getBody().dns()).isNotNull();
        assertThat(response.getBody().dns().hostname()).isEqualTo("localhost");
        assertThat(response.getBody().dns().resolvedIps()).isNotEmpty();
        assertThat(response.getBody().tcp()).isNotNull();
        assertThat(response.getBody().tcp().port()).isEqualTo(localServer.getAddress().getPort());
        assertThat(response.getBody().tcp().durationMs()).isGreaterThanOrEqualTo(0);
        assertThat(response.getBody().tls()).isNull();
        assertThat(response.getBody().protocol()).isEqualTo("HTTP/1.1");
        assertThat(response.getBody().contentType()).isEqualTo("text/plain");
        assertThat(response.getBody().contentLength()).isEqualTo((long) body.length);
    }

    @Test
    void rejectsAMalformedUrl() {
        ResponseEntity<ErrorResponse> response = restTemplate.postForEntity(
                "/api/analyze", new AnalyzeRequest("this is not a url"), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo("INVALID_URL");
    }

    @Test
    void rejectsAnUnsupportedScheme() {
        ResponseEntity<ErrorResponse> response = restTemplate.postForEntity(
                "/api/analyze", new AnalyzeRequest("ftp://example.com"), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo("INVALID_URL");
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
