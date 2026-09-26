package com.netrace.backend.controller;

import com.netrace.backend.dto.CompareResponse;
import com.netrace.backend.dto.CompareResult;
import com.netrace.backend.dto.DnsResult;
import com.netrace.backend.dto.HttpResult;
import com.netrace.backend.dto.TcpResult;
import com.netrace.backend.dto.TlsResult;
import com.netrace.backend.service.ComparisonService;
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

@WebMvcTest(CompareController.class)
class CompareControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ComparisonService comparisonService;

    @Test
    void returnsOkWithAMixOfSuccessAndFailureResults() throws Exception {
        DnsResult dns = new DnsResult("a.example.com", List.of("93.184.216.34"), List.of(), 12L, true);
        TcpResult tcp = TcpResult.success("93.184.216.34", 443, 8L);
        TlsResult tls = new TlsResult("TLSv1.3", "TLS_AES_256_GCM_SHA384", "CN=example.com", "CN=Example CA", 15L);
        HttpResult httpSuccess = new HttpResult("https://a.example.com", 200, 120L, 50L, 70L, false, "HTTP/2", 1024L, "text/html", "https://a.example.com", 0, 1024L);
        when(comparisonService.compare(any())).thenReturn(new CompareResponse(List.of(
                CompareResult.success("https://a.example.com", httpSuccess, dns, tcp, tls),
                CompareResult.failure("https://b.example.com", "Connection refused by b"))));

        mockMvc.perform(post("/api/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"urls\":[\"https://a.example.com\",\"https://b.example.com\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].url").value("https://a.example.com"))
                .andExpect(jsonPath("$.results[0].success").value(true))
                .andExpect(jsonPath("$.results[0].statusCode").value(200))
                .andExpect(jsonPath("$.results[0].totalTimeMs").value(120))
                .andExpect(jsonPath("$.results[0].dns").isNotEmpty())
                .andExpect(jsonPath("$.results[0].tcp").isNotEmpty())
                .andExpect(jsonPath("$.results[1].url").value("https://b.example.com"))
                .andExpect(jsonPath("$.results[1].success").value(false))
                .andExpect(jsonPath("$.results[1].error").value("Connection refused by b"));
    }

    @Test
    void rejectsFewerThanTwoUrls() throws Exception {
        mockMvc.perform(post("/api/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"urls\":[\"https://example.com\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));
    }

    @Test
    void rejectsMoreThanFiveUrls() throws Exception {
        String urls = "[\"https://a.com\",\"https://b.com\",\"https://c.com\",\"https://d.com\","
                + "\"https://e.com\",\"https://f.com\"]";

        mockMvc.perform(post("/api/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"urls\":" + urls + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));
    }

    @Test
    void rejectsABlankUrlInTheList() throws Exception {
        mockMvc.perform(post("/api/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"urls\":[\"https://example.com\",\"\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));
    }

    @Test
    void rejectsAMissingUrlsField() throws Exception {
        mockMvc.perform(post("/api/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));
    }
}
