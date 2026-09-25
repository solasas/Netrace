package com.netrace.backend.dto;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AnalyzeResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serializesToTheExpectedJsonFieldsForAnHttpsResponse() throws Exception {
        DnsResult dns = new DnsResult("example.com", List.of("93.184.216.34"), 12L);
        TcpResult tcp = new TcpResult("93.184.216.34", 443, 8L);
        TlsResult tls = new TlsResult("TLSv1.3", "TLS_AES_128_GCM_SHA256", "CN=example.com", "CN=Test CA", 20L);
        AnalyzeResponse response = new AnalyzeResponse("https://example.com", dns, tcp, tls, 200, 123L);

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
                          "tcp": {
                            "host": "93.184.216.34",
                            "port": 443,
                            "durationMs": 8
                          },
                          "tls": {
                            "tlsVersion": "TLSv1.3",
                            "cipherSuite": "TLS_AES_128_GCM_SHA256",
                            "certificateSubject": "CN=example.com",
                            "certificateIssuer": "CN=Test CA",
                            "durationMs": 20
                          },
                          "statusCode": 200,
                          "totalTimeMs": 123
                        }
                        """));
    }

    @Test
    void serializesTlsAsNullForAnHttpResponse() throws Exception {
        DnsResult dns = new DnsResult("example.com", List.of("93.184.216.34"), 12L);
        TcpResult tcp = new TcpResult("93.184.216.34", 80, 8L);
        AnalyzeResponse response = new AnalyzeResponse("http://example.com", dns, tcp, null, 200, 123L);

        String json = objectMapper.writeValueAsString(response);

        assertThat(objectMapper.readTree(json).get("tls").isNull()).isTrue();
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
                  "tcp": {
                    "host": "93.184.216.34",
                    "port": 443,
                    "durationMs": 8
                  },
                  "tls": {
                    "tlsVersion": "TLSv1.3",
                    "cipherSuite": "TLS_AES_128_GCM_SHA256",
                    "certificateSubject": "CN=example.com",
                    "certificateIssuer": "CN=Test CA",
                    "durationMs": 20
                  },
                  "statusCode": 404,
                  "totalTimeMs": 987
                }
                """;

        AnalyzeResponse response = objectMapper.readValue(json, AnalyzeResponse.class);

        DnsResult dns = new DnsResult("example.com", List.of("93.184.216.34"), 12L);
        TcpResult tcp = new TcpResult("93.184.216.34", 443, 8L);
        TlsResult tls = new TlsResult("TLSv1.3", "TLS_AES_128_GCM_SHA256", "CN=example.com", "CN=Test CA", 20L);
        assertThat(response).isEqualTo(new AnalyzeResponse("https://example.com", dns, tcp, tls, 404, 987L));
    }
}
