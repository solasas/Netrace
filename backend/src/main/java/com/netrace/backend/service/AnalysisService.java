package com.netrace.backend.service;

import com.netrace.backend.analyzer.AnalysisException;
import com.netrace.backend.analyzer.DnsAnalyzer;
import com.netrace.backend.analyzer.HttpAnalyzer;
import com.netrace.backend.analyzer.TcpAnalyzer;
import com.netrace.backend.analyzer.TlsAnalyzer;
import com.netrace.backend.config.AnalyzerProperties;
import com.netrace.backend.dto.AnalyzeRequest;
import com.netrace.backend.dto.AnalyzeResponse;
import com.netrace.backend.dto.DnsMetadata;
import com.netrace.backend.dto.DnsResult;
import com.netrace.backend.dto.HttpResult;
import com.netrace.backend.dto.PhaseResult;
import com.netrace.backend.dto.Probes;
import com.netrace.backend.dto.TcpFailureReason;
import com.netrace.backend.dto.TcpMetadata;
import com.netrace.backend.dto.TcpResult;
import com.netrace.backend.dto.TlsFailureReason;
import com.netrace.backend.dto.TlsMetadata;
import com.netrace.backend.dto.TlsResult;
import com.netrace.backend.security.SsrfGuard;
import com.netrace.backend.validation.UrlValidator;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;

/**
 * Coordinates a single analysis end to end as a fixed sequence of
 * phases, each its own private method with one responsibility - call
 * its analyzer, translate a failure into the right exception, and
 * unwrap the successful result into its DTO:
 * validate -&gt; DNS -&gt; TCP -&gt; TLS (HTTPS only) -&gt; HTTP -&gt; aggregate.
 * For an HTTP URL, the TLS phase is skipped entirely - it is not
 * applicable, so Probes.tls is null rather than some placeholder/empty
 * result. Keeps this orchestration out of the controller.
 * <p>
 * A DNS, TCP, or TLS failure short-circuits before the next phase
 * runs - if the resolved address refuses connections or rejects the
 * TLS handshake, there is no point letting HttpAnalyzer attempt (and
 * redundantly fail at) its own connection to the same target. Each
 * phase's failure is translated into a specific AnalysisException
 * reason and message based on its own failure category, rather than
 * always reporting a generic connection failure. An out-of-range
 * destination port is a request problem, not a target one, so it is
 * translated into InvalidUrlException (400) instead.
 * <p>
 * Every address DnsAnalyzer resolves is checked against SsrfGuard
 * before TCP/TLS ever run - this project fetches arbitrary
 * user-supplied URLs, so a hostname resolving to a private, loopback,
 * or link-local address (including cloud metadata endpoints) is
 * refused with AnalysisException.Reason.BLOCKED_TARGET rather than
 * connected to. HttpAnalyzer independently re-validates the same way
 * before its own request and before following each redirect, since it
 * performs its own separate resolution rather than reusing this one.
 * See docs/security.md for the full threat model and disclosed
 * limitations.
 * <p>
 * PhaseResult from DnsAnalyzer/TcpAnalyzer/TlsAnalyzer is unwrapped
 * back into the existing flat DnsResult/TcpResult/TlsResult shapes and
 * grouped under Probes, kept structurally separate from the real HTTP
 * request's own fields on AnalyzeResponse (statusCode, protocol,
 * ttfbMs, downloadMs, totalTimeMs, ...) - DnsAnalyzer/TcpAnalyzer/
 * TlsAnalyzer each use their own fresh, dedicated connection, never
 * reused by HttpAnalyzer's real request, so their durations are
 * independent measurements, not sequential phases of one timeline that
 * sum to totalTimeMs. See docs/measurement.md.
 */
@Service
public class AnalysisService {

    private final UrlValidator urlValidator;
    private final DnsAnalyzer dnsAnalyzer;
    private final TcpAnalyzer tcpAnalyzer;
    private final TlsAnalyzer tlsAnalyzer;
    private final HttpAnalyzer httpAnalyzer;
    private final boolean allowPrivateTargets;

    public AnalysisService(
            UrlValidator urlValidator,
            DnsAnalyzer dnsAnalyzer,
            TcpAnalyzer tcpAnalyzer,
            TlsAnalyzer tlsAnalyzer,
            HttpAnalyzer httpAnalyzer,
            AnalyzerProperties analyzerProperties) {
        this.urlValidator = urlValidator;
        this.dnsAnalyzer = dnsAnalyzer;
        this.tcpAnalyzer = tcpAnalyzer;
        this.tlsAnalyzer = tlsAnalyzer;
        this.httpAnalyzer = httpAnalyzer;
        this.allowPrivateTargets = analyzerProperties.allowPrivateTargets();
    }

    public AnalyzeResponse analyze(AnalyzeRequest request) {
        String url = request.url();
        validateUrl(url);

        DnsResult dns = runDns(url);

        // Connect to a specific resolved address, the same one a client
        // would actually reach - not the hostname again, which would let
        // TCP silently redo DNS resolution.
        String resolvedIp = firstResolvedIp(dns);
        int port = portOf(url);

        TcpResult tcp = runTcp(url, resolvedIp, port);
        TlsResult tls = isHttps(url) ? runTls(dns.hostname(), resolvedIp, port) : null;
        HttpResult http = runHttp(url);

        return aggregate(http, new Probes(dns, tcp, tls));
    }

    private void validateUrl(String url) {
        if (!urlValidator.isValid(url)) {
            throw new InvalidUrlException(url);
        }
    }

