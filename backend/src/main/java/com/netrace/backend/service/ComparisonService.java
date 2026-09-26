package com.netrace.backend.service;

import com.netrace.backend.analyzer.AnalysisException;
import com.netrace.backend.analyzer.HttpAnalyzer;
import com.netrace.backend.dto.CompareRequest;
import com.netrace.backend.dto.CompareResponse;
import com.netrace.backend.dto.CompareResult;
import com.netrace.backend.dto.HttpResult;
import com.netrace.backend.validation.UrlValidator;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * Orchestrates comparing 2-5 URLs by running each through the same
 * HttpAnalyzer a single analysis uses - not a second analyzer, and not
 * the full DNS/TCP/TLS pipeline AnalysisService runs for /api/analyze.
 * Only the real HTTP request and its total/TTFB/download timing are in
 * scope here; see the architecture note on ComparisonService's
 * Javadoc-equivalent in the PR/commit for why.
 * <p>
 * Each URL is independent: a syntax failure (UrlValidator) or an
 * analysis failure (AnalysisException - DNS failure, connection
 * refused, timeout, blocked target, ...) for one URL becomes a
 * CompareResult.failure() carrying that URL's own error message, never
 * an exception that aborts the whole comparison. This is why
 * ComparisonService, unlike AnalysisService, never lets an
 * AnalysisException escape - a single bad target must not prevent
 * results for the others.
 * <p>
 * Analyses run concurrently on a small, bounded, application-wide
 * thread pool (ComparisonConfig.comparisonExecutor) rather than
 * sequentially (slow for no reason) or one thread per URL with no cap
 * (unbounded concurrency).
 */
@Service
public class ComparisonService {

    private final UrlValidator urlValidator;
    private final HttpAnalyzer httpAnalyzer;
    private final ExecutorService executor;

    public ComparisonService(UrlValidator urlValidator, HttpAnalyzer httpAnalyzer, ExecutorService comparisonExecutor) {
        this.urlValidator = urlValidator;
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
        try {
            HttpResult result = httpAnalyzer.analyze(url);
            return CompareResult.success(url, result);
        } catch (AnalysisException e) {
            return CompareResult.failure(url, e.getMessage());
        }
    }
}
