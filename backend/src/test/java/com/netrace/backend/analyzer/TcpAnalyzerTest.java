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
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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

    @Test
    void constructorRejectsAZeroConnectTimeout() {
        // Socket.connect(address, 0) means "block forever," not "fail
        // immediately" - a 0 config value must never reach it.
        assertThatThrownBy(() -> new TcpAnalyzer(new AnalyzerProperties(Duration.ZERO, Duration.ofSeconds(2))))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void constructorRejectsANegativeConnectTimeout() {
        assertThatThrownBy(() ->
                new TcpAnalyzer(new AnalyzerProperties(Duration.ofSeconds(-1), Duration.ofSeconds(2))))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void isSafeForConcurrentUseAsASharedSingleton() throws Exception {
        serverSocket = new ServerSocket(0);
        int port = serverSocket.getLocalPort();
        Thread acceptorThread = acceptAndCloseLoop(serverSocket);
        acceptorThread.start();

        int taskCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(taskCount);
        try {
            List<Callable<PhaseResult<TcpMetadata>>> tasks = new ArrayList<>();
            for (int i = 0; i < taskCount; i++) {
                tasks.add(() -> analyzer.analyze("127.0.0.1", port));
            }

            List<Future<PhaseResult<TcpMetadata>>> futures = executor.invokeAll(tasks);

            for (Future<PhaseResult<TcpMetadata>> future : futures) {
                PhaseResult<TcpMetadata> result = future.get();
                assertThat(result.status()).isEqualTo(PhaseResult.Status.SUCCESS);
                assertThat(result.metadata().host()).isEqualTo("127.0.0.1");
                assertThat(result.metadata().port()).isEqualTo(port);
                assertThat(result.durationMs()).isGreaterThanOrEqualTo(0);
            }
        } finally {
            executor.shutdownNow();
            serverSocket.close();
            acceptorThread.join(2000);
        }
    }

    @Test
    void closesEverySocketAcrossManySequentialConnections() throws Exception {
        // If sockets weren't being closed, file descriptors would exhaust
        // partway through this loop and later iterations would start
        // failing - this is a real leak check, not just trusting
        // try-with-resources by inspection.
        serverSocket = new ServerSocket(0);
        int port = serverSocket.getLocalPort();
        Thread acceptorThread = acceptAndCloseLoop(serverSocket);
        acceptorThread.start();

        try {
            for (int i = 0; i < 500; i++) {
                PhaseResult<TcpMetadata> result = analyzer.analyze("127.0.0.1", port);
                assertThat(result.status())
                        .as("iteration %d should still succeed if sockets are being closed, not leaked", i)
                        .isEqualTo(PhaseResult.Status.SUCCESS);
            }
        } finally {
            serverSocket.close();
            acceptorThread.join(2000);
        }
    }

    private static Thread acceptAndCloseLoop(ServerSocket serverSocket) {
        return new Thread(() -> {
            try {
                while (!serverSocket.isClosed()) {
                    Socket accepted = serverSocket.accept();
                    accepted.close();
                }
            } catch (IOException ignored) {
                // Expected once the test closes serverSocket to stop the loop.
            }
        });
    }
}
