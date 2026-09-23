package com.netrace.backend.analyzer;

import com.netrace.backend.dto.AnalyzeResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;

/**
 * Makes a real HTTP(S) request to an already-validated URL and reports
 * the observed status code, final URL (after redirects), and total
 * elapsed time. DNS/TCP/TLS phase timing is not broken out yet.
 */
@Component
public class HttpAnalyzer {

    private final HttpClient httpClient;
    private final Duration requestTimeout;

    public HttpAnalyzer(
            HttpClient httpClient,
            @Value("${netrace.analyzer.request-timeout-ms:10000}") long requestTimeoutMs) {
        this.httpClient = httpClient;
        this.requestTimeout = Duration.ofMillis(requestTimeoutMs);
    }

    public AnalyzeResponse analyze(String url) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(requestTimeout)
                .GET()
                .build();

        long startNanos = System.nanoTime();
        HttpResponse<Void> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (HttpConnectTimeoutException e) {
            throw new AnalysisException(AnalysisException.Reason.TIMEOUT,
                    "Connection to " + url + " timed out", e);
        } catch (HttpTimeoutException e) {
            throw new AnalysisException(AnalysisException.Reason.TIMEOUT,
                    "Request to " + url + " timed out", e);
        } catch (ConnectException | UnknownHostException e) {
            throw new AnalysisException(AnalysisException.Reason.CONNECTION_FAILURE,
                    "Failed to connect to " + url, e);
        } catch (IOException e) {
            throw new AnalysisException(AnalysisException.Reason.INVALID_RESPONSE,
                    "Invalid response from " + url, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AnalysisException(AnalysisException.Reason.CONNECTION_FAILURE,
                    "Interrupted while contacting " + url, e);
        }
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;

        return new AnalyzeResponse(response.uri().toString(), response.statusCode(), elapsedMs);
    }
}
