package com.netrace.backend.analyzer;

import com.netrace.backend.config.AnalyzerProperties;
import com.netrace.backend.dto.HttpResult;
import com.netrace.backend.security.SsrfGuard;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
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
import java.util.OptionalLong;
import java.util.Set;
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
 * <b>Redirects are followed manually, one hop at a time, not by the
 * underlying HttpClient (which is configured with Redirect.NEVER).</b>
 * Before every hop - the original URL and every redirect target -
 * {@link #validateTargetIsAllowed(String)} resolves the host and
 * rejects it via {@link SsrfGuard} if any resolved address is private,
 * loopback, link-local, or otherwise reserved, and rejects any redirect
 * to a non-http(s) scheme outright. Without this, a target could pass
 * validation with a public IP and then redirect the client to an
 * internal address the automatic redirect-follower would connect to
 * without ever being checked. See docs/security.md for the full threat
 * model and this project's disclosed limitations (notably: this
 * revalidates on every hop, but does not close every DNS-rebinding
 * timing gap between validating an address and connecting to it).
 * Capped at {@code netrace.analyzer.max-redirects} hops.
 * <p>
 * ttfbMs is measured from the same starting point as totalTimeMs
 * (right before the first request is sent) to the moment
 * HttpResponse.BodyHandler.apply(ResponseInfo) is invoked for the
 * final (non-redirect) response - the point at which the JDK's HTTP
 * client has received and parsed that response's status line and
 * headers, before any body bytes are delivered to a BodySubscriber.
 * This is not the literal first physical byte on the wire (unobservable
 * without packet capture) and it is not "server processing time" in
 * isolation: like totalTimeMs, it bundles this analyzer's own
 * connection setup and request-send time - across every hop, including
 * time spent on earlier redirects - together with however long the
 * server took to start responding. downloadMs is simply
 * totalTimeMs - ttfbMs: a real derived duration from those same two
 * real timestamps, not a separately-estimated figure.
 * <p>
 * The response body is never fully buffered in memory (bytes are
 * discarded as they arrive, exactly as before), but bytes are still
 * counted as they are received. If the body exceeds
 * netrace.analyzer.max-response-size, the subscription is cancelled
 * and the download is abandoned - a server cannot force this analyzer
 * to spend unbounded bandwidth/time on an arbitrarily large or
 * never-ending response. HttpResult.bodyTruncated reports when this
 * happened; downloadMs in that case is time spent up to the cutoff,
 * not what a full download would have taken. This same size limit
 * applies to every intermediate redirect response's body too, not just
 * the final one.
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
 * <p>
 * contentLength and contentType are read straight from the response
 * headers - not derived from the body, which is never stored. Both
 * are null when the server omitted that header (routine for chunked
 * responses in particular).
 */
@Component
public class HttpAnalyzer {

    private static final Set<Integer> REDIRECT_STATUS_CODES = Set.of(301, 302, 303, 307, 308);

    /**
     * Isolates "is this resolved address blocked" so tests can supply a
     * permissive guard and exercise this class's other behavior (TTFB,
     * truncation, redirects, protocol detection, ...) against a real
     * local HttpServer, which is itself a loopback address and would
     * otherwise always be refused. Production always uses SsrfGuard,
     * via the public constructor, unless allowPrivateTargets is set.
     */
    @FunctionalInterface
    interface TargetGuard {
        boolean isBlocked(InetAddress address);
    }

    /**
     * Isolates "is this port allowed for this scheme" the same way
     * TargetGuard isolates the address check - a real local HttpServer
     * is bound to an OS-assigned port, never 80/443, so tests need a
     * permissive guard here too. Production always uses
     * isStandardPort, via the public constructor, unless
     * allowPrivateTargets is set (see the class Javadoc on why that
     * one property gates both - the two are always relaxed together in
     * practice, for the same "controlled testing/internal context"
     * reason).
     */
    @FunctionalInterface
    interface PortGuard {
        boolean isAllowed(String scheme, int port);
    }

    private final HttpClient httpClient;
    private final Duration requestTimeout;
    private final long maxResponseBytes;
    private final int maxRedirects;
    private final TargetGuard targetGuard;
    private final PortGuard portGuard;

    @Autowired
    public HttpAnalyzer(HttpClient httpClient, AnalyzerProperties analyzerProperties) {
        this(httpClient, analyzerProperties,
                analyzerProperties.allowPrivateTargets() ? address -> false : SsrfGuard::isBlocked,
                analyzerProperties.allowPrivateTargets() ? (scheme, port) -> true : HttpAnalyzer::isStandardPort);
    }

    HttpAnalyzer(HttpClient httpClient, AnalyzerProperties analyzerProperties, TargetGuard targetGuard,
            PortGuard portGuard) {
        long maxResponseBytes = analyzerProperties.maxResponseSize().toBytes();
        if (maxResponseBytes <= 0) {
            throw new IllegalStateException(
                    "netrace.analyzer.max-response-size must be positive, was "
                            + analyzerProperties.maxResponseSize());
        }
        int maxRedirects = analyzerProperties.maxRedirects();
        if (maxRedirects < 0) {
            throw new IllegalStateException(
                    "netrace.analyzer.max-redirects must not be negative, was " + maxRedirects);
        }
        this.httpClient = httpClient;
        this.requestTimeout = analyzerProperties.requestTimeout();
        this.maxResponseBytes = maxResponseBytes;
        this.maxRedirects = maxRedirects;
        this.targetGuard = targetGuard;
        this.portGuard = portGuard;
    }

