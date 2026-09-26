package com.netrace.backend.service;

import com.netrace.backend.analyzer.AnalysisException;
import com.netrace.backend.analyzer.HttpAnalyzer;
import com.netrace.backend.dto.CompareRequest;
import com.netrace.backend.dto.CompareResult;
import com.netrace.backend.dto.HttpResult;
import com.netrace.backend.validation.UrlValidator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ComparisonServiceTest {

    @Mock
    private UrlValidator urlValidator;

    @Mock
    private HttpAnalyzer httpAnalyzer;

    private final ExecutorService executor = Executors.newFixedThreadPool(3);

    @AfterEach
    void shutdownExecutor() {
        executor.shutdownNow();
    }

    private ComparisonService service() {
        return new ComparisonService(urlValidator, httpAnalyzer, executor);
    }

    private static HttpResult httpResult(String url, int statusCode, long totalTimeMs, String protocol) {
        return new HttpResult(url, statusCode, totalTimeMs, totalTimeMs - 5, 5, false, protocol, 100L, "text/html");
    }

    @Test
    void returnsOneSuccessResultPerUrlInRequestOrder() {
        String urlA = "https://a.example.com";
        String urlB = "https://b.example.com";
        when(urlValidator.isValid(urlA)).thenReturn(true);
        when(urlValidator.isValid(urlB)).thenReturn(true);
        when(httpAnalyzer.analyze(urlA)).thenReturn(httpResult(urlA, 200, 120L, "HTTP/2"));
        when(httpAnalyzer.analyze(urlB)).thenReturn(httpResult(urlB, 200, 80L, "HTTP/2"));

        List<CompareResult> results = service().compare(new CompareRequest(List.of(urlA, urlB))).results();

        assertThat(results).containsExactly(
                new CompareResult(urlA, true, 200, "HTTP/2", 120L, null),
                new CompareResult(urlB, true, 200, "HTTP/2", 80L, null));
    }

    @Test
    void marksASyntacticallyInvalidUrlAsAFailureWithoutCallingHttpAnalyzer() {
        String badUrl = "not a url";
        String goodUrl = "https://example.com";
        when(urlValidator.isValid(badUrl)).thenReturn(false);
        when(urlValidator.isValid(goodUrl)).thenReturn(true);
        when(httpAnalyzer.analyze(goodUrl)).thenReturn(httpResult(goodUrl, 200, 50L, "HTTP/2"));

        List<CompareResult> results = service().compare(new CompareRequest(List.of(badUrl, goodUrl))).results();

        assertThat(results.get(0).success()).isFalse();
        assertThat(results.get(0).error()).contains(badUrl);
        assertThat(results.get(0).statusCode()).isNull();
        assertThat(results.get(1).success()).isTrue();
        verify(httpAnalyzer, never()).analyze(eq(badUrl));
    }

    @Test
    void marksAFailedAnalysisAsAFailureWithoutAffectingOtherUrls() {
        String failingUrl = "https://unreachable.example.com";
        String succeedingUrl = "https://example.com";
        when(urlValidator.isValid(failingUrl)).thenReturn(true);
        when(urlValidator.isValid(succeedingUrl)).thenReturn(true);
        when(httpAnalyzer.analyze(failingUrl)).thenThrow(new AnalysisException(
                AnalysisException.Reason.CONNECTION_FAILURE, "Failed to connect to " + failingUrl, null));
        when(httpAnalyzer.analyze(succeedingUrl)).thenReturn(httpResult(succeedingUrl, 200, 90L, "HTTP/1.1"));

        List<CompareResult> results = service()
                .compare(new CompareRequest(List.of(failingUrl, succeedingUrl))).results();

        assertThat(results.get(0).success()).isFalse();
        assertThat(results.get(0).error()).isEqualTo("Failed to connect to " + failingUrl);
        assertThat(results.get(1).success()).isTrue();
        assertThat(results.get(1).totalTimeMs()).isEqualTo(90L);
    }

    @Test
    void returnsAllFailuresWhenEveryUrlFails() {
        String urlA = "https://a.example.com";
        String urlB = "https://b.example.com";
        when(urlValidator.isValid(urlA)).thenReturn(true);
        when(urlValidator.isValid(urlB)).thenReturn(true);
        when(httpAnalyzer.analyze(urlA)).thenThrow(new AnalysisException(
                AnalysisException.Reason.TIMEOUT, "Request to " + urlA + " timed out", null));
        when(httpAnalyzer.analyze(urlB)).thenThrow(new AnalysisException(
                AnalysisException.Reason.DNS_FAILURE, "Could not resolve host for " + urlB, null));

        List<CompareResult> results = service().compare(new CompareRequest(List.of(urlA, urlB))).results();

        assertThat(results).extracting(CompareResult::success).containsExactly(false, false);
    }
}
