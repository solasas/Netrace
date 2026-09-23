package com.netrace.backend.analyzer;

import com.netrace.backend.dto.AnalyzeResponse;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

        AnalyzeResponse response = newAnalyzer().analyze(url);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.url()).isEqualTo(url);
        assertThat(response.totalTimeMs()).isGreaterThanOrEqualTo(0);
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

        AnalyzeResponse response = newAnalyzer().analyze("http://localhost:" + port + "/redirect");

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

        AnalyzeResponse response = newAnalyzer().analyze(url);

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
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        return new HttpAnalyzer(client, requestTimeout.toMillis());
    }
}
