package com.netrace.backend.controller;

import com.netrace.backend.analyzer.AnalysisException;
import com.netrace.backend.dto.AnalyzeResponse;
import com.netrace.backend.dto.DnsResult;
import com.netrace.backend.dto.Probes;
import com.netrace.backend.dto.TcpResult;
import com.netrace.backend.dto.TlsResult;
import com.netrace.backend.service.AnalysisService;
import com.netrace.backend.service.InvalidUrlException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AnalyzeController.class)
class AnalyzeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AnalysisService analysisService;

    @Test
    void returnsOkWithTheAnalysisResultOnSuccess() throws Exception {
        DnsResult dns = new DnsResult("example.com", List.of("93.184.216.34"), List.of(), 12L, true);
        TcpResult tcp = TcpResult.success("93.184.216.34", 443, 8L);
        TlsResult tls = new TlsResult("TLSv1.3", "TLS_AES_128_GCM_SHA256", "CN=example.com", "CN=Test CA", 20L);
        Probes probes = new Probes(dns, tcp, tls);
        when(analysisService.analyze(any())).thenReturn(
                new AnalyzeResponse("https://example.com", 200, "HTTP/2", 1234L, "text/html",
                        100L, 23L, false, 123L, probes));

        mockMvc.perform(post("/api/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://example.com"))
                .andExpect(jsonPath("$.probes.dns.hostname").value("example.com"))
                .andExpect(jsonPath("$.probes.dns.resolvedIpv4[0]").value("93.184.216.34"))
                .andExpect(jsonPath("$.probes.dns.resolvedIpv6").isEmpty())
                .andExpect(jsonPath("$.probes.dns.durationMs").value(12))
                .andExpect(jsonPath("$.probes.dns.success").value(true))
                .andExpect(jsonPath("$.probes.tcp.host").value("93.184.216.34"))
                .andExpect(jsonPath("$.probes.tcp.port").value(443))
                .andExpect(jsonPath("$.probes.tcp.durationMs").value(8))
                .andExpect(jsonPath("$.probes.tls.tlsVersion").value("TLSv1.3"))
                .andExpect(jsonPath("$.probes.tls.cipherSuite").value("TLS_AES_128_GCM_SHA256"))
                .andExpect(jsonPath("$.probes.tls.certificateSubject").value("CN=example.com"))
                .andExpect(jsonPath("$.probes.tls.certificateIssuer").value("CN=Test CA"))
                .andExpect(jsonPath("$.probes.tls.durationMs").value(20))
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.protocol").value("HTTP/2"))
                .andExpect(jsonPath("$.contentLength").value(1234))
                .andExpect(jsonPath("$.contentType").value("text/html"))
                .andExpect(jsonPath("$.ttfbMs").value(100))
                .andExpect(jsonPath("$.downloadMs").value(23))
                .andExpect(jsonPath("$.bodyTruncated").value(false))
                .andExpect(jsonPath("$.totalTimeMs").value(123));
    }

    @Test
    void returnsNullTlsAndHttp1_1ProtocolForAnHttpUrl() throws Exception {
        DnsResult dns = new DnsResult("example.com", List.of("93.184.216.34"), List.of(), 12L, true);
        TcpResult tcp = TcpResult.success("93.184.216.34", 80, 8L);
        Probes probes = new Probes(dns, tcp, null);
        when(analysisService.analyze(any())).thenReturn(
                new AnalyzeResponse("http://example.com", 200, "HTTP/1.1", null, "text/plain",
                        100L, 23L, false, 123L, probes));

        mockMvc.perform(post("/api/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"http://example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.probes.tls").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.protocol").value("HTTP/1.1"))
                .andExpect(jsonPath("$.contentLength").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.contentType").value("text/plain"));
    }

    @Test
    void returnsAConsistentErrorBodyWhenTheUrlIsBlank() throws Exception {
        mockMvc.perform(post("/api/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void returnsAConsistentErrorBodyWhenTheRequestBodyIsMalformed() throws Exception {
        mockMvc.perform(post("/api/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));
    }

    @Test
    void returnsAConsistentErrorBodyWhenTheServiceRejectsTheUrlAsInvalid() throws Exception {
        when(analysisService.analyze(any())).thenThrow(new InvalidUrlException("ftp://example.com"));

        mockMvc.perform(post("/api/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"ftp://example.com\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("INVALID_URL"))
                .andExpect(jsonPath("$.message").value("Not a valid http/https URL: ftp://example.com"));
    }

    @Test
    void returnsAConsistentErrorBodyOnConnectionFailure() throws Exception {
        when(analysisService.analyze(any())).thenThrow(new AnalysisException(
                AnalysisException.Reason.CONNECTION_FAILURE, "Failed to connect to https://example.com",
                new RuntimeException("internal socket detail that should not leak")));

        mockMvc.perform(post("/api/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502))
                .andExpect(jsonPath("$.error").value("CONNECTION_FAILURE"))
                .andExpect(jsonPath("$.message").value("Failed to connect to https://example.com"));
    }

    @Test
    void returnsAConsistentErrorBodyOnDnsFailure() throws Exception {
        when(analysisService.analyze(any())).thenThrow(new AnalysisException(
                AnalysisException.Reason.DNS_FAILURE, "Could not resolve host for https://example.invalid",
                new RuntimeException()));

        mockMvc.perform(post("/api/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.invalid\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502))
                .andExpect(jsonPath("$.error").value("DNS_FAILURE"));
    }

    @Test
    void returnsAConsistentErrorBodyOnTimeout() throws Exception {
        when(analysisService.analyze(any())).thenThrow(new AnalysisException(
                AnalysisException.Reason.TIMEOUT, "Request to https://example.com timed out",
                new RuntimeException()));

        mockMvc.perform(post("/api/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com\"}"))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.status").value(504))
                .andExpect(jsonPath("$.error").value("TIMEOUT"));
    }

    @Test
    void returnsAConsistentErrorBodyOnAnInvalidUpstreamResponse() throws Exception {
        when(analysisService.analyze(any())).thenThrow(new AnalysisException(
                AnalysisException.Reason.INVALID_RESPONSE, "Invalid response from https://example.com",
                new RuntimeException()));

        mockMvc.perform(post("/api/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502))
                .andExpect(jsonPath("$.error").value("INVALID_RESPONSE"));
    }

    @Test
    void returnsAGenericErrorBodyForAnUnexpectedFailureWithoutLeakingItsDetails() throws Exception {
        when(analysisService.analyze(any()))
                .thenThrow(new RuntimeException("connection pool internals: leaked socket at 10.0.4.2:5432"));

        mockMvc.perform(post("/api/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.error").value("ANALYSIS_FAILED"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred while analyzing the URL"));
    }
}
