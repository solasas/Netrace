package com.netrace.backend.integration;

import com.netrace.backend.dto.AnalyzeRequest;
import com.netrace.backend.dto.AnalyzeResponse;
import com.netrace.backend.dto.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full-stack tests against the real /api/analyze endpoint: real Spring
 * context, real validation/analyzer/service wiring. The success cases
 * reach the public internet and target example.com specifically
 * because it's IANA-reserved for this purpose and has none of the
 * instability a random public site would.
 * <p>
 * There is deliberately no "analyzes a local HTTP server successfully"
 * test here: with the real, default SsrfGuard-backed target guard
 * active (netrace.analyzer.allow-private-targets is NOT overridden in
 * this class), a local server on localhost is exactly what should be
 * refused - see blocksALocalHttpUrlAsAnSsrfTarget below, and
 * docs/security.md for the full threat model. Tests that need a real,
 * controllable local server for scenarios other than SSRF blocking
 * (timeouts, connection-refused) live in
 * AnalyzeEndpointLocalTargetIntegrationTest, which explicitly opts into
 * allow-private-targets=true for that purpose.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class AnalyzeEndpointIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void analyzesARealHttpsUrlSuccessfully() {
        ResponseEntity<AnalyzeResponse> response = restTemplate.postForEntity(
                "/api/analyze", new AnalyzeRequest("https://example.com"), AnalyzeResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().statusCode()).isEqualTo(200);
        assertThat(response.getBody().url()).startsWith("https://example.com");
        assertThat(response.getBody().totalTimeMs()).isGreaterThanOrEqualTo(0);
        assertThat(response.getBody().ttfbMs()).isGreaterThanOrEqualTo(0);
        assertThat(response.getBody().downloadMs()).isGreaterThanOrEqualTo(0);
        assertThat(response.getBody().ttfbMs() + response.getBody().downloadMs())
                .isEqualTo(response.getBody().totalTimeMs());
        assertThat(response.getBody().probes().dns()).isNotNull();
        assertThat(response.getBody().probes().dns().hostname()).isEqualTo("example.com");
        // example.com is dual-stack (verified real, stable behavior), so
        // both families should be non-empty here.
        assertThat(response.getBody().probes().dns().resolvedIpv4()).isNotEmpty();
        assertThat(response.getBody().probes().dns().resolvedIpv6()).isNotEmpty();
        assertThat(response.getBody().probes().dns().durationMs()).isGreaterThanOrEqualTo(0);
        assertThat(response.getBody().probes().dns().success()).isTrue();
        assertThat(response.getBody().probes().tcp()).isNotNull();
        assertThat(response.getBody().probes().tcp().host())
                .isEqualTo(response.getBody().probes().dns().resolvedIpv4().get(0));
        assertThat(response.getBody().probes().tcp().port()).isEqualTo(443);
        assertThat(response.getBody().probes().tcp().durationMs()).isGreaterThanOrEqualTo(0);
        assertThat(response.getBody().probes().tls()).isNotNull();
        assertThat(response.getBody().probes().tls().tlsVersion()).startsWith("TLSv1");
        assertThat(response.getBody().probes().tls().cipherSuite()).isNotBlank();
        assertThat(response.getBody().probes().tls().certificateSubject()).isNotBlank();
        assertThat(response.getBody().probes().tls().certificateIssuer()).isNotBlank();
        assertThat(response.getBody().probes().tls().durationMs()).isGreaterThanOrEqualTo(0);
        assertThat(response.getBody().protocol()).isIn("HTTP/1.1", "HTTP/2");
        // Not asserting an exact contentLength here: whether Cloudflare
        // sends a Content-Length or uses chunked transfer for this page
        // isn't something this test should depend on.
        assertThat(response.getBody().contentType()).isNotBlank();
    }

    @Test
    void analyzesARealHttpUrlSuccessfully() {
        ResponseEntity<AnalyzeResponse> response = restTemplate.postForEntity(
                "/api/analyze", new AnalyzeRequest("http://example.com"), AnalyzeResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().statusCode()).isEqualTo(200);
        assertThat(response.getBody().url()).startsWith("http://example.com");
        assertThat(response.getBody().ttfbMs() + response.getBody().downloadMs())
                .isEqualTo(response.getBody().totalTimeMs());
        assertThat(response.getBody().probes().dns()).isNotNull();
        assertThat(response.getBody().probes().dns().hostname()).isEqualTo("example.com");
        assertThat(response.getBody().probes().tcp()).isNotNull();
        assertThat(response.getBody().probes().tcp().port()).isEqualTo(80);
        assertThat(response.getBody().probes().tls()).isNull();
        assertThat(response.getBody().protocol()).isIn("HTTP/1.1", "HTTP/2");
    }

    @Test
    void blocksALocalHttpUrlAsAnSsrfTarget() {
        // Proves the SSRF guard is wired all the way through the real
        // stack, not just at the unit level. No real server is needed
        // (or even possible to use meaningfully here): "localhost" with
        // no explicit port passes URL validation - port 80 is this
        // scheme's own standard port - and is refused because it
        // resolves to a loopback address, before any TCP connection is
        // attempted. See docs/security.md.
        ResponseEntity<ErrorResponse> response = restTemplate.postForEntity(
                "/api/analyze", new AnalyzeRequest("http://localhost/"), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo("BLOCKED_TARGET");
    }

    @Test
    void rejectsAUrlWithANonStandardPort() {
        // A public-looking host with an arbitrary explicit port must be
        // rejected before any network activity, not silently
        // reinterpreted - otherwise this endpoint could be used to
        // probe whether an arbitrary port is open on any public host.
        // See docs/security.md.
        ResponseEntity<ErrorResponse> response = restTemplate.postForEntity(
                "/api/analyze", new AnalyzeRequest("https://example.com:8443/"), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo("INVALID_URL");
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
}
