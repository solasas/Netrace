package com.netrace.backend.service;

import com.netrace.backend.analyzer.AnalysisException;
import com.netrace.backend.analyzer.DnsAnalyzer;
import com.netrace.backend.analyzer.HttpAnalyzer;
import com.netrace.backend.analyzer.TcpAnalyzer;
import com.netrace.backend.analyzer.TlsAnalyzer;
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
import com.netrace.backend.validation.UrlValidator;
import org.springframework.stereotype.Service;

import java.net.URI;

/**
 * Coordinates a single analysis end to end. For an HTTPS URL:
 * validate, resolve (DnsAnalyzer), open a fresh TCP connection to the
 * resolved destination (TcpAnalyzer), perform a fresh TLS handshake
 * over that same destination (TlsAnalyzer), then run the real HTTP
 * request (HttpAnalyzer). For an HTTP URL, the TLS phase is skipped
 * entirely - it is not applicable, so Probes.tls is null rather than
 * some placeholder/empty result. Keeps this orchestration out of the
 * controller.
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

    public AnalysisService(
            UrlValidator urlValidator,
            DnsAnalyzer dnsAnalyzer,
            TcpAnalyzer tcpAnalyzer,
            TlsAnalyzer tlsAnalyzer,
            HttpAnalyzer httpAnalyzer) {
        this.urlValidator = urlValidator;
        this.dnsAnalyzer = dnsAnalyzer;
        this.tcpAnalyzer = tcpAnalyzer;
        this.tlsAnalyzer = tlsAnalyzer;
        this.httpAnalyzer = httpAnalyzer;
    }

    public AnalyzeResponse analyze(AnalyzeRequest request) {
        String url = request.url();
        if (!urlValidator.isValid(url)) {
            throw new InvalidUrlException(url);
        }

        PhaseResult<DnsMetadata> dnsPhase = dnsAnalyzer.analyze(url);
        DnsResult dns = new DnsResult(
                dnsPhase.metadata().hostname(), dnsPhase.metadata().resolvedIps(), dnsPhase.durationMs());

        // Connect to a specific resolved address, the same one a client
        // would actually reach - not the hostname again, which would let
        // TCP silently redo DNS resolution.
        String resolvedIp = dns.resolvedIps().get(0);
        int port = portOf(url);

        PhaseResult<TcpMetadata> tcpPhase;
        try {
            tcpPhase = tcpAnalyzer.analyze(resolvedIp, port);
        } catch (IllegalArgumentException e) {
            throw new InvalidUrlException(url);
        }
        if (tcpPhase.status() == PhaseResult.Status.FAILURE) {
            throw tcpFailure(resolvedIp, port, tcpPhase.metadata().failureReason());
        }
        TcpResult tcp = new TcpResult(resolvedIp, port, tcpPhase.durationMs());

        TlsResult tls = null;
        if (isHttps(url)) {
            PhaseResult<TlsMetadata> tlsPhase = tlsAnalyzer.analyze(dns.hostname(), resolvedIp, port);
            if (tlsPhase.status() == PhaseResult.Status.FAILURE) {
                throw tlsFailure(dns.hostname(), resolvedIp, port, tlsPhase.metadata().failureReason());
            }
            tls = new TlsResult(
                    tlsPhase.metadata().tlsVersion(),
                    tlsPhase.metadata().cipherSuite(),
                    tlsPhase.metadata().certificateSubject(),
                    tlsPhase.metadata().certificateIssuer(),
                    tlsPhase.durationMs());
        }

        HttpResult http = httpAnalyzer.analyze(url);

        Probes probes = new Probes(dns, tcp, tls);
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
