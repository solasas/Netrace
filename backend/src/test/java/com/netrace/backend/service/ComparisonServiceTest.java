package com.netrace.backend.service;

import com.netrace.backend.analyzer.AnalysisException;
import com.netrace.backend.analyzer.DnsAnalyzer;
import com.netrace.backend.analyzer.HttpAnalyzer;
import com.netrace.backend.analyzer.TcpAnalyzer;
import com.netrace.backend.analyzer.TlsAnalyzer;
import com.netrace.backend.dto.CompareRequest;
import com.netrace.backend.dto.CompareResult;
import com.netrace.backend.dto.DnsMetadata;
import com.netrace.backend.dto.HttpResult;
import com.netrace.backend.dto.PhaseResult;
import com.netrace.backend.dto.TcpMetadata;
import com.netrace.backend.dto.TcpResult;
import com.netrace.backend.dto.TlsMetadata;
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
    private DnsAnalyzer dnsAnalyzer;

    @Mock
    private TcpAnalyzer tcpAnalyzer;

    @Mock
    private TlsAnalyzer tlsAnalyzer;

    @Mock
    private HttpAnalyzer httpAnalyzer;

    private final ExecutorService executor = Executors.newFixedThreadPool(3);

    @AfterEach
    void shutdownExecutor() {
        executor.shutdownNow();
    }

    private ComparisonService service() {
        return new ComparisonService(urlValidator, dnsAnalyzer, tcpAnalyzer, tlsAnalyzer, httpAnalyzer, executor);
    }

    private static HttpResult httpResult(String url, int statusCode, long totalTimeMs, String protocol) {
        return new HttpResult(url, statusCode, totalTimeMs, totalTimeMs - 5, 5, false, protocol, 100L, "text/html", url, 0, 100L);
    }

    private static PhaseResult<DnsMetadata> dnsSuccess(String hostname, String ipv4) {
        return PhaseResult.success("DNS", 10L, new DnsMetadata(hostname, List.of(ipv4), List.of()));
    }

    private static PhaseResult<TlsMetadata> tlsSuccess(String hostname, String ip, int port) {
        return PhaseResult.success("TLS", 7L,
                new TlsMetadata(hostname, ip, port, "TLSv1.3", "TLS_AES_256_GCM_SHA384", "CN=example.com", "CN=Example CA", null));
    }

    @Test
    void returnsOneSuccessResultPerUrlInRequestOrder() {
        String urlA = "https://a.example.com";
        String urlB = "https://b.example.com";
        when(urlValidator.isValid(urlA)).thenReturn(true);
        when(urlValidator.isValid(urlB)).thenReturn(true);

        when(dnsAnalyzer.analyze(urlA)).thenReturn(dnsSuccess("a.example.com", "1.2.3.4"));
        when(tcpAnalyzer.analyze("1.2.3.4", 443)).thenReturn(PhaseResult.success("TCP", 5L, new TcpMetadata("1.2.3.4", 443)));
        when(tlsAnalyzer.analyze("a.example.com", "1.2.3.4", 443)).thenReturn(tlsSuccess("a.example.com", "1.2.3.4", 443));
        when(httpAnalyzer.analyze(urlA)).thenReturn(httpResult(urlA, 200, 120L, "HTTP/2"));

        when(dnsAnalyzer.analyze(urlB)).thenReturn(dnsSuccess("b.example.com", "5.6.7.8"));
        when(tcpAnalyzer.analyze("5.6.7.8", 443)).thenReturn(PhaseResult.success("TCP", 3L, new TcpMetadata("5.6.7.8", 443)));
        when(tlsAnalyzer.analyze("b.example.com", "5.6.7.8", 443)).thenReturn(tlsSuccess("b.example.com", "5.6.7.8", 443));
        when(httpAnalyzer.analyze(urlB)).thenReturn(httpResult(urlB, 200, 80L, "HTTP/2"));

        List<CompareResult> results = service().compare(new CompareRequest(List.of(urlA, urlB))).results();

        assertThat(results).hasSize(2);
        assertThat(results.get(0).url()).isEqualTo(urlA);
        assertThat(results.get(0).success()).isTrue();
        assertThat(results.get(0).statusCode()).isEqualTo(200);
        assertThat(results.get(0).totalTimeMs()).isEqualTo(120L);
        assertThat(results.get(0).dns()).isNotNull();
        assertThat(results.get(0).tcp()).isNotNull();
        assertThat(results.get(0).tls()).isNotNull();

        assertThat(results.get(1).url()).isEqualTo(urlB);
        assertThat(results.get(1).success()).isTrue();
        assertThat(results.get(1).statusCode()).isEqualTo(200);
        assertThat(results.get(1).totalTimeMs()).isEqualTo(80L);
        assertThat(results.get(1).dns()).isNotNull();
        assertThat(results.get(1).tcp()).isNotNull();
        assertThat(results.get(1).tls()).isNotNull();
    }

    @Test
    void marksASyntacticallyInvalidUrlAsAFailureWithoutCallingHttpAnalyzer() {
        String badUrl = "not a url";
        String goodUrl = "https://example.com";
        when(urlValidator.isValid(badUrl)).thenReturn(false);
        when(urlValidator.isValid(goodUrl)).thenReturn(true);

        when(dnsAnalyzer.analyze(goodUrl)).thenReturn(dnsSuccess("example.com", "1.2.3.4"));
        when(tcpAnalyzer.analyze("1.2.3.4", 443)).thenReturn(PhaseResult.success("TCP", 5L, new TcpMetadata("1.2.3.4", 443)));
        when(tlsAnalyzer.analyze("example.com", "1.2.3.4", 443)).thenReturn(tlsSuccess("example.com", "1.2.3.4", 443));
        when(httpAnalyzer.analyze(goodUrl)).thenReturn(httpResult(goodUrl, 200, 50L, "HTTP/2"));

        List<CompareResult> results = service().compare(new CompareRequest(List.of(badUrl, goodUrl))).results();

        assertThat(results.get(0).success()).isFalse();
        assertThat(results.get(0).error()).contains(badUrl);
        assertThat(results.get(0).statusCode()).isNull();
        assertThat(results.get(1).success()).isTrue();
        verify(httpAnalyzer, never()).analyze(eq(badUrl));
        verify(dnsAnalyzer, never()).analyze(eq(badUrl));
    }

    @Test
    void marksAFailedAnalysisAsAFailureWithoutAffectingOtherUrls() {
        String failingUrl = "https://unreachable.example.com";
        String succeedingUrl = "https://example.com";
        when(urlValidator.isValid(failingUrl)).thenReturn(true);
        when(urlValidator.isValid(succeedingUrl)).thenReturn(true);

        when(dnsAnalyzer.analyze(failingUrl)).thenThrow(new AnalysisException(
                AnalysisException.Reason.CONNECTION_FAILURE, "Failed to connect to " + failingUrl, null));

        when(dnsAnalyzer.analyze(succeedingUrl)).thenReturn(dnsSuccess("example.com", "1.2.3.4"));
        when(tcpAnalyzer.analyze("1.2.3.4", 443)).thenReturn(PhaseResult.success("TCP", 5L, new TcpMetadata("1.2.3.4", 443)));
        when(tlsAnalyzer.analyze("example.com", "1.2.3.4", 443)).thenReturn(tlsSuccess("example.com", "1.2.3.4", 443));
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

        when(dnsAnalyzer.analyze(urlA)).thenThrow(new AnalysisException(
                AnalysisException.Reason.TIMEOUT, "Request to " + urlA + " timed out", null));
        when(dnsAnalyzer.analyze(urlB)).thenThrow(new AnalysisException(
                AnalysisException.Reason.DNS_FAILURE, "Could not resolve host for " + urlB, null));

        List<CompareResult> results = service().compare(new CompareRequest(List.of(urlA, urlB))).results();

        assertThat(results).extracting(CompareResult::success).containsExactly(false, false);
    }

    @Test
    void httpUrlsHaveNullTlsButSucceedOtherwise() {
        String httpUrl = "http://example.com";
        when(urlValidator.isValid(httpUrl)).thenReturn(true);

        when(dnsAnalyzer.analyze(httpUrl)).thenReturn(dnsSuccess("example.com", "1.2.3.4"));
        when(tcpAnalyzer.analyze("1.2.3.4", 80)).thenReturn(PhaseResult.success("TCP", 5L, new TcpMetadata("1.2.3.4", 80)));
        when(httpAnalyzer.analyze(httpUrl)).thenReturn(httpResult(httpUrl, 200, 30L, "HTTP/1.1"));

        List<CompareResult> results = service().compare(new CompareRequest(List.of(httpUrl))).results();

        assertThat(results).hasSize(1);
        assertThat(results.get(0).success()).isTrue();
        assertThat(results.get(0).dns()).isNotNull();
        assertThat(results.get(0).tcp()).isNotNull();
        assertThat(results.get(0).tls()).isNull();
    }
}
