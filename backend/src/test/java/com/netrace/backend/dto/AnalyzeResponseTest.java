package com.netrace.backend.dto;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AnalyzeResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serializesToTheExpectedJsonFieldsForAnHttpsResponse() throws Exception {
        DnsResult dns = new DnsResult("example.com", List.of("93.184.216.34"), List.of(), 12L, true);
        TcpResult tcp = new TcpResult("93.184.216.34", 443, 8L);
        TlsResult tls = new TlsResult("TLSv1.3", "TLS_AES_128_GCM_SHA256", "CN=example.com", "CN=Test CA", 20L);
        Probes probes = new Probes(dns, tcp, tls);
        AnalyzeResponse response = new AnalyzeResponse(
                "https://example.com", 200, "HTTP/2", 1234L, "text/html", 100L, 23L, false, 123L, probes);

        String json = objectMapper.writeValueAsString(response);

        assertThat(objectMapper.readTree(json))
                .isEqualTo(objectMapper.readTree("""
                        {
                          "url": "https://example.com",
                          "statusCode": 200,
                          "protocol": "HTTP/2",
                          "contentLength": 1234,
                          "contentType": "text/html",
                          "ttfbMs": 100,
                          "downloadMs": 23,
                          "bodyTruncated": false,
                          "totalTimeMs": 123,
                          "probes": {
                            "dns": {
                              "hostname": "example.com",
                              "resolvedIpv4": ["93.184.216.34"],
                              "resolvedIpv6": [],
                              "durationMs": 12,
                              "success": true
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
                            }
                          }
                        }
                        """));
    }

    @Test
    void serializesTlsAsNullForAnHttpResponse() throws Exception {
        DnsResult dns = new DnsResult("example.com", List.of("93.184.216.34"), List.of(), 12L, true);
        TcpResult tcp = new TcpResult("93.184.216.34", 80, 8L);
        Probes probes = new Probes(dns, tcp, null);
        AnalyzeResponse response = new AnalyzeResponse(
                "http://example.com", 200, "HTTP/1.1", null, "text/plain", 100L, 23L, false, 123L, probes);

        String json = objectMapper.writeValueAsString(response);

        assertThat(objectMapper.readTree(json).get("probes").get("tls").isNull()).isTrue();
        assertThat(objectMapper.readTree(json).get("protocol").asString()).isEqualTo("HTTP/1.1");
        assertThat(objectMapper.readTree(json).get("contentLength").isNull())
                .as("contentLength should serialize as null, not 0, when the server sent no Content-Length header")
                .isTrue();
        assertThat(objectMapper.readTree(json).get("contentType").asString()).isEqualTo("text/plain");
    }

    @Test
    void roundTripsThroughDeserialization() throws Exception {
        String json = """
                {
                  "url": "https://example.com",
                  "statusCode": 404,
                  "protocol": "HTTP/2",
                  "contentLength": 1234,
                  "contentType": "text/html",
                  "ttfbMs": 900,
                  "downloadMs": 87,
                  "bodyTruncated": false,
                  "totalTimeMs": 987,
                  "probes": {
                    "dns": {
                      "hostname": "example.com",
                      "resolvedIpv4": ["93.184.216.34"],
                      "resolvedIpv6": [],
                      "durationMs": 12,
                      "success": true
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
                    }
                  }
                }
                """;

        AnalyzeResponse response = objectMapper.readValue(json, AnalyzeResponse.class);

        DnsResult dns = new DnsResult("example.com", List.of("93.184.216.34"), List.of(), 12L, true);
        TcpResult tcp = new TcpResult("93.184.216.34", 443, 8L);
        TlsResult tls = new TlsResult("TLSv1.3", "TLS_AES_128_GCM_SHA256", "CN=example.com", "CN=Test CA", 20L);
        Probes probes = new Probes(dns, tcp, tls);
        assertThat(response).isEqualTo(new AnalyzeResponse(
                "https://example.com", 404, "HTTP/2", 1234L, "text/html", 900L, 87L, false, 987L, probes));
    }
}