    private DnsResult runDns(String url) {
        PhaseResult<DnsMetadata> dnsPhase = dnsAnalyzer.analyze(url);
        DnsMetadata metadata = dnsPhase.metadata();
        rejectIfAnyBlocked(metadata.hostname(), metadata.resolvedIpv4());
        rejectIfAnyBlocked(metadata.hostname(), metadata.resolvedIpv6());
        return new DnsResult(
                metadata.hostname(), metadata.resolvedIpv4(), metadata.resolvedIpv6(), dnsPhase.durationMs(), true);
    }

    private void rejectIfAnyBlocked(String hostname, List<String> ips) {
        for (String ip : ips) {
            if (!allowPrivateTargets && SsrfGuard.isBlocked(parseLiteralIp(ip))) {
                throw new AnalysisException(AnalysisException.Reason.BLOCKED_TARGET,
                        "Refusing to analyze " + hostname
                                + ": resolves to a private or reserved address (" + ip + ")", null);
            }
        }
    }

    // resolvedIpv4/resolvedIpv6 entries are always the literal
    // getHostAddress() form of an address DnsAnalyzer already
    // resolved, so re-parsing them here is a local, non-blocking
    // string parse - never a second DNS lookup.
    private static InetAddress parseLiteralIp(String ip) {
        try {
            return InetAddress.getByName(ip);
        } catch (UnknownHostException e) {
            throw new IllegalStateException("Not a literal IP address: " + ip, e);
        }
    }

    // Prefers IPv4 when both families are available. DnsAnalyzer
    // guarantees at least one address across the two lists (it throws
    // DNS_FAILURE rather than ever returning both empty), so exactly
    // one of these two lookups always succeeds.
    private static String firstResolvedIp(DnsResult dns) {
        if (!dns.resolvedIpv4().isEmpty()) {
            return dns.resolvedIpv4().get(0);
        }
        return dns.resolvedIpv6().get(0);
    }

    private TcpResult runTcp(String url, String resolvedIp, int port) {
        PhaseResult<TcpMetadata> tcpPhase;
        try {
            tcpPhase = tcpAnalyzer.analyze(resolvedIp, port);
        } catch (IllegalArgumentException e) {
            throw new InvalidUrlException(url);
        }
        if (tcpPhase.status() == PhaseResult.Status.FAILURE) {
            throw tcpFailure(resolvedIp, port, tcpPhase.metadata().failureReason());
        }
        return TcpResult.success(resolvedIp, port, tcpPhase.durationMs());
    }

    private TlsResult runTls(String hostname, String resolvedIp, int port) {
        PhaseResult<TlsMetadata> tlsPhase = tlsAnalyzer.analyze(hostname, resolvedIp, port);
        if (tlsPhase.status() == PhaseResult.Status.FAILURE) {
            throw tlsFailure(hostname, resolvedIp, port, tlsPhase.metadata().failureReason());
        }
        return new TlsResult(
                tlsPhase.metadata().tlsVersion(),
                tlsPhase.metadata().cipherSuite(),
                tlsPhase.metadata().certificateSubject(),
                tlsPhase.metadata().certificateIssuer(),
                tlsPhase.durationMs());
    }

    private HttpResult runHttp(String url) {
        return httpAnalyzer.analyze(url);
    }

    private static AnalyzeResponse aggregate(HttpResult http, Probes probes) {
        return new AnalyzeResponse(
                http.url(),
                http.statusCode(),
                http.protocol(),
                http.contentLength(),
                http.contentType(),
                http.ttfbMs(),
                http.downloadMs(),
                http.bodyTruncated(),
                http.totalTimeMs(),
                probes);
    }

    private static AnalysisException tcpFailure(String host, int port, TcpFailureReason reason) {
        String target = host + ":" + port;
        return switch (reason) {
            case TIMEOUT -> new AnalysisException(AnalysisException.Reason.TIMEOUT,
                    "Connection to " + target + " timed out", null);
            case CONNECTION_REFUSED -> new AnalysisException(AnalysisException.Reason.CONNECTION_FAILURE,
                    "Connection refused by " + target, null);
            case UNREACHABLE -> new AnalysisException(AnalysisException.Reason.CONNECTION_FAILURE,
                    "No route to host " + target, null);
            case UNKNOWN -> new AnalysisException(AnalysisException.Reason.CONNECTION_FAILURE,
                    "Failed to establish a TCP connection to " + target, null);
        };
    }

    private static AnalysisException tlsFailure(String hostname, String resolvedIp, int port, TlsFailureReason reason) {
        String target = hostname + " (" + resolvedIp + ":" + port + ")";
        return switch (reason) {
            case TIMEOUT -> new AnalysisException(AnalysisException.Reason.TIMEOUT,
                    "TLS handshake with " + target + " timed out", null);
            case CONNECTION_FAILURE -> new AnalysisException(AnalysisException.Reason.CONNECTION_FAILURE,
                    "Failed to connect to " + target + " for the TLS handshake", null);
            case HANDSHAKE_FAILURE -> new AnalysisException(AnalysisException.Reason.CONNECTION_FAILURE,
                    "TLS handshake with " + target + " failed", null);
            case UNKNOWN -> new AnalysisException(AnalysisException.Reason.CONNECTION_FAILURE,
                    "TLS handshake with " + target + " failed", null);
        };
    }

    private static boolean isHttps(String url) {
        return "https".equalsIgnoreCase(URI.create(url).getScheme());
    }

    private static int portOf(String url) {
        URI uri = URI.create(url);
        if (uri.getPort() != -1) {
            return uri.getPort();
        }
        return isHttps(url) ? 443 : 80;
    }
}
