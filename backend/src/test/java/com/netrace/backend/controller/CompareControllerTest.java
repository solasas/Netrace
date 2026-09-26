package com.netrace.backend.controller;

import com.netrace.backend.dto.CompareResponse;
import com.netrace.backend.dto.CompareResult;
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
        when(comparisonService.compare(any())).thenReturn(new CompareResponse(List.of(
                new CompareResult("https://a.example.com", true, 200, "HTTP/2", 120L, null),
                new CompareResult("https://b.example.com", false, null, null, null, "Connection refused by b"))));

        mockMvc.perform(post("/api/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"urls\":[\"https://a.example.com\",\"https://b.example.com\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].url").value("https://a.example.com"))
                .andExpect(jsonPath("$.results[0].success").value(true))
                .andExpect(jsonPath("$.results[0].statusCode").value(200))
                .andExpect(jsonPath("$.results[0].totalTimeMs").value(120))
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
