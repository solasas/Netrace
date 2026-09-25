package com.netrace.backend.analyzer;

import com.netrace.backend.config.AnalyzerProperties;
import com.netrace.backend.dto.PhaseResult;
import com.netrace.backend.dto.TlsFailureReason;
import com.netrace.backend.dto.TlsMetadata;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TlsAnalyzerTest {

    private final TlsAnalyzer analyzer = new TlsAnalyzer(
            new AnalyzerProperties(Duration.ofSeconds(5), Duration.ofSeconds(5)));

    @Test
    void capturesTlsVersionCipherSuiteAndCertificateInfoForARealHandshake() throws IOException {
        String hostname = "example.com";
        String resolvedIp = InetAddress.getByName(hostname).getHostAddress();

        PhaseResult<TlsMetadata> result = analyzer.analyze(hostname, resolvedIp, 443);

        assertThat(result.phase()).isEqualTo("TLS");
        assertThat(result.status()).isEqualTo(PhaseResult.Status.SUCCESS);
        assertThat(result.durationMs()).isGreaterThanOrEqualTo(0);
        assertThat(result.metadata().hostname()).isEqualTo(hostname);
        assertThat(result.metadata().resolvedIp()).isEqualTo(resolvedIp);
        assertThat(result.metadata().port()).isEqualTo(443);
        assertThat(result.metadata().tlsVersion()).startsWith("TLSv1");
        assertThat(result.metadata().cipherSuite()).isNotBlank();
        assertThat(result.metadata().certificateSubject()).isNotBlank();
        assertThat(result.metadata().certificateIssuer()).isNotBlank();
        assertThat(result.metadata().failureReason()).isNull();
    }

    @Test
    void reportsAHandshakeFailureWhenTheHostnameDoesNotMatchTheCertificate() throws IOException {
        // Real, deterministic (not flaky): connecting to example.com's real
        // IP but verifying against an unrelated hostname must always fail
        // hostname/endpoint verification, proving that verification is
        // actually enabled, not just intended.
        String resolvedIp = InetAddress.getByName("example.com").getHostAddress();

        PhaseResult<TlsMetadata> result = analyzer.analyze("this-is-not-example-dot-com.test", resolvedIp, 443);

        assertThat(result.status()).isEqualTo(PhaseResult.Status.FAILURE);
        assertThat(result.metadata().failureReason()).isEqualTo(TlsFailureReason.HANDSHAKE_FAILURE);
    }

    @Test
    void reportsConnectionFailureAndARealDurationWhenNothingIsListening() throws IOException {
        int freePort;
        try (ServerSocket probe = new ServerSocket(0)) {
            freePort = probe.getLocalPort();
        }

        PhaseResult<TlsMetadata> result = analyzer.analyze("localhost", "127.0.0.1", freePort);

        assertThat(result.status()).isEqualTo(PhaseResult.Status.FAILURE);
        assertThat(result.durationMs()).isGreaterThanOrEqualTo(0);
        assertThat(result.metadata().failureReason()).isEqualTo(TlsFailureReason.CONNECTION_FAILURE);
    }

    @Test
    void reportsTimeoutWhenTheHandshakeTimesOut() {
        // A real network handshake timeout isn't reliably reproducible in a
        // portable test, so this exercises the exception-to-reason mapping
        // directly, the same way DnsAnalyzer/TcpAnalyzer's failure mappings
        // are tested.
        TlsAnalyzer timingOutAnalyzer = new TlsAnalyzer(
                new AnalyzerProperties(Duration.ofSeconds(2), Duration.ofSeconds(2)),
                (hostname, resolvedIp, port, timeoutMs) -> {
                    throw new SocketTimeoutException("handshake timed out");
                });

        PhaseResult<TlsMetadata> result = timingOutAnalyzer.analyze("example.com", "203.0.113.1", 443);

        assertThat(result.status()).isEqualTo(PhaseResult.Status.FAILURE);
        assertThat(result.metadata().failureReason()).isEqualTo(TlsFailureReason.TIMEOUT);
    }

    @Test
    void reportsUnknownForAnyOtherIoException() {
        TlsAnalyzer failingAnalyzer = new TlsAnalyzer(
                new AnalyzerProperties(Duration.ofSeconds(2), Duration.ofSeconds(2)),
                (hostname, resolvedIp, port, timeoutMs) -> {
                    throw new IOException("something else went wrong");
                });

        PhaseResult<TlsMetadata> result = failingAnalyzer.analyze("example.com", "203.0.113.1", 443);

        assertThat(result.status()).isEqualTo(PhaseResult.Status.FAILURE);
        assertThat(result.metadata().failureReason()).isEqualTo(TlsFailureReason.UNKNOWN);
    }

    @Test
    void throwsForANegativePort() {
        assertThatThrownBy(() -> analyzer.analyze("example.com", "93.184.216.34", -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void throwsForAPortAboveTheValidRange() {
        assertThatThrownBy(() -> analyzer.analyze("example.com", "93.184.216.34", 70000))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructorRejectsAZeroConnectTimeout() {
        assertThatThrownBy(() -> new TlsAnalyzer(new AnalyzerProperties(Duration.ZERO, Duration.ofSeconds(2))))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void constructorRejectsANegativeConnectTimeout() {
        assertThatThrownBy(() ->
                new TlsAnalyzer(new AnalyzerProperties(Duration.ofSeconds(-1), Duration.ofSeconds(2))))
                .isInstanceOf(IllegalStateException.class);
    }
}
