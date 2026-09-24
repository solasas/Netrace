package com.netrace.backend.controller;

import com.netrace.backend.analyzer.AnalysisException;
import com.netrace.backend.dto.AnalyzeResponse;
import com.netrace.backend.dto.DnsResult;
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
        DnsResult dns = new DnsResult("example.com", List.of("93.184.216.34"), 12L);
        when(analysisService.analyze(any())).thenReturn(
                new AnalyzeResponse("https://example.com", dns, 200, 123L));

        mockMvc.perform(post("/api/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://example.com"))
                .andExpect(jsonPath("$.dns.hostname").value("example.com"))
                .andExpect(jsonPath("$.dns.resolvedIps[0]").value("93.184.216.34"))
                .andExpect(jsonPath("$.dns.durationMs").value(12))
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.totalTimeMs").value(123));
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
