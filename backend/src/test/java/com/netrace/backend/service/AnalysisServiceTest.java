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
import com.netrace.backend.validation.UrlValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalysisServiceTest {

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

    private static PhaseResult<DnsMetadata> dnsSuccess(String hostname, String ip) {
        return PhaseResult.success("DNS", 12L, new DnsMetadata(hostname, List.of(ip)));
    }

    private static PhaseResult<TcpMetadata> tcpSuccess(String ip, int port) {
        return PhaseResult.success("TCP", 8L, new TcpMetadata(ip, port));
    }

    private static PhaseResult<TlsMetadata> tlsSuccess(String hostname, String ip, int port) {
        return PhaseResult.success("TLS", 20L,
                new TlsMetadata(hostname, ip, port, "TLSv1.3", "TLS_AES_128_GCM_SHA256",
                        "CN=" + hostname, "CN=Test CA", null));
    }

    private AnalysisService service() {
        return service(false);
    }

    private AnalysisService service(boolean allowPrivateTargets) {
        AnalyzerProperties properties = new AnalyzerProperties(
                Duration.ofSeconds(2), Duration.ofSeconds(2), null, 5, allowPrivateTargets);
        return new AnalysisService(urlValidator, dnsAnalyzer, tcpAnalyzer, tlsAnalyzer, httpAnalyzer, properties);
    }

    @Test
    void combinesDnsTcpTlsAndHttpResultsForAnHttpsUrl() {
        String url = "https://example.com";
        when(urlValidator.isValid(url)).thenReturn(true);
        when(dnsAnalyzer.analyze(url)).thenReturn(dnsSuccess("example.com", "93.184.216.34"));
        when(tcpAnalyzer.analyze("93.184.216.34", 443)).thenReturn(tcpSuccess("93.184.216.34", 443));
        when(tlsAnalyzer.analyze("example.com", "93.184.216.34", 443))
                .thenReturn(tlsSuccess("example.com", "93.184.216.34", 443));
        when(httpAnalyzer.analyze(url)).thenReturn(new HttpResult(url, 200, 42L, 30L, 12L, false, "HTTP/2", 1234L, "text/html"));

        AnalyzeResponse actual = service().analyze(new AnalyzeRequest(url));

        DnsResult expectedDns = new DnsResult("example.com", List.of("93.184.216.34"), 12L);
        TcpResult expectedTcp = new TcpResult("93.184.216.34", 443, 8L);
        TlsResult expectedTls =
                new TlsResult("TLSv1.3", "TLS_AES_128_GCM_SHA256", "CN=example.com", "CN=Test CA", 20L);
        Probes expectedProbes = new Probes(expectedDns, expectedTcp, expectedTls);
        assertThat(actual)
                .isEqualTo(new AnalyzeResponse(
                        url, 200, "HTTP/2", 1234L, "text/html", 30L, 12L, false, 42L, expectedProbes));
    }

    @Test
    void skipsTlsEntirelyForAnHttpUrl() {
        String url = "http://example.com/";
        when(urlValidator.isValid(url)).thenReturn(true);
        when(dnsAnalyzer.analyze(url)).thenReturn(dnsSuccess("example.com", "93.184.216.34"));
        when(tcpAnalyzer.analyze("93.184.216.34", 80)).thenReturn(tcpSuccess("93.184.216.34", 80));
        when(httpAnalyzer.analyze(url)).thenReturn(new HttpResult(url, 200, 42L, 30L, 12L, false, "HTTP/2", 1234L, "text/html"));

        AnalyzeResponse actual = service().analyze(new AnalyzeRequest(url));

        assertThat(actual.probes().tls()).isNull();
        verifyNoInteractions(tlsAnalyzer);
    }

    @Test
    void usesTheExplicitPortFromTheUrlWhenPresent() {
        String url = "https://example.com:8443/path";
        when(urlValidator.isValid(url)).thenReturn(true);
        when(dnsAnalyzer.analyze(url)).thenReturn(dnsSuccess("example.com", "93.184.216.34"));
        when(tcpAnalyzer.analyze("93.184.216.34", 8443)).thenReturn(tcpSuccess("93.184.216.34", 8443));
        when(tlsAnalyzer.analyze("example.com", "93.184.216.34", 8443))
                .thenReturn(tlsSuccess("example.com", "93.184.216.34", 8443));
        when(httpAnalyzer.analyze(url)).thenReturn(new HttpResult(url, 200, 42L, 30L, 12L, false, "HTTP/2", 1234L, "text/html"));

        service().analyze(new AnalyzeRequest(url));

        verify(tcpAnalyzer).analyze(eq("93.184.216.34"), eq(8443));
        verify(tlsAnalyzer).analyze(eq("example.com"), eq("93.184.216.34"), eq(8443));
    }

    @Test
    void usesPort80ForHttpUrlsWithNoExplicitPort() {
        String url = "http://example.com/";
        when(urlValidator.isValid(url)).thenReturn(true);
        when(dnsAnalyzer.analyze(url)).thenReturn(dnsSuccess("example.com", "93.184.216.34"));
        when(tcpAnalyzer.analyze("93.184.216.34", 80)).thenReturn(tcpSuccess("93.184.216.34", 80));
        when(httpAnalyzer.analyze(url)).thenReturn(new HttpResult(url, 200, 42L, 30L, 12L, false, "HTTP/2", 1234L, "text/html"));

        service().analyze(new AnalyzeRequest(url));

        verify(tcpAnalyzer).analyze(eq("93.184.216.34"), eq(80));
        verifyNoInteractions(tlsAnalyzer);
    }

    @Test
    void rejectsAnInvalidUrlWithoutCallingAnyAnalyzer() {
        String url = "not a url";
        when(urlValidator.isValid(url)).thenReturn(false);

        assertThatThrownBy(() -> service().analyze(new AnalyzeRequest(url)))
                .isInstanceOf(InvalidUrlException.class);

        verifyNoInteractions(dnsAnalyzer);
        verifyNoInteractions(tcpAnalyzer);
        verifyNoInteractions(tlsAnalyzer);
        verifyNoInteractions(httpAnalyzer);
    }

    @Test
    void propagatesADnsFailureWithoutCallingTcpTlsOrHttp() {
        String url = "https://example.com";
        AnalysisException failure = new AnalysisException(
                AnalysisException.Reason.DNS_FAILURE, "boom", new RuntimeException());
        when(urlValidator.isValid(url)).thenReturn(true);
        when(dnsAnalyzer.analyze(url)).thenThrow(failure);

        assertThatThrownBy(() -> service().analyze(new AnalyzeRequest(url)))
                .isSameAs(failure);

        verifyNoInteractions(tcpAnalyzer);
        verifyNoInteractions(tlsAnalyzer);
        verifyNoInteractions(httpAnalyzer);
    }

    @Test
    void blocksATargetThatResolvesToAPrivateAddressWithoutCallingTcpTlsOrHttp() {
        String url = "https://internal.example.com";
        when(urlValidator.isValid(url)).thenReturn(true);
        when(dnsAnalyzer.analyze(url)).thenReturn(dnsSuccess("internal.example.com", "10.0.0.5"));

        assertThatThrownBy(() -> service().analyze(new AnalyzeRequest(url)))
                .isInstanceOf(AnalysisException.class)
                .extracting(e -> ((AnalysisException) e).reason())
                .isEqualTo(AnalysisException.Reason.BLOCKED_TARGET);

        verifyNoInteractions(tcpAnalyzer);
        verifyNoInteractions(tlsAnalyzer);
        verifyNoInteractions(httpAnalyzer);
    }

    @Test
    void allowPrivateTargetsBypassesTheBlockForAPrivateAddress() {
        String url = "http://internal.example.com/";
        when(urlValidator.isValid(url)).thenReturn(true);
        when(dnsAnalyzer.analyze(url)).thenReturn(dnsSuccess("internal.example.com", "10.0.0.5"));
        when(tcpAnalyzer.analyze("10.0.0.5", 80)).thenReturn(tcpSuccess("10.0.0.5", 80));
        when(httpAnalyzer.analyze(url))
                .thenReturn(new HttpResult(url, 200, 42L, 30L, 12L, false, "HTTP/1.1", 10L, "text/plain"));

        AnalyzeResponse actual = service(true).analyze(new AnalyzeRequest(url));

        assertThat(actual.probes().dns().resolvedIps()).containsExactly("10.0.0.5");
    }

    @ParameterizedTest
    @EnumSource(TcpFailureReason.class)
    void mapsEachTcpFailureReasonToAnAnalysisExceptionWithoutCallingTlsOrHttp(TcpFailureReason tcpReason) {
        String url = "https://example.com";
        when(urlValidator.isValid(url)).thenReturn(true);
        when(dnsAnalyzer.analyze(url)).thenReturn(dnsSuccess("example.com", "93.184.216.34"));
        when(tcpAnalyzer.analyze("93.184.216.34", 443))
                .thenReturn(PhaseResult.failure("TCP", 5000L, new TcpMetadata("93.184.216.34", 443, tcpReason)));

        AnalysisException.Reason expected = tcpReason == TcpFailureReason.TIMEOUT
                ? AnalysisException.Reason.TIMEOUT
                : AnalysisException.Reason.CONNECTION_FAILURE;

        assertThatThrownBy(() -> service().analyze(new AnalyzeRequest(url)))
                .isInstanceOf(AnalysisException.class)
                .extracting(e -> ((AnalysisException) e).reason())
                .isEqualTo(expected);

        verifyNoInteractions(tlsAnalyzer);
        verifyNoInteractions(httpAnalyzer);
    }

    @ParameterizedTest
    @EnumSource(TlsFailureReason.class)
    void mapsEachTlsFailureReasonToAnAnalysisExceptionWithoutCallingHttp(TlsFailureReason tlsReason) {
        String url = "https://example.com";
        when(urlValidator.isValid(url)).thenReturn(true);
        when(dnsAnalyzer.analyze(url)).thenReturn(dnsSuccess("example.com", "93.184.216.34"));
        when(tcpAnalyzer.analyze("93.184.216.34", 443)).thenReturn(tcpSuccess("93.184.216.34", 443));
        when(tlsAnalyzer.analyze("example.com", "93.184.216.34", 443))
                .thenReturn(PhaseResult.failure("TLS", 5000L,
                        new TlsMetadata("example.com", "93.184.216.34", 443, tlsReason)));

        AnalysisException.Reason expected = tlsReason == TlsFailureReason.TIMEOUT
                ? AnalysisException.Reason.TIMEOUT
                : AnalysisException.Reason.CONNECTION_FAILURE;

        assertThatThrownBy(() -> service().analyze(new AnalyzeRequest(url)))
                .isInstanceOf(AnalysisException.class)
                .extracting(e -> ((AnalysisException) e).reason())
                .isEqualTo(expected);

        verifyNoInteractions(httpAnalyzer);
    }

    @Test
    void throwsAnInvalidUrlWhenTheDestinationPortIsOutOfRange() {
        String url = "https://example.com:99999/";
        when(urlValidator.isValid(url)).thenReturn(true);
        when(dnsAnalyzer.analyze(url)).thenReturn(dnsSuccess("example.com", "93.184.216.34"));
        when(tcpAnalyzer.analyze("93.184.216.34", 99999))
                .thenThrow(new IllegalArgumentException("Invalid destination port: 99999"));

        assertThatThrownBy(() -> service().analyze(new AnalyzeRequest(url)))
                .isInstanceOf(InvalidUrlException.class);

        verifyNoInteractions(tlsAnalyzer);
        verifyNoInteractions(httpAnalyzer);
    }

    @Test
    void propagatesAnHttpFailureAfterDnsTcpAndTlsSucceed() {
        String url = "https://example.com";
        AnalysisException failure = new AnalysisException(
                AnalysisException.Reason.INVALID_RESPONSE, "boom", new RuntimeException());
        when(urlValidator.isValid(url)).thenReturn(true);
        when(dnsAnalyzer.analyze(url)).thenReturn(dnsSuccess("example.com", "93.184.216.34"));
        when(tcpAnalyzer.analyze("93.184.216.34", 443)).thenReturn(tcpSuccess("93.184.216.34", 443));
        when(tlsAnalyzer.analyze("example.com", "93.184.216.34", 443))
                .thenReturn(tlsSuccess("example.com", "93.184.216.34", 443));
        when(httpAnalyzer.analyze(url)).thenThrow(failure);

        assertThatThrownBy(() -> service().analyze(new AnalyzeRequest(url)))
                .isSameAs(failure);
    }
}
