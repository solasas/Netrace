package com.netrace.backend.service;

import com.netrace.backend.analyzer.AnalysisException;
import com.netrace.backend.analyzer.DnsAnalyzer;
import com.netrace.backend.analyzer.HttpAnalyzer;
import com.netrace.backend.analyzer.TcpAnalyzer;
import com.netrace.backend.dto.AnalyzeRequest;
import com.netrace.backend.dto.AnalyzeResponse;
import com.netrace.backend.dto.DnsMetadata;
import com.netrace.backend.dto.DnsResult;
import com.netrace.backend.dto.HttpResult;
import com.netrace.backend.dto.PhaseResult;
import com.netrace.backend.dto.TcpFailureReason;
import com.netrace.backend.dto.TcpMetadata;
import com.netrace.backend.dto.TcpResult;
import com.netrace.backend.validation.UrlValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    private HttpAnalyzer httpAnalyzer;

    private static PhaseResult<DnsMetadata> dnsSuccess(String hostname, String ip) {
        return PhaseResult.success("DNS", 12L, new DnsMetadata(hostname, List.of(ip)));
    }

    private static PhaseResult<TcpMetadata> tcpSuccess(String ip, int port) {
        return PhaseResult.success("TCP", 8L, new TcpMetadata(ip, port));
    }

    @Test
    void combinesDnsTcpAndHttpResultsWhenTheUrlIsValid() {
        String url = "https://example.com";
        PhaseResult<DnsMetadata> dnsPhase = dnsSuccess("example.com", "93.184.216.34");
        PhaseResult<TcpMetadata> tcpPhase = tcpSuccess("93.184.216.34", 443);
        HttpResult http = new HttpResult(url, 200, 42L);
        when(urlValidator.isValid(url)).thenReturn(true);
        when(dnsAnalyzer.analyze(url)).thenReturn(dnsPhase);
        when(tcpAnalyzer.analyze("93.184.216.34", 443)).thenReturn(tcpPhase);
        when(httpAnalyzer.analyze(url)).thenReturn(http);
        AnalysisService service = new AnalysisService(urlValidator, dnsAnalyzer, tcpAnalyzer, httpAnalyzer);

        AnalyzeResponse actual = service.analyze(new AnalyzeRequest(url));

        DnsResult expectedDns = new DnsResult("example.com", List.of("93.184.216.34"), 12L);
        TcpResult expectedTcp = new TcpResult("93.184.216.34", 443, 8L);
        assertThat(actual).isEqualTo(new AnalyzeResponse(url, expectedDns, expectedTcp, 200, 42L));
    }

    @Test
    void usesTheExplicitPortFromTheUrlWhenPresent() {
        String url = "https://example.com:8443/path";
        when(urlValidator.isValid(url)).thenReturn(true);
        when(dnsAnalyzer.analyze(url)).thenReturn(dnsSuccess("example.com", "93.184.216.34"));
        when(tcpAnalyzer.analyze("93.184.216.34", 8443)).thenReturn(tcpSuccess("93.184.216.34", 8443));
        when(httpAnalyzer.analyze(url)).thenReturn(new HttpResult(url, 200, 42L));
        AnalysisService service = new AnalysisService(urlValidator, dnsAnalyzer, tcpAnalyzer, httpAnalyzer);

        service.analyze(new AnalyzeRequest(url));

        verify(tcpAnalyzer).analyze(eq("93.184.216.34"), eq(8443));
    }

    @Test
    void usesPort80ForHttpUrlsWithNoExplicitPort() {
        String url = "http://example.com/";
        when(urlValidator.isValid(url)).thenReturn(true);
        when(dnsAnalyzer.analyze(url)).thenReturn(dnsSuccess("example.com", "93.184.216.34"));
        when(tcpAnalyzer.analyze("93.184.216.34", 80)).thenReturn(tcpSuccess("93.184.216.34", 80));
        when(httpAnalyzer.analyze(url)).thenReturn(new HttpResult(url, 200, 42L));
        AnalysisService service = new AnalysisService(urlValidator, dnsAnalyzer, tcpAnalyzer, httpAnalyzer);

        service.analyze(new AnalyzeRequest(url));

        verify(tcpAnalyzer).analyze(eq("93.184.216.34"), eq(80));
    }

    @Test
    void rejectsAnInvalidUrlWithoutCallingAnyAnalyzer() {
        String url = "not a url";
        when(urlValidator.isValid(url)).thenReturn(false);
        AnalysisService service = new AnalysisService(urlValidator, dnsAnalyzer, tcpAnalyzer, httpAnalyzer);

        assertThatThrownBy(() -> service.analyze(new AnalyzeRequest(url)))
                .isInstanceOf(InvalidUrlException.class);

        verifyNoInteractions(dnsAnalyzer);
        verifyNoInteractions(tcpAnalyzer);
        verifyNoInteractions(httpAnalyzer);
    }

    @Test
    void propagatesADnsFailureWithoutCallingTcpOrHttp() {
        String url = "https://example.com";
        AnalysisException failure = new AnalysisException(
                AnalysisException.Reason.DNS_FAILURE, "boom", new RuntimeException());
        when(urlValidator.isValid(url)).thenReturn(true);
        when(dnsAnalyzer.analyze(url)).thenThrow(failure);
        AnalysisService service = new AnalysisService(urlValidator, dnsAnalyzer, tcpAnalyzer, httpAnalyzer);

        assertThatThrownBy(() -> service.analyze(new AnalyzeRequest(url)))
                .isSameAs(failure);

        verifyNoInteractions(tcpAnalyzer);
        verifyNoInteractions(httpAnalyzer);
    }

    @ParameterizedTest
    @EnumSource(TcpFailureReason.class)
    void mapsEachTcpFailureReasonToAnAnalysisExceptionWithoutCallingHttp(TcpFailureReason tcpReason) {
        String url = "https://example.com";
        when(urlValidator.isValid(url)).thenReturn(true);
        when(dnsAnalyzer.analyze(url)).thenReturn(dnsSuccess("example.com", "93.184.216.34"));
        when(tcpAnalyzer.analyze("93.184.216.34", 443))
                .thenReturn(PhaseResult.failure("TCP", 5000L, new TcpMetadata("93.184.216.34", 443, tcpReason)));
        AnalysisService service = new AnalysisService(urlValidator, dnsAnalyzer, tcpAnalyzer, httpAnalyzer);

        AnalysisException.Reason expected = tcpReason == TcpFailureReason.TIMEOUT
                ? AnalysisException.Reason.TIMEOUT
                : AnalysisException.Reason.CONNECTION_FAILURE;

        assertThatThrownBy(() -> service.analyze(new AnalyzeRequest(url)))
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
        AnalysisService service = new AnalysisService(urlValidator, dnsAnalyzer, tcpAnalyzer, httpAnalyzer);

        assertThatThrownBy(() -> service.analyze(new AnalyzeRequest(url)))
                .isInstanceOf(InvalidUrlException.class);

        verifyNoInteractions(httpAnalyzer);
    }

    @Test
    void propagatesAnHttpFailureAfterDnsAndTcpSucceed() {
        String url = "https://example.com";
        AnalysisException failure = new AnalysisException(
                AnalysisException.Reason.INVALID_RESPONSE, "boom", new RuntimeException());
        when(urlValidator.isValid(url)).thenReturn(true);
        when(dnsAnalyzer.analyze(url)).thenReturn(dnsSuccess("example.com", "93.184.216.34"));
        when(tcpAnalyzer.analyze("93.184.216.34", 443)).thenReturn(tcpSuccess("93.184.216.34", 443));
        when(httpAnalyzer.analyze(url)).thenThrow(failure);
        AnalysisService service = new AnalysisService(urlValidator, dnsAnalyzer, tcpAnalyzer, httpAnalyzer);

        assertThatThrownBy(() -> service.analyze(new AnalyzeRequest(url)))
                .isSameAs(failure);
    }
}
