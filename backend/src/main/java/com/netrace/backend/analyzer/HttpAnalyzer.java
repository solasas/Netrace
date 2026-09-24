package com.netrace.backend.analyzer;

import com.netrace.backend.config.AnalyzerProperties;
import com.netrace.backend.dto.HttpResult;
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
 * elapsed time. This still performs its own internal DNS resolution as
 * part of connecting (via HttpClient) - it does not reuse DnsAnalyzer's
 * result - so totalTimeMs includes that internal resolution too, on top
 * of whatever DnsAnalyzer separately measured beforehand. TCP/TLS phase
 * timing is not broken out yet.
 */
@Component
public class HttpAnalyzer {

    private final HttpClient httpClient;
    private final Duration requestTimeout;

    public HttpAnalyzer(HttpClient httpClient, AnalyzerProperties analyzerProperties) {
        this.httpClient = httpClient;
        this.requestTimeout = analyzerProperties.requestTimeout();
    }

    public HttpResult analyze(String url) {
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
        } catch (UnknownHostException e) {
            throw new AnalysisException(AnalysisException.Reason.DNS_FAILURE,
                    "Could not resolve host for " + url, e);
        } catch (ConnectException e) {
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

        return new HttpResult(response.uri().toString(), response.statusCode(), elapsedMs);
    }
}
