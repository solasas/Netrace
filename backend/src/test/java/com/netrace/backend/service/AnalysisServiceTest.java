package com.netrace.backend.service;

import com.netrace.backend.analyzer.AnalysisException;
import com.netrace.backend.analyzer.DnsAnalyzer;
import com.netrace.backend.analyzer.HttpAnalyzer;
import com.netrace.backend.dto.AnalyzeRequest;
import com.netrace.backend.dto.AnalyzeResponse;
import com.netrace.backend.dto.DnsMetadata;
import com.netrace.backend.dto.DnsResult;
import com.netrace.backend.dto.HttpResult;
import com.netrace.backend.dto.PhaseResult;
import com.netrace.backend.validation.UrlValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalysisServiceTest {

    @Mock
    private UrlValidator urlValidator;

    @Mock
    private DnsAnalyzer dnsAnalyzer;

    @Mock
    private HttpAnalyzer httpAnalyzer;

    @Test
    void combinesDnsAndHttpResultsWhenTheUrlIsValid() {
        String url = "https://example.com";
        PhaseResult<DnsMetadata> dnsPhase = PhaseResult.success(
                "DNS", 12L, new DnsMetadata("example.com", List.of("93.184.216.34")));
        HttpResult http = new HttpResult(url, 200, 42L);
        when(urlValidator.isValid(url)).thenReturn(true);
        when(dnsAnalyzer.analyze(url)).thenReturn(dnsPhase);
        when(httpAnalyzer.analyze(url)).thenReturn(http);
        AnalysisService service = new AnalysisService(urlValidator, dnsAnalyzer, httpAnalyzer);

        AnalyzeResponse actual = service.analyze(new AnalyzeRequest(url));

        DnsResult expectedDns = new DnsResult("example.com", List.of("93.184.216.34"), 12L);
        assertThat(actual).isEqualTo(new AnalyzeResponse(url, expectedDns, 200, 42L));
    }

    @Test
    void rejectsAnInvalidUrlWithoutCallingEitherAnalyzer() {
        String url = "not a url";
        when(urlValidator.isValid(url)).thenReturn(false);
        AnalysisService service = new AnalysisService(urlValidator, dnsAnalyzer, httpAnalyzer);

        assertThatThrownBy(() -> service.analyze(new AnalyzeRequest(url)))
                .isInstanceOf(InvalidUrlException.class);

        verifyNoInteractions(dnsAnalyzer);
        verifyNoInteractions(httpAnalyzer);
    }

    @Test
    void propagatesADnsFailureWithoutCallingTheHttpAnalyzer() {
        String url = "https://example.com";
        AnalysisException failure = new AnalysisException(
                AnalysisException.Reason.DNS_FAILURE, "boom", new RuntimeException());
        when(urlValidator.isValid(url)).thenReturn(true);
        when(dnsAnalyzer.analyze(url)).thenThrow(failure);
        AnalysisService service = new AnalysisService(urlValidator, dnsAnalyzer, httpAnalyzer);

        assertThatThrownBy(() -> service.analyze(new AnalyzeRequest(url)))
                .isSameAs(failure);

        verifyNoInteractions(httpAnalyzer);
    }

    @Test
    void propagatesAnHttpFailureAfterDnsSucceeds() {
        String url = "https://example.com";
        PhaseResult<DnsMetadata> dnsPhase = PhaseResult.success(
                "DNS", 12L, new DnsMetadata("example.com", List.of("93.184.216.34")));
        AnalysisException failure = new AnalysisException(
                AnalysisException.Reason.CONNECTION_FAILURE, "boom", new RuntimeException());
        when(urlValidator.isValid(url)).thenReturn(true);
        when(dnsAnalyzer.analyze(url)).thenReturn(dnsPhase);
        when(httpAnalyzer.analyze(url)).thenThrow(failure);
        AnalysisService service = new AnalysisService(urlValidator, dnsAnalyzer, httpAnalyzer);

        assertThatThrownBy(() -> service.analyze(new AnalyzeRequest(url)))
                .isSameAs(failure);
    }
}
