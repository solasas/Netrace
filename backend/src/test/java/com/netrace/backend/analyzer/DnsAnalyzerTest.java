package com.netrace.backend.analyzer;

import com.netrace.backend.dto.DnsResult;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DnsAnalyzerTest {

    private final DnsAnalyzer analyzer = new DnsAnalyzer();

    @Test
    void resolvesLocalhostToALoopbackAddress() throws UnknownHostException {
        DnsResult result = analyzer.analyze("http://localhost:8080/");

        assertThat(result.hostname()).isEqualTo("localhost");
        assertThat(result.resolvedIps()).isNotEmpty();
        for (String ip : result.resolvedIps()) {
            assertThat(InetAddress.getByName(ip).isLoopbackAddress()).isTrue();
        }
        assertThat(result.durationMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void extractsTheHostnameWithoutThePortOrPath() {
        DnsResult result = analyzer.analyze("http://localhost:12345/some/path?x=1");

        assertThat(result.hostname()).isEqualTo("localhost");
    }

    @Test
    void resolvesARealStableHostname() {
        DnsResult result = analyzer.analyze("https://example.com");

        assertThat(result.hostname()).isEqualTo("example.com");
        assertThat(result.resolvedIps()).isNotEmpty();
        assertThat(result.durationMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void throwsADnsFailureWhenResolutionFails() {
        DnsAnalyzer failingAnalyzer = new DnsAnalyzer(hostname -> {
            throw new UnknownHostException(hostname);
        });

        assertThatThrownBy(() -> failingAnalyzer.analyze("http://does-not-resolve.example/"))
                .isInstanceOf(AnalysisException.class)
                .extracting(e -> ((AnalysisException) e).reason())
                .isEqualTo(AnalysisException.Reason.DNS_FAILURE);
    }
}
