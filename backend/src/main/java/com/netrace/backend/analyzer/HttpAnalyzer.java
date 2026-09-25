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
 * the observed status code, final URL (after redirects), total elapsed
 * time, and time to first byte (TTFB). This still performs its own
 * internal DNS resolution as part of connecting (via HttpClient) - it
 * does not reuse DnsAnalyzer's result - so totalTimeMs includes that
 * internal resolution too, on top of whatever DnsAnalyzer separately
 * measured beforehand. TCP/TLS phase timing is not broken out here
 * either.
 * <p>
 * ttfbMs is measured from the same starting point as totalTimeMs
 * (right before the request is sent) to the moment
 * HttpResponse.BodyHandler.apply(ResponseInfo) is invoked - the point
 * at which the JDK's HTTP client has received and parsed the response
 * status line and headers, before any body bytes are delivered to a
 * BodySubscriber. This is not the literal first physical byte on the
 * wire (unobservable without packet capture) and it is not "server
 * processing time" in isolation: like totalTimeMs, it bundles this
 * analyzer's own connection setup and request-send time together with
 * however long the server took to start responding. For a redirected
 * request it reflects the final response's headers, not an
 * intermediate hop's.
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

        TtfbCapturingBodyHandler bodyHandler = new TtfbCapturingBodyHandler();
        long startNanos = System.nanoTime();
        HttpResponse<Void> response;
        try {
            response = httpClient.send(request, bodyHandler);
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
        long ttfbMs = (bodyHandler.firstByteNanos() - startNanos) / 1_000_000;

        return new HttpResult(response.uri().toString(), response.statusCode(), elapsedMs, ttfbMs);
    }

    /**
     * Times the moment the response status line and headers become
     * available, then discards the body exactly like the previous
     * BodyHandlers.discarding() did - this class only adds a timestamp
     * around that existing behavior.
     */
    private static final class TtfbCapturingBodyHandler implements HttpResponse.BodyHandler<Void> {

        private long firstByteNanos;

        @Override
        public HttpResponse.BodySubscriber<Void> apply(HttpResponse.ResponseInfo responseInfo) {
            firstByteNanos = System.nanoTime();
            return HttpResponse.BodySubscribers.discarding();
        }

        long firstByteNanos() {
            return firstByteNanos;
        }
    }
}
