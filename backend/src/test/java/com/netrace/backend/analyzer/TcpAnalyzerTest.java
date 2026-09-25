package com.netrace.backend.analyzer;

import com.netrace.backend.config.AnalyzerProperties;
import com.netrace.backend.dto.PhaseResult;
import com.netrace.backend.dto.TcpMetadata;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

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
    }

    @Test
    void reportsFailureAndARealDurationWhenNothingIsListening() throws IOException {
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
    }
}
