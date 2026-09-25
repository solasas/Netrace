package com.netrace.backend.analyzer;

import com.netrace.backend.config.AnalyzerProperties;
import com.netrace.backend.dto.PhaseResult;
import com.netrace.backend.dto.TcpFailureReason;
import com.netrace.backend.dto.TcpMetadata;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.NoRouteToHostException;
import java.net.Socket;
import java.net.SocketTimeoutException;

/**
 * Opens a dedicated, fresh TCP connection (never reused or pooled) to
 * a specific host:port and measures how long connection establishment
 * took. host is expected to already be a resolved IP literal - passing
 * a hostname would make Socket.connect() resolve it too, quietly
 * folding DNS time into what is meant to be a pure TCP measurement.
 * <p>
 * This is wall-clock time around the blocking Socket.connect() call:
 * what the JVM observes from issuing the OS connect() call to it
 * returning, successfully or not. There is no visibility into
 * individual SYN/SYN-ACK/ACK packets, retransmissions, or whether a
 * slow result came from real network latency, packet loss and OS-level
 * retry, or the destination host's listen backlog - only the
 * caller-visible duration.
 * <p>
 * A connection failure is reported as a normal PhaseResult with
 * Status.FAILURE (still carrying a real elapsed duration) and a
 * TcpFailureReason distinguishing connection refused, timeout, an
 * unreachable host, or anything else - rather than thrown as an
 * AnalysisException, since a refused or timed-out connection is itself
 * a meaningful, directly measured outcome of this phase.
 * <p>
 * An out-of-range destination port is different: no connection is ever
 * attempted, so there is no meaningful duration to report. That case
 * throws IllegalArgumentException immediately instead.
 * <p>
 * Resource lifecycle: the real Socket is opened and closed entirely
 * within one try-with-resources block (see connectWithRealSocket), so
 * it is closed on every path - success, any IOException, including a
 * timeout - before analyze() ever sees control return.
 * <p>
 * Thread safety: this class holds no mutable state (timeoutMs and
 * connector are both final, set once at construction); every value
 * used during a call to analyze() is local to that call. It is safe to
 * use concurrently as the Spring singleton it is.
 * <p>
 * Timeout behavior: java.net.Socket.connect(address, timeout) treats a
 * timeout of exactly 0 as "block forever," not "fail immediately" -
 * the opposite of what a 0 setting would suggest. To make sure a
 * misconfigured netrace.analyzer.connect-timeout of 0 (or negative)
 * can never cause a request thread to hang indefinitely, the
 * configured timeout is validated once at construction, not
 * per-request, so a bad value fails loudly at startup instead of
 * silently on the first real request.
 */
@Component
public class TcpAnalyzer {

    public static final String PHASE = "TCP";

    @FunctionalInterface
    interface Connector {
        void connect(String host, int port, int timeoutMs) throws IOException;
    }

    private final int timeoutMs;
    private final Connector connector;

    @Autowired
    public TcpAnalyzer(AnalyzerProperties analyzerProperties) {
        this(analyzerProperties, TcpAnalyzer::connectWithRealSocket);
    }

    TcpAnalyzer(AnalyzerProperties analyzerProperties, Connector connector) {
        long configuredTimeoutMs = analyzerProperties.connectTimeout().toMillis();
        if (configuredTimeoutMs <= 0) {
            throw new IllegalStateException(
                    "netrace.analyzer.connect-timeout must be positive, was "
                            + analyzerProperties.connectTimeout());
        }
        this.timeoutMs = Math.toIntExact(configuredTimeoutMs);
        this.connector = connector;
    }

    private static void connectWithRealSocket(String host, int port, int timeoutMs) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
        }
    }

    public PhaseResult<TcpMetadata> analyze(String host, int port) {
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("Invalid destination port: " + port);
        }

        long startNanos = System.nanoTime();
        try {
            connector.connect(host, port, timeoutMs);
            return PhaseResult.success(PHASE, elapsedMs(startNanos), new TcpMetadata(host, port));
        } catch (SocketTimeoutException e) {
            return failure(host, port, startNanos, TcpFailureReason.TIMEOUT);
        } catch (ConnectException e) {
            return failure(host, port, startNanos, TcpFailureReason.CONNECTION_REFUSED);
        } catch (NoRouteToHostException e) {
            return failure(host, port, startNanos, TcpFailureReason.UNREACHABLE);
        } catch (IOException e) {
            return failure(host, port, startNanos, TcpFailureReason.UNKNOWN);
        }
    }

    private PhaseResult<TcpMetadata> failure(String host, int port, long startNanos, TcpFailureReason reason) {
        return PhaseResult.failure(PHASE, elapsedMs(startNanos), new TcpMetadata(host, port, reason));
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
