package com.netrace.backend.dto;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class AnalyzeResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serializesToTheExpectedJsonFields() throws Exception {
        AnalyzeResponse response = new AnalyzeResponse("https://example.com", 200, 123L);

        String json = objectMapper.writeValueAsString(response);

        assertThat(objectMapper.readTree(json))
                .isEqualTo(objectMapper.readTree("""
                        {
                          "url": "https://example.com",
                          "statusCode": 200,
                          "totalTimeMs": 123
                        }
                        """));
    }

    @Test
    void roundTripsThroughDeserialization() throws Exception {
        String json = """
                {
                  "url": "https://example.com",
                  "statusCode": 404,
                  "totalTimeMs": 987
                }
                """;

        AnalyzeResponse response = objectMapper.readValue(json, AnalyzeResponse.class);

        assertThat(response).isEqualTo(new AnalyzeResponse("https://example.com", 404, 987L));
    }
}
