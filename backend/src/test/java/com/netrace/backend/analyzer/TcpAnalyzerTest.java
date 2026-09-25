package com.netrace.backend.analyzer;

import com.netrace.backend.config.AnalyzerProperties;
import com.netrace.backend.dto.PhaseResult;
import com.netrace.backend.dto.TcpFailureReason;
import com.netrace.backend.dto.TcpMetadata;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.NoRouteToHostException;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TcpAnalyzerTest {

    private final TcpAnalyzer analyzer = new TcpAnalyzer(
            new AnalyzerProperties(Duration.ofSeconds(2), Duration.ofSeconds(2)));

    private ServerSocket serverSocket;

    @AfterEach
    void closeServerSocket() throws IOException {
        if (serverSocket != null) {
            serverSocket.close();
        }
    }

    @Test
    void reportsSuccessAndARealDurationWhenTheHostIsListening() throws IOException {
        serverSocket = new ServerSocket(0);
        int port = serverSocket.getLocalPort();

        PhaseResult<TcpMetadata> result = analyzer.analyze("127.0.0.1", port);

        assertThat(result.phase()).isEqualTo("TCP");
        assertThat(result.status()).isEqualTo(PhaseResult.Status.SUCCESS);
        assertThat(result.durationMs()).isGreaterThanOrEqualTo(0);
        assertThat(result.metadata().host()).isEqualTo("127.0.0.1");
        assertThat(result.metadata().port()).isEqualTo(port);
        assertThat(result.metadata().failureReason()).isNull();
    }

    @Test
    void reportsConnectionRefusedAndARealDurationWhenNothingIsListening() throws IOException {
        // A closed local port reliably raises ECONNREFUSED (ConnectException)
        // rather than hanging, so this is real-network-backed, not mocked.
        int freePort;
        try (ServerSocket probe = new ServerSocket(0)) {
            freePort = probe.getLocalPort();
        }

        PhaseResult<TcpMetadata> result = analyzer.analyze("127.0.0.1", freePort);

        assertThat(result.phase()).isEqualTo("TCP");
        assertThat(result.status()).isEqualTo(PhaseResult.Status.FAILURE);
        assertThat(result.durationMs()).isGreaterThanOrEqualTo(0);
        assertThat(result.metadata().host()).isEqualTo("127.0.0.1");
        assertThat(result.metadata().port()).isEqualTo(freePort);
        assertThat(result.metadata().failureReason()).isEqualTo(TcpFailureReason.CONNECTION_REFUSED);
    }

    @Test
    void reportsTimeoutWhenTheConnectionAttemptTimesOut() {
        // A real network timeout can't be triggered deterministically in a
        // portable test (it needs a destination that silently drops
        // packets, which behaves inconsistently across environments), so
        // this exercises the exception-to-reason mapping directly, the
        // same way DnsAnalyzer's DNS_FAILURE mapping is tested.
        TcpAnalyzer timingOutAnalyzer = new TcpAnalyzer(
                new AnalyzerProperties(Duration.ofSeconds(2), Duration.ofSeconds(2)),
                (host, port, timeoutMs) -> {
                    throw new SocketTimeoutException("connect timed out");
                });

        PhaseResult<TcpMetadata> result = timingOutAnalyzer.analyze("203.0.113.1", 443);

        assertThat(result.status()).isEqualTo(PhaseResult.Status.FAILURE);
        assertThat(result.metadata().failureReason()).isEqualTo(TcpFailureReason.TIMEOUT);
    }

    @Test
    void reportsUnreachableWhenThereIsNoRouteToTheHost() {
        TcpAnalyzer unreachableAnalyzer = new TcpAnalyzer(
                new AnalyzerProperties(Duration.ofSeconds(2), Duration.ofSeconds(2)),
                (host, port, timeoutMs) -> {
                    throw new NoRouteToHostException("No route to host");
                });

        PhaseResult<TcpMetadata> result = unreachableAnalyzer.analyze("203.0.113.1", 443);

        assertThat(result.status()).isEqualTo(PhaseResult.Status.FAILURE);
        assertThat(result.metadata().failureReason()).isEqualTo(TcpFailureReason.UNREACHABLE);
    }

    @Test
    void reportsUnknownForAnyOtherIoException() {
        TcpAnalyzer failingAnalyzer = new TcpAnalyzer(
                new AnalyzerProperties(Duration.ofSeconds(2), Duration.ofSeconds(2)),
                (host, port, timeoutMs) -> {
                    throw new IOException("something else went wrong");
                });

        PhaseResult<TcpMetadata> result = failingAnalyzer.analyze("203.0.113.1", 443);

        assertThat(result.status()).isEqualTo(PhaseResult.Status.FAILURE);
        assertThat(result.metadata().failureReason()).isEqualTo(TcpFailureReason.UNKNOWN);
    }

    @Test
    void throwsForANegativePort() {
        assertThatThrownBy(() -> analyzer.analyze("127.0.0.1", -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void throwsForAPortAboveTheValidRange() {
        assertThatThrownBy(() -> analyzer.analyze("127.0.0.1", 70000))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
