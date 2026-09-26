package com.netrace.backend.analyzer;

import com.netrace.backend.config.AnalyzerProperties;
import com.netrace.backend.dto.HttpResult;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Every test here targets a real local HttpServer on localhost, bound
 * to an OS-assigned port - both the real, production SsrfGuard-backed
 * target guard (loopback is always refused) and the real port guard
 * (that assigned port is never 80/443) would refuse to connect to it.
 * newAnalyzer()/newAnalyzerWithMaxResponseSize() below build analyzers
 * with permissive guards for both so these tests can keep exercising
 * everything else (TTFB, truncation, redirects, protocol detection,
 * error mapping...) against a real, fully controlled local server. The
 * real guards' blocking behavior is tested separately, deliberately
 * against localhost and non-standard ports, in HttpAnalyzerSsrfTest.
 */
class HttpAnalyzerTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void reportsStatusCodeFinalUrlAndElapsedTimeForASuccessfulRequest() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/ok", exchange -> {
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        String url = "http://localhost:" + server.getAddress().getPort() + "/ok";

        HttpResult response = newAnalyzer().analyze(url);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.url()).isEqualTo(url);
        assertThat(response.totalTimeMs()).isGreaterThanOrEqualTo(0);
        assertThat(response.ttfbMs()).isGreaterThanOrEqualTo(0);
        assertThat(response.ttfbMs()).isLessThanOrEqualTo(response.totalTimeMs());
        assertThat(response.ttfbMs() + response.downloadMs())
                .as("totalTimeMs is the only field ttfbMs/downloadMs are guaranteed to sum to")
                .isEqualTo(response.totalTimeMs());
        assertThat(response.protocol()).isEqualTo("HTTP/1.1");
    }

    @Test
    void detectsHttp2ForARealServerThatNegotiatesIt() throws IOException {
        // Real, not mocked: com.sun.net.httpserver.HttpServer (used
        // everywhere else in this class) only ever speaks HTTP/1.1, so a
        // real HTTP/2 negotiation needs a real HTTP/2 server. example.com
        // is Cloudflare-fronted and supports HTTP/2, and is the same
        // stable, IANA-reserved target already used elsewhere in this
        // suite for real-network tests.
        HttpResult response = newAnalyzer().analyze("https://example.com");

        assertThat(response.protocol()).isEqualTo("HTTP/2");
    }

    @Test
    void capturesContentTypeContentLengthAndTheFinalUrlAfterARedirect() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        int port = server.getAddress().getPort();
        String targetUrl = "http://localhost:" + port + "/target";
        byte[] body = "{\"hello\":\"world\"}".getBytes(StandardCharsets.UTF_8);

        server.createContext("/target", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().add("Location", targetUrl);
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.start();

        HttpResult response = newAnalyzer().analyze("http://localhost:" + port + "/redirect");

        assertThat(response.url()).isEqualTo(targetUrl);
        assertThat(response.contentType()).isEqualTo("application/json");
        assertThat(response.contentLength()).isEqualTo((long) body.length);
    }

    @Test
    void reportsNullContentLengthWhenTheServerOmitsItRatherThanZero() throws IOException {
        // Chunked transfer-encoding routinely omits Content-Length; this
        // must be represented as "unknown" (null), not "zero bytes".
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        byte[] body = "a chunked body with no declared content-length".getBytes(StandardCharsets.US_ASCII);
        server.createContext("/chunked", exchange -> {
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        String url = "http://localhost:" + server.getAddress().getPort() + "/chunked";

        HttpResult response = newAnalyzer().analyze(url);

        assertThat(response.contentLength()).isNull();
    }

    @Test
    void reportsNullContentTypeWhenTheServerOmitsIt() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/no-content-type", exchange -> {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.start();
        String url = "http://localhost:" + server.getAddress().getPort() + "/no-content-type";

        HttpResult response = newAnalyzer().analyze(url);

        assertThat(response.contentType()).isNull();
    }

    @Test
    void javaHttpClientHasNoHttp3SupportToDetect() {
        // Locks in the actual current JDK reality as a verifiable test,
        // not just a claim in prose: if a future JDK ever adds HTTP/3 to
        // this enum, this test breaks, forcing a deliberate decision
        // about how to represent it rather than silently misreporting.
        assertThat(HttpClient.Version.values())
                .containsExactly(HttpClient.Version.HTTP_1_1, HttpClient.Version.HTTP_2);
    }

    @Test
    void measuresTtfbSeparatelyFromDownloadTimeRatherThanFakingItAsTheTotal() throws IOException {
        // The server sends headers immediately, then deliberately delays
        // before writing the body. If ttfbMs were just an alias for
        // totalTimeMs (faked), it would also include that delay; a real
        // TTFB measurement must be captured before the delay happens.
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        byte[] body = "this body arrives well after the headers do".getBytes(StandardCharsets.US_ASCII);
        server.createContext("/slow-body", exchange -> {
            exchange.sendResponseHeaders(200, body.length);
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        String url = "http://localhost:" + server.getAddress().getPort() + "/slow-body";

        HttpResult response = newAnalyzer().analyze(url);

        assertThat(response.ttfbMs()).isLessThan(response.totalTimeMs());
        assertThat(response.totalTimeMs() - response.ttfbMs())
                .as("the gap between ttfb and total should reflect the body delay, not be ~0")
                .isGreaterThanOrEqualTo(200);
        assertThat(response.ttfbMs() + response.downloadMs()).isEqualTo(response.totalTimeMs());
    }

    @Test
    void doesNotTruncateAResponseWithinTheSizeLimit() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        byte[] body = "a perfectly ordinary, small response body".getBytes(StandardCharsets.US_ASCII);
        server.createContext("/small", exchange -> {
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        String url = "http://localhost:" + server.getAddress().getPort() + "/small";

        HttpResult response = newAnalyzer().analyze(url);

        assertThat(response.bodyTruncated()).isFalse();
    }

    @Test
    void abandonsTheDownloadAndReportsTruncationWhenTheBodyExceedsTheMaxResponseSize() throws IOException {
        // Do not download unbounded response bodies: the server keeps
        // writing well past the configured 1KB limit (up to ~4MB, via
        // chunked encoding so no Content-Length caps it upfront); the
        // analyzer must cancel and return quickly rather than reading it
        // all just to discard it.
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/huge", exchange -> {
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream out = exchange.getResponseBody()) {
                byte[] chunk = new byte[8192];
                Arrays.fill(chunk, (byte) 'x');
                for (int i = 0; i < 500; i++) {
                    out.write(chunk);
                    out.flush();
                }
            } catch (IOException ignored) {
                // Expected once the client cancels after exceeding the limit.
            }
        });
        server.start();
        String url = "http://localhost:" + server.getAddress().getPort() + "/huge";

        HttpResult response = newAnalyzerWithMaxResponseSize(DataSize.ofKilobytes(1)).analyze(url);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.bodyTruncated()).isTrue();
        assertThat(response.totalTimeMs())
                .as("should abandon the download quickly rather than waiting for the full ~4MB body")
                .isLessThan(5000);
        assertThat(response.ttfbMs() + response.downloadMs())
                .as("the invariant must hold even when the download is abandoned early, not just on a full download")
                .isEqualTo(response.totalTimeMs());
    }

    @Test
    void constructorRejectsAZeroMaxResponseSize() {
        HttpClient client = HttpClient.newBuilder().build();

        assertThatThrownBy(() -> new HttpAnalyzer(client,
                new AnalyzerProperties(Duration.ofSeconds(2), Duration.ofSeconds(2), DataSize.ofBytes(0))))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void reportsTheFinalUrlAfterFollowingARedirect() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        int port = server.getAddress().getPort();
        String targetUrl = "http://localhost:" + port + "/target";

        server.createContext("/target", exchange -> {
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().add("Location", targetUrl);
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.start();

        HttpResult response = newAnalyzer().analyze("http://localhost:" + port + "/redirect");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.url()).isEqualTo(targetUrl);
    }

    @Test
    void reportsHttpErrorStatusCodesWithoutThrowing() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/missing", exchange -> {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });
        server.start();
        String url = "http://localhost:" + server.getAddress().getPort() + "/missing";

        HttpResult response = newAnalyzer().analyze(url);

        assertThat(response.statusCode()).isEqualTo(404);
    }

    @Test
    void throwsAConnectionFailureWhenNothingIsListening() throws IOException {
        int freePort;
        try (ServerSocket probe = new ServerSocket(0)) {
            freePort = probe.getLocalPort();
        }

        assertThatThrownBy(() -> newAnalyzer().analyze("http://localhost:" + freePort + "/"))
                .isInstanceOf(AnalysisException.class)
                .extracting(e -> ((AnalysisException) e).reason())
                .isEqualTo(AnalysisException.Reason.CONNECTION_FAILURE);
    }

    @Test
    void throwsADnsFailureWhenTheHostCannotBeResolved() throws IOException, InterruptedException {
        // Uses example.com - a real, reliably-resolvable hostname - so
        // the pre-flight SSRF resolution check (which does its own real
        // DNS lookup before httpClient.send() is ever called) succeeds
        // deterministically, and control reaches the mocked client
        // below. That mock is what throws UnknownHostException here,
        // isolating just the mapping from that exception to DNS_FAILURE
        // without depending on any environment's real DNS failure
        // behavior for an unresolvable name (found unreliable earlier
        // in this project for reserved/unresolvable TLDs).
        HttpClient mockClient = mock(HttpClient.class);
        when(mockClient.send(any(), any())).thenThrow(new UnknownHostException("simulated failure"));
        HttpAnalyzer analyzer = new HttpAnalyzer(mockClient,
                new AnalyzerProperties(Duration.ofSeconds(2), Duration.ofSeconds(2)),
                address -> false, (scheme, port) -> true);

        assertThatThrownBy(() -> analyzer.analyze("https://example.com/"))
                .isInstanceOf(AnalysisException.class)
                .extracting(e -> ((AnalysisException) e).reason())
                .isEqualTo(AnalysisException.Reason.DNS_FAILURE);
    }

    @Test
    void throwsATimeoutWhenTheServerIsSlowerThanTheRequestTimeout() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/slow", exchange -> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            try {
                exchange.sendResponseHeaders(200, -1);
            } finally {
                exchange.close();
            }
        });
        server.start();
        String url = "http://localhost:" + server.getAddress().getPort() + "/slow";

        assertThatThrownBy(() -> newAnalyzer(Duration.ofMillis(100)).analyze(url))
                .isInstanceOf(AnalysisException.class)
                .extracting(e -> ((AnalysisException) e).reason())
                .isEqualTo(AnalysisException.Reason.TIMEOUT);
    }

    @Test
    void throwsAnInvalidResponseForAMalformedHttpResponse() throws IOException, InterruptedException {
        try (ServerSocket rawServer = new ServerSocket(0, 1, InetAddress.getByName("localhost"))) {
            int port = rawServer.getLocalPort();
            Thread serverThread = new Thread(() -> {
                try (Socket socket = rawServer.accept();
                     OutputStream out = socket.getOutputStream()) {
                    out.write("not a valid http response\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
                    out.flush();
                } catch (IOException ignored) {
                    // best-effort test server
                }
            });
            serverThread.start();
            String url = "http://localhost:" + port + "/";

            assertThatThrownBy(() -> newAnalyzer().analyze(url))
                    .isInstanceOf(AnalysisException.class)
                    .extracting(e -> ((AnalysisException) e).reason())
                    .isEqualTo(AnalysisException.Reason.INVALID_RESPONSE);

            serverThread.join(2000);
        }
    }

    private HttpAnalyzer newAnalyzer() {
        return newAnalyzer(Duration.ofSeconds(2));
    }

    private HttpAnalyzer newAnalyzer(Duration requestTimeout) {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        return new HttpAnalyzer(client, new AnalyzerProperties(Duration.ofSeconds(2), requestTimeout),
                address -> false, (scheme, port) -> true);
    }

    private HttpAnalyzer newAnalyzerWithMaxResponseSize(DataSize maxResponseSize) {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        return new HttpAnalyzer(client,
                new AnalyzerProperties(Duration.ofSeconds(2), Duration.ofSeconds(2), maxResponseSize),
                address -> false, (scheme, port) -> true);
    }
}
