package com.netrace.backend.analyzer;

import com.netrace.backend.config.AnalyzerProperties;
import com.netrace.backend.dto.PhaseResult;
import com.netrace.backend.dto.TcpMetadata;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

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
 * Unlike DnsAnalyzer/HttpAnalyzer, a connection failure here is
 * reported as a normal PhaseResult with Status.FAILURE (still carrying
 * a real elapsed duration) rather than thrown as an AnalysisException:
 * a refused or timed-out connection is itself a meaningful, directly
 * measured outcome of this phase, not a condition that prevents any
 * measurement from being taken.
 */
@Component
public class TcpAnalyzer {

    public static final String PHASE = "TCP";

    private final AnalyzerProperties analyzerProperties;

    public TcpAnalyzer(AnalyzerProperties analyzerProperties) {
        this.analyzerProperties = analyzerProperties;
    }

    public PhaseResult<TcpMetadata> analyze(String host, int port) {
        int timeoutMs = (int) analyzerProperties.connectTimeout().toMillis();
        TcpMetadata metadata = new TcpMetadata(host, port);

        long startNanos = System.nanoTime();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            return PhaseResult.success(PHASE, elapsedMs(startNanos), metadata);
        } catch (IOException e) {
            return PhaseResult.failure(PHASE, elapsedMs(startNanos), metadata);
        }
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