    private static boolean isStandardPort(String scheme, int port) {
        int standardPort = "https".equalsIgnoreCase(scheme) ? 443 : 80;
        return port == -1 || port == standardPort;
    }

    public HttpResult analyze(String url) {
        long startNanos = System.nanoTime();
        String currentUrl = url;
        int redirects = 0;

        while (true) {
            validateTargetIsAllowed(currentUrl);

            TtfbCapturingBodyHandler bodyHandler = new TtfbCapturingBodyHandler(maxResponseBytes);
            HttpResponse<Boolean> response = send(currentUrl, bodyHandler);

            if (REDIRECT_STATUS_CODES.contains(response.statusCode())) {
                String hopUrl = currentUrl;
                String location = response.headers().firstValue("location")
                        .orElseThrow(() -> new AnalysisException(AnalysisException.Reason.INVALID_RESPONSE,
                                "Redirect from " + hopUrl + " had no Location header", null));
                redirects++;
                if (redirects > maxRedirects) {
                    throw new AnalysisException(AnalysisException.Reason.INVALID_RESPONSE,
                            "Too many redirects starting from " + url + " (limit " + maxRedirects + ")", null);
                }
                currentUrl = URI.create(currentUrl).resolve(location).toString();
                continue;
            }

            long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;
            long ttfbMs = (bodyHandler.firstByteNanos() - startNanos) / 1_000_000;
            long downloadMs = elapsedMs - ttfbMs;
            boolean bodyTruncated = Boolean.TRUE.equals(response.body());
            String protocol = protocolName(response.version());
            OptionalLong contentLengthHeader = response.headers().firstValueAsLong("content-length");
            Long contentLength = contentLengthHeader.isPresent() ? contentLengthHeader.getAsLong() : null;
            String contentType = response.headers().firstValue("content-type").orElse(null);

            return new HttpResult(response.uri().toString(), response.statusCode(), elapsedMs, ttfbMs, downloadMs,
                    bodyTruncated, protocol, contentLength, contentType);
        }
    }

    /**
     * Resolves the host of urlString and rejects it if the scheme
     * isn't http(s), if the port isn't that scheme's own standard port
     * (80/443), or if SsrfGuard blocks any resolved address. Run
     * before every hop - the original URL and every redirect target -
     * so a redirect can never reach an address, scheme, or port this
     * analyzer would have refused to connect to directly. UrlValidator
     * already enforces the same port rule for the original URL, but a
     * redirect target never passes through UrlValidator, so it has to
     * be re-checked here too - otherwise a redirect to an arbitrary
     * port on an otherwise-public host would turn this analyzer into a
     * generic TCP port prober (see docs/security.md). This is a fresh
     * resolution separate from the one httpClient.send() performs
     * moments later for the same hostname; see docs/security.md for
     * why that gap (a narrow DNS-rebinding window) is disclosed rather
     * than fully closed.
     */
    private void validateTargetIsAllowed(String urlString) {
        URI uri = URI.create(urlString);
        String scheme = uri.getScheme();
        if (scheme == null || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
            throw new AnalysisException(AnalysisException.Reason.BLOCKED_TARGET,
                    "Refusing to follow a redirect to an unsupported scheme: " + urlString, null);
        }
        if (!portGuard.isAllowed(scheme, uri.getPort())) {
            throw new AnalysisException(AnalysisException.Reason.BLOCKED_TARGET,
                    "Refusing to follow a redirect to a non-standard port: " + urlString, null);
        }

        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(uri.getHost());
        } catch (UnknownHostException e) {
            throw new AnalysisException(AnalysisException.Reason.DNS_FAILURE,
                    "Could not resolve host for " + urlString, e);
        }
        for (InetAddress address : addresses) {
            if (targetGuard.isBlocked(address)) {
                throw new AnalysisException(AnalysisException.Reason.BLOCKED_TARGET,
                        "Refusing to request " + urlString + ": resolves to a private or reserved address ("
                                + address.getHostAddress() + ")", null);
            }
        }
    }

    private HttpResponse<Boolean> send(String urlString, TtfbCapturingBodyHandler bodyHandler) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(urlString))
                .timeout(requestTimeout)
                .GET()
                .build();
        try {
            return httpClient.send(request, bodyHandler);
        } catch (HttpConnectTimeoutException e) {
            throw new AnalysisException(AnalysisException.Reason.TIMEOUT,
                    "Connection to " + urlString + " timed out", e);
        } catch (HttpTimeoutException e) {
            throw new AnalysisException(AnalysisException.Reason.TIMEOUT,
                    "Request to " + urlString + " timed out", e);
        } catch (UnknownHostException e) {
            throw new AnalysisException(AnalysisException.Reason.DNS_FAILURE,
                    "Could not resolve host for " + urlString, e);
        } catch (ConnectException e) {
            throw new AnalysisException(AnalysisException.Reason.CONNECTION_FAILURE,
                    "Failed to connect to " + urlString, e);
        } catch (IOException e) {
            throw new AnalysisException(AnalysisException.Reason.INVALID_RESPONSE,
                    "Invalid response from " + urlString, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AnalysisException(AnalysisException.Reason.CONNECTION_FAILURE,
                    "Interrupted while contacting " + urlString, e);
        }
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
