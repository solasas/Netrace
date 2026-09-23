package com.netrace.backend.controller;

import com.netrace.backend.analyzer.AnalysisException;
import com.netrace.backend.dto.AnalyzeResponse;
import com.netrace.backend.service.AnalysisService;
import com.netrace.backend.service.InvalidUrlException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

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
        when(analysisService.analyze(any())).thenReturn(
                new AnalyzeResponse("https://example.com", 200, 123L));

        mockMvc.perform(post("/api/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://example.com"))
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.totalTimeMs").value(123));
    }

    @Test
    void returnsBadRequestWhenTheUrlIsBlank() throws Exception {
        mockMvc.perform(post("/api/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsBadRequestWhenTheRequestBodyIsMalformed() throws Exception {
        mockMvc.perform(post("/api/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("not json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsBadRequestWhenTheServiceRejectsTheUrlAsInvalid() throws Exception {
        when(analysisService.analyze(any())).thenThrow(new InvalidUrlException("ftp://example.com"));

        mockMvc.perform(post("/api/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"ftp://example.com\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsBadGatewayOnConnectionFailure() throws Exception {
        when(analysisService.analyze(any())).thenThrow(new AnalysisException(
                AnalysisException.Reason.CONNECTION_FAILURE, "boom", new RuntimeException()));

        mockMvc.perform(post("/api/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com\"}"))
                .andExpect(status().isBadGateway());
    }

    @Test
    void returnsGatewayTimeoutOnTimeout() throws Exception {
        when(analysisService.analyze(any())).thenThrow(new AnalysisException(
                AnalysisException.Reason.TIMEOUT, "boom", new RuntimeException()));

        mockMvc.perform(post("/api/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com\"}"))
                .andExpect(status().isGatewayTimeout());
    }

    @Test
    void returnsBadGatewayOnAnInvalidUpstreamResponse() throws Exception {
        when(analysisService.analyze(any())).thenThrow(new AnalysisException(
                AnalysisException.Reason.INVALID_RESPONSE, "boom", new RuntimeException()));

        mockMvc.perform(post("/api/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com\"}"))
                .andExpect(status().isBadGateway());
    }
}
