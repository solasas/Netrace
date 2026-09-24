package com.netrace.backend.dto;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AnalyzeResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serializesToTheExpectedJsonFields() throws Exception {
        DnsResult dns = new DnsResult("example.com", List.of("93.184.216.34"), 12L);
        AnalyzeResponse response = new AnalyzeResponse("https://example.com", dns, 200, 123L);

        String json = objectMapper.writeValueAsString(response);

        assertThat(objectMapper.readTree(json))
                .isEqualTo(objectMapper.readTree("""
                        {
                          "url": "https://example.com",
                          "dns": {
                            "hostname": "example.com",
                            "resolvedIps": ["93.184.216.34"],
                            "durationMs": 12
                          },
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
                  "dns": {
                    "hostname": "example.com",
                    "resolvedIps": ["93.184.216.34"],
                    "durationMs": 12
                  },
                  "statusCode": 404,
                  "totalTimeMs": 987
                }
                """;

        AnalyzeResponse response = objectMapper.readValue(json, AnalyzeResponse.class);

        DnsResult dns = new DnsResult("example.com", List.of("93.184.216.34"), 12L);
        assertThat(response).isEqualTo(new AnalyzeResponse("https://example.com", dns, 404, 987L));
    }
}
