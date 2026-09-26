package com.netrace.backend.analyzer;

import com.netrace.backend.config.AnalyzerProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * HttpAnalyzer-level SSRF protection tests. SsrfGuard's own IP
 * classification is tested exhaustively in SsrfGuardTest; this class
 * proves HttpAnalyzer actually wires that guard into every request it
 * makes, including every redirect hop, not just the first URL it is
 * given - and does so with the real, default (production) guard where
 * a real local server can prove it, or an injectable TargetGuard where
 * distinguishing "the initial hop" from "the redirect target" by real
 * IP address alone isn't reliably reproducible in a sandboxed test
 * environment (see docs/security.md).
 */
class HttpAnalyzerSsrfTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void blocksARequestToALoopbackTargetWithTheRealGuard() {
        // No server needed: with the real, default guard (the public
        // 2-arg constructor, exactly as Spring wires it in production)
        // this must be refused before any connection is attempted.
        HttpAnalyzer analyzer = realAnalyzer();

        assertThatThrownBy(() -> analyzer.analyze("http://127.0.0.1/"))
                .isInstanceOf(AnalysisException.class)
                .extracting(e -> ((AnalysisException) e).reason())
                .isEqualTo(AnalysisException.Reason.BLOCKED_TARGET);
    }

    @Test
    void blocksTheCloudMetadataAddressWithTheRealGuard() {
        HttpAnalyzer analyzer = realAnalyzer();

        assertThatThrownBy(() -> analyzer.analyze("http://169.254.169.254/latest/meta-data/"))
                .isInstanceOf(AnalysisException.class)
                .extracting(e -> ((AnalysisException) e).reason())
                .isEqualTo(AnalysisException.Reason.BLOCKED_TARGET);
    }

    @Test
    void blocksARedirectTargetEvenWhenTheInitialRequestPassed() throws IOException {
        // A real local server's initial hop and its own redirect target
        // are the same loopback address, so a real IP-based guard can't
        // distinguish "allowed initial hop" from "blocked redirect
        // target" here. A guard that blocks every call after the first
        // proves the same mechanism instead: the redirect hop gets its
        // own, separate validation call, and a rejection there stops
        // the analyzer exactly like a rejection on the first hop would.
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        int port = server.getAddress().getPort();
        String targetUrl = "http://localhost:" + port + "/target";
        server.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().add("Location", targetUrl);
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/target", exchange -> {
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();

        AtomicInteger validationCalls = new AtomicInteger();
        HttpAnalyzer analyzer = new HttpAnalyzer(neverRedirectingClient(),
                new AnalyzerProperties(Duration.ofSeconds(2), Duration.ofSeconds(2)),
                address -> validationCalls.getAndIncrement() > 0, (scheme, p) -> true);

        assertThatThrownBy(() -> analyzer.analyze("http://localhost:" + port + "/redirect"))
                .isInstanceOf(AnalysisException.class)
                .extracting(e -> ((AnalysisException) e).reason())
                .isEqualTo(AnalysisException.Reason.BLOCKED_TARGET);
        assertThat(validationCalls.get())
                .as("the redirect target's address must be validated separately from the initial hop's")
                .isGreaterThan(1);
    }

    @Test
    void blocksARedirectToANonHttpScheme() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().add("Location", "file:///etc/passwd");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.start();
        String url = "http://localhost:" + server.getAddress().getPort() + "/redirect";

        HttpAnalyzer analyzer = new HttpAnalyzer(neverRedirectingClient(),
                new AnalyzerProperties(Duration.ofSeconds(2), Duration.ofSeconds(2)),
                address -> false, (scheme, port) -> true);

        assertThatThrownBy(() -> analyzer.analyze(url))
                .isInstanceOf(AnalysisException.class)
                .extracting(e -> ((AnalysisException) e).reason())
                .isEqualTo(AnalysisException.Reason.BLOCKED_TARGET);
    }

    @Test
    void blocksARedirectToANonStandardPort() throws IOException {
        // Proves the gap a redirect could otherwise use to bypass port
        // restriction: the initial URL here uses the server's real
        // (non-standard, OS-assigned) port, allowed through via a
        // permissive port guard, exactly like the other redirect tests
        // above - but the redirect target names a fixed, different,
        // very much non-standard port (6379, a real service's default
        // port), which a strict port guard must still reject on that
        // second, separate validation call.
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().add("Location", "http://localhost:6379/");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.start();
        String url = "http://localhost:" + server.getAddress().getPort() + "/redirect";

        HttpAnalyzer analyzer = new HttpAnalyzer(neverRedirectingClient(),
                new AnalyzerProperties(Duration.ofSeconds(2), Duration.ofSeconds(2)),
                address -> false, (scheme, port) -> port == server.getAddress().getPort());

        assertThatThrownBy(() -> analyzer.analyze(url))
                .isInstanceOf(AnalysisException.class)
                .extracting(e -> ((AnalysisException) e).reason())
                .isEqualTo(AnalysisException.Reason.BLOCKED_TARGET);
    }

    @Test
    void throwsWhenRedirectsExceedTheConfiguredLimit() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        int port = server.getAddress().getPort();
        server.createContext("/loop", exchange -> {
            exchange.getResponseHeaders().add("Location", "http://localhost:" + port + "/loop");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.start();

        HttpAnalyzer analyzer = new HttpAnalyzer(neverRedirectingClient(),
                new AnalyzerProperties(Duration.ofSeconds(2), Duration.ofSeconds(2), DataSize.ofMegabytes(10), 2, false),
                address -> false, (scheme, p) -> true);

        assertThatThrownBy(() -> analyzer.analyze("http://localhost:" + port + "/loop"))
                .isInstanceOf(AnalysisException.class)
                .extracting(e -> ((AnalysisException) e).reason())
                .isEqualTo(AnalysisException.Reason.INVALID_RESPONSE);
    }

    @Test
    void allowPrivateTargetsBypassesTheGuardForARealLoopbackRequest() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/ok", exchange -> {
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        String url = "http://localhost:" + server.getAddress().getPort() + "/ok";

        HttpAnalyzer analyzer = new HttpAnalyzer(neverRedirectingClient(),
                new AnalyzerProperties(Duration.ofSeconds(2), Duration.ofSeconds(2), DataSize.ofMegabytes(10), 5, true));

        assertThat(analyzer.analyze(url).statusCode()).isEqualTo(200);
    }

    private static HttpAnalyzer realAnalyzer() {
        return new HttpAnalyzer(neverRedirectingClient(),
                new AnalyzerProperties(Duration.ofSeconds(2), Duration.ofSeconds(2)));
    }

    private static HttpClient neverRedirectingClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }
}
