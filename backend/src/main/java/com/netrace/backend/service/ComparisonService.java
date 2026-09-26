package com.netrace.backend.service;

import com.netrace.backend.analyzer.AnalysisException;
import com.netrace.backend.analyzer.DnsAnalyzer;
import com.netrace.backend.analyzer.HttpAnalyzer;
import com.netrace.backend.analyzer.TcpAnalyzer;
import com.netrace.backend.analyzer.TlsAnalyzer;
import com.netrace.backend.dto.CompareRequest;
import com.netrace.backend.dto.CompareResponse;
import com.netrace.backend.dto.CompareResult;
import com.netrace.backend.dto.DnsMetadata;
import com.netrace.backend.dto.DnsResult;
import com.netrace.backend.dto.HttpResult;
import com.netrace.backend.dto.PhaseResult;
import com.netrace.backend.dto.TcpResult;
import com.netrace.backend.dto.TlsMetadata;
import com.netrace.backend.dto.TlsResult;
import com.netrace.backend.validation.UrlValidator;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * Orchestrates comparing 2-5 URLs by running each through DNS → TCP →
 * HTTP in sequence. Unlike AnalysisService (which performs DNS/TCP/TLS
 * separately and can short-circuit on failure), ComparisonService is
 * designed to analyze as much as possible for each URL even if one
 * phase fails, so that one URL's DNS failure (for example) does not
 * prevent analyzing TCP/HTTP for the other URLs in the batch.
 * <p>
 * Each URL is independent: a syntax failure (UrlValidator) or any
 * analysis failure (DNS, TCP, HTTP) for one URL becomes a
 * CompareResult carrying that URL's own error, never an exception that
 * aborts the whole comparison. Partial results (e.g., DNS succeeded but
 * TCP failed) are represented explicitly in CompareResult.dns and
 * CompareResult.tcp so the frontend can display what succeeded and what
 * didn't for each URL.
 * <p>
 * Analyses run concurrently on a small, bounded, application-wide
 * thread pool (ComparisonConfig.comparisonExecutor) rather than
 * sequentially (slow for no reason) or one thread per URL with no cap
 * (unbounded concurrency).
 */
@Service
public class ComparisonService {

    private final UrlValidator urlValidator;
    private final DnsAnalyzer dnsAnalyzer;
    private final TcpAnalyzer tcpAnalyzer;
    private final TlsAnalyzer tlsAnalyzer;
    private final HttpAnalyzer httpAnalyzer;
    private final ExecutorService executor;

    public ComparisonService(
            UrlValidator urlValidator,
            DnsAnalyzer dnsAnalyzer,
            TcpAnalyzer tcpAnalyzer,
            TlsAnalyzer tlsAnalyzer,
            HttpAnalyzer httpAnalyzer,
            ExecutorService comparisonExecutor) {
        this.urlValidator = urlValidator;
        this.dnsAnalyzer = dnsAnalyzer;
        this.tcpAnalyzer = tcpAnalyzer;
        this.tlsAnalyzer = tlsAnalyzer;
        this.httpAnalyzer = httpAnalyzer;
        this.executor = comparisonExecutor;
    }

    public CompareResponse compare(CompareRequest request) {
        List<CompletableFuture<CompareResult>> futures = request.urls().stream()
                .map(url -> CompletableFuture.supplyAsync(() -> analyzeOne(url), executor))
                .toList();

        List<CompareResult> results = futures.stream()
                .map(CompletableFuture::join)
                .toList();

        return new CompareResponse(results);
    }

    private CompareResult analyzeOne(String url) {
        if (!urlValidator.isValid(url)) {
            return CompareResult.failure(url, "Not a valid http/https URL: " + url);
        }

        DnsResult dns = null;
        TcpResult tcp = null;
        TlsResult tls = null;

        // Run DNS - capture result even on failure so we can report it
        try {
            dns = runDns(url);
        } catch (AnalysisException e) {
            // DNS failed; TCP/TLS/HTTP cannot proceed, so report this as final failure
            return CompareResult.failureWithPartialResults(url, e.getMessage(), null, null, null);
        }

        // Run TCP on the resolved address - capture result even on failure
        String resolvedIp = firstResolvedIp(dns);
        int port = portOf(url);
        try {
            tcp = runTcp(resolvedIp, port);
        } catch (IllegalArgumentException e) {
            // Invalid port: shouldn't happen since UrlValidator already checked,
            // but report it if it does
            return CompareResult.failureWithPartialResults(url, "Invalid port: " + port, dns, null, null);
        }

        // For HTTPS, run TLS on the resolved address - capture result even on failure
        if (isHttps(url)) {
            try {
                tls = runTls(dns.hostname(), resolvedIp, port);
            } catch (IllegalArgumentException e) {
                // Invalid port; shouldn't happen but report if it does
                return CompareResult.failureWithPartialResults(url, "Invalid port: " + port, dns, tcp, null);
            }
        }

        // TCP/TLS succeeded or failed with results; always attempt HTTP
        try {
            HttpResult httpResult = httpAnalyzer.analyze(url);
            return CompareResult.success(url, httpResult, dns, tcp, tls);
        } catch (AnalysisException e) {
            // HTTP failed; return partial results with DNS/TCP/TLS info
            return CompareResult.failureWithPartialResults(url, e.getMessage(), dns, tcp, tls);
        }
    }

    private DnsResult runDns(String url) {
        PhaseResult<DnsMetadata> dnsPhase = dnsAnalyzer.analyze(url);
        DnsMetadata metadata = dnsPhase.metadata();
        return new DnsResult(
                metadata.hostname(),
                metadata.resolvedIpv4(),
                metadata.resolvedIpv6(),
                dnsPhase.durationMs(),
                true);
    }

    private TcpResult runTcp(String resolvedIp, int port) {
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("Invalid destination port: " + port);
        }
        PhaseResult<com.netrace.backend.dto.TcpMetadata> tcpPhase =
                tcpAnalyzer.analyze(resolvedIp, port);

        if (tcpPhase.status() == PhaseResult.Status.FAILURE) {
            return TcpResult.failure(
                    resolvedIp,
                    port,
                    tcpPhase.durationMs(),
                    tcpPhase.metadata().failureReason());
        }
        return TcpResult.success(resolvedIp, port, tcpPhase.durationMs());
    }

    private static String firstResolvedIp(DnsResult dns) {
        if (!dns.resolvedIpv4().isEmpty()) {
            return dns.resolvedIpv4().get(0);
        }
        return dns.resolvedIpv6().get(0);
    }

    private static int portOf(String url) {
        URI uri = URI.create(url);
        if (uri.getPort() != -1) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private TlsResult runTls(String hostname, String resolvedIp, int port) {
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("Invalid destination port: " + port);
        }
        PhaseResult<TlsMetadata> tlsPhase = tlsAnalyzer.analyze(hostname, resolvedIp, port);
        if (tlsPhase.status() == PhaseResult.Status.FAILURE) {
            TlsMetadata metadata = tlsPhase.metadata();
            return new TlsResult(null, null, null, null, tlsPhase.durationMs());
        }
        TlsMetadata metadata = tlsPhase.metadata();
        return new TlsResult(
                metadata.tlsVersion(),
                metadata.cipherSuite(),
                metadata.certificateSubject(),
                metadata.certificateIssuer(),
                tlsPhase.durationMs());
    }

    private static boolean isHttps(String url) {
        return "https".equalsIgnoreCase(URI.create(url).getScheme());
    }
}
