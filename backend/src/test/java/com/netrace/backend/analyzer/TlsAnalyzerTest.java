package com.netrace.backend.analyzer;

import com.netrace.backend.config.AnalyzerProperties;
import com.netrace.backend.dto.PhaseResult;
import com.netrace.backend.dto.TlsFailureReason;
import com.netrace.backend.dto.TlsMetadata;
import org.junit.jupiter.api.Test;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.security.KeyStore;
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

    @Test
    void allowedProtocolsExcludeLegacyTlsVersions() {
        // Security regression: the negotiable protocol set must be
        // explicitly pinned to 1.2/1.3, never left to fall back to
        // whatever the ambient JVM's defaults happen to allow.
        assertThat(TlsAnalyzer.ALLOWED_PROTOCOLS).containsExactly("TLSv1.3", "TLSv1.2");
        assertThat(TlsAnalyzer.ALLOWED_PROTOCOLS)
                .doesNotContain("TLSv1", "TLSv1.1", "SSLv3", "SSLv2Hello");
    }

    @Test
    void reportsAHandshakeFailureForAnUntrustedSelfSignedCertificate() throws Exception {
        // Real, not mocked: proves certificate trust-chain validation is
        // never bypassed - a distinct check from hostname verification
        // (above), since a cert can match the hostname and still be
        // untrusted. This is genuine JDK default trust store behavior:
        // TlsAnalyzer never installs a custom TrustManager.
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream in = getClass().getResourceAsStream("/self-signed-test.p12")) {
            keyStore.load(in, "changeit".toCharArray());
        }
        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(keyStore, "changeit".toCharArray());
        SSLContext serverContext = SSLContext.getInstance("TLS");
        serverContext.init(keyManagerFactory.getKeyManagers(), null, null);

        try (SSLServerSocket serverSocket =
                (SSLServerSocket) serverContext.getServerSocketFactory().createServerSocket(0)) {
            int port = serverSocket.getLocalPort();
            Thread serverThread = new Thread(() -> {
                try (SSLSocket accepted = (SSLSocket) serverSocket.accept()) {
                    accepted.startHandshake();
                } catch (IOException ignored) {
                    // Expected: the client (this test's TlsAnalyzer) does
                    // not trust our self-signed cert and aborts first.
                }
            });
            serverThread.start();

            PhaseResult<TlsMetadata> result = analyzer.analyze("localhost", "127.0.0.1", port);

            assertThat(result.status()).isEqualTo(PhaseResult.Status.FAILURE);
            assertThat(result.metadata().failureReason()).isEqualTo(TlsFailureReason.HANDSHAKE_FAILURE);

            serverThread.join(2000);
        }
    }

    @Test
    void doesNotLeakSocketsAcrossManyFailedConnectionAttempts() throws IOException {
        // If sockets weren't being closed on the failure path, file
        // descriptors would exhaust partway through this loop.
        int freePort;
        try (ServerSocket probe = new ServerSocket(0)) {
            freePort = probe.getLocalPort();
        }

        for (int i = 0; i < 300; i++) {
            PhaseResult<TlsMetadata> result = analyzer.analyze("localhost", "127.0.0.1", freePort);
            assertThat(result.status())
                    .as("iteration %d should still fail cleanly if sockets are being closed, not leaked", i)
                    .isEqualTo(PhaseResult.Status.FAILURE);
        }
    }
}
