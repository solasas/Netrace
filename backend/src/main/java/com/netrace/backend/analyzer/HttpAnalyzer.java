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
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Makes a real HTTP(S) request to an already-validated URL and reports
 * the observed status code, final URL (after redirects), total elapsed
 * time, time to first byte (TTFB), and download time. This still
 * performs its own internal DNS resolution as part of connecting (via
 * HttpClient) - it does not reuse DnsAnalyzer's result - so
 * totalTimeMs includes that internal resolution too, on top of
 * whatever DnsAnalyzer separately measured beforehand. TCP/TLS phase
 * timing is not broken out here either.
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
 * intermediate hop's. downloadMs is simply totalTimeMs - ttfbMs: a
 * real derived duration from those same two real timestamps, not a
 * separately-estimated figure.
 * <p>
 * The response body is never fully buffered in memory (bytes are
 * discarded as they arrive, exactly as before), but bytes are still
 * counted as they are received. If the body exceeds
 * netrace.analyzer.max-response-size, the subscription is cancelled
 * and the download is abandoned - a server cannot force this analyzer
 * to spend unbounded bandwidth/time on an arbitrarily large or
 * never-ending response. HttpResult.bodyTruncated reports when this
 * happened; downloadMs in that case is time spent up to the cutoff,
 * not what a full download would have taken.
 * <p>
 * protocol reports response.version() - the HTTP version actually
 * negotiated for this specific response, not merely requested.
 * HttpClientConfig doesn't pin a version, so HttpClient's own default
 * preference applies: it prefers HTTP/2 and transparently falls back
 * to HTTP/1.1 per connection depending on what the server supports.
 * java.net.http.HttpClient.Version has exactly two values,
 * HTTP_1_1 and HTTP_2 - there is no HTTP/3 support in this API at
 * all, so none is claimed here; the mapping below is an exhaustive
 * switch with no default case specifically so that if a future JDK
 * ever adds a third value, this stops compiling instead of silently
 * mis-reporting it.
 */
@Component
public class HttpAnalyzer {

    private final HttpClient httpClient;
    private final Duration requestTimeout;
    private final long maxResponseBytes;

    public HttpAnalyzer(HttpClient httpClient, AnalyzerProperties analyzerProperties) {
        long maxResponseBytes = analyzerProperties.maxResponseSize().toBytes();
        if (maxResponseBytes <= 0) {
            throw new IllegalStateException(
                    "netrace.analyzer.max-response-size must be positive, was "
                            + analyzerProperties.maxResponseSize());
        }
        this.httpClient = httpClient;
        this.requestTimeout = analyzerProperties.requestTimeout();
        this.maxResponseBytes = maxResponseBytes;
    }

    public HttpResult analyze(String url) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(requestTimeout)
                .GET()
                .build();

        TtfbCapturingBodyHandler bodyHandler = new TtfbCapturingBodyHandler(maxResponseBytes);
        long startNanos = System.nanoTime();
        HttpResponse<Boolean> response;
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
        long downloadMs = elapsedMs - ttfbMs;
        boolean bodyTruncated = Boolean.TRUE.equals(response.body());
        String protocol = protocolName(response.version());

        return new HttpResult(response.uri().toString(), response.statusCode(), elapsedMs, ttfbMs, downloadMs,
                bodyTruncated, protocol);
    }

    private static String protocolName(HttpClient.Version version) {
        return switch (version) {
            case HTTP_1_1 -> "HTTP/1.1";
            case HTTP_2 -> "HTTP/2";
        };
    }

    /**
     * Times the moment the response status line and headers become
     * available, then hands off to a size-limited discarding
     * subscriber for the body.
     */
    private static final class TtfbCapturingBodyHandler implements HttpResponse.BodyHandler<Boolean> {

        private final long maxBytes;
        private long firstByteNanos;

        TtfbCapturingBodyHandler(long maxBytes) {
            this.maxBytes = maxBytes;
        }

        @Override
        public HttpResponse.BodySubscriber<Boolean> apply(HttpResponse.ResponseInfo responseInfo) {
            firstByteNanos = System.nanoTime();
            return new SizeLimitedDiscardingBodySubscriber(maxBytes);
        }

        long firstByteNanos() {
            return firstByteNanos;
        }
    }

    /**
     * Reads and discards body bytes without ever buffering them, the
     * same as HttpResponse.BodySubscribers.discarding(), but cancels
     * the subscription once more than maxBytes have been received
     * rather than reading an unbounded or arbitrarily large body to
     * completion. getBody() resolves to true if the body was
     * truncated this way, false if it was fully consumed within the
     * limit.
     */
    private static final class SizeLimitedDiscardingBodySubscriber
            implements HttpResponse.BodySubscriber<Boolean> {

        private final long maxBytes;
        private final AtomicLong received = new AtomicLong();
        private final CompletableFuture<Boolean> result = new CompletableFuture<>();
        private volatile Flow.Subscription subscription;

        SizeLimitedDiscardingBodySubscriber(long maxBytes) {
            this.maxBytes = maxBytes;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(List<ByteBuffer> buffers) {
            long total = received.addAndGet(buffers.stream().mapToLong(ByteBuffer::remaining).sum());
            if (total > maxBytes) {
                subscription.cancel();
                result.complete(true);
            }
        }

        @Override
        public void onError(Throwable throwable) {
            result.completeExceptionally(throwable);
        }

        @Override
        public void onComplete() {
            result.complete(false);
        }

        @Override
        public CompletionStage<Boolean> getBody() {
            return result;
        }
    }
}
