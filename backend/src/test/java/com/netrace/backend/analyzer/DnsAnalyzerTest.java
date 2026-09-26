package com.netrace.backend.analyzer;

import com.netrace.backend.dto.DnsMetadata;
import com.netrace.backend.dto.PhaseResult;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class DnsAnalyzerTest {

    private final DnsAnalyzer analyzer = new DnsAnalyzer();

    private static List<String> allResolvedIps(DnsMetadata metadata) {
        return Stream.concat(metadata.resolvedIpv4().stream(), metadata.resolvedIpv6().stream()).toList();
    }

    @Test
    void resolvesLocalhostToALoopbackAddress() throws UnknownHostException {
        PhaseResult<DnsMetadata> result = analyzer.analyze("http://localhost:8080/");

        assertThat(result.phase()).isEqualTo("DNS");
        assertThat(result.status()).isEqualTo(PhaseResult.Status.SUCCESS);
        assertThat(result.metadata().hostname()).isEqualTo("localhost");
        List<String> allIps = allResolvedIps(result.metadata());
        assertThat(allIps).isNotEmpty();
        for (String ip : allIps) {
            assertThat(InetAddress.getByName(ip).isLoopbackAddress()).isTrue();
        }
        assertThat(result.durationMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void extractsTheHostnameWithoutThePortOrPath() {
        PhaseResult<DnsMetadata> result = analyzer.analyze("http://localhost:12345/some/path?x=1");

        assertThat(result.metadata().hostname()).isEqualTo("localhost");
    }

    @Test
    void resolvesARealStableHostname() {
        PhaseResult<DnsMetadata> result = analyzer.analyze("https://example.com");

        assertThat(result.metadata().hostname()).isEqualTo("example.com");
        assertThat(allResolvedIps(result.metadata())).isNotEmpty();
        assertThat(result.durationMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void resolvesAnIpv4LiteralHost() {
        PhaseResult<DnsMetadata> result = analyzer.analyze("http://127.0.0.1:8080/");

        assertThat(result.metadata().hostname()).isEqualTo("127.0.0.1");
        assertThat(result.metadata().resolvedIpv4()).containsExactly("127.0.0.1");
        assertThat(result.metadata().resolvedIpv6()).isEmpty();
    }

    @Test
    void resolvesAnIpv6LiteralHostWhenIpv6IsSupported() throws UnknownHostException {
        boolean ipv6Supported;
        try {
            ipv6Supported = InetAddress.getByName("::1").isLoopbackAddress();
        } catch (UnknownHostException e) {
            ipv6Supported = false;
        }
        assumeTrue(ipv6Supported, "IPv6 loopback is not available in this environment");

        PhaseResult<DnsMetadata> result = analyzer.analyze("http://[::1]:8080/");

        assertThat(result.metadata().resolvedIpv6()).isNotEmpty();
        assertThat(result.metadata().resolvedIpv4()).isEmpty();
        for (String ip : result.metadata().resolvedIpv6()) {
            assertThat(InetAddress.getByName(ip).isLoopbackAddress()).isTrue();
        }
    }

    @Test
    void splitsResolvedAddressesByFamilyWhenMultipleAreResolved() throws UnknownHostException {
        DnsAnalyzer multiAddressAnalyzer = new DnsAnalyzer(hostname -> new InetAddress[]{
                InetAddress.getByName("203.0.113.1"),
                InetAddress.getByName("203.0.113.2"),
                InetAddress.getByName("2001:db8::1")
        });

        PhaseResult<DnsMetadata> result = multiAddressAnalyzer.analyze("https://multi.example/");

        assertThat(result.metadata().resolvedIpv4()).containsExactly("203.0.113.1", "203.0.113.2");
        assertThat(result.metadata().resolvedIpv6()).containsExactly("2001:db8:0:0:0:0:0:1");
    }

    @Test
    void throwsADnsFailureForANonexistentDomainOrAnyOtherResolutionFailure() {
        // Java's resolver reports NXDOMAIN and other resolution failures
        // (e.g. an unreachable resolver) identically as
        // UnknownHostException, so both are handled the same way here.
        DnsAnalyzer failingAnalyzer = new DnsAnalyzer(hostname -> {
            throw new UnknownHostException(hostname);
        });

        assertThatThrownBy(() -> failingAnalyzer.analyze("http://does-not-resolve.example/"))
                .isInstanceOf(AnalysisException.class)
                .extracting(e -> ((AnalysisException) e).reason())
                .isEqualTo(AnalysisException.Reason.DNS_FAILURE);
    }

    @Test
    void throwsADnsFailureWhenTheResolverReturnsNoAddresses() {
        DnsAnalyzer emptyResultAnalyzer = new DnsAnalyzer(hostname -> new InetAddress[0]);

        assertThatThrownBy(() -> emptyResultAnalyzer.analyze("http://no-addresses.example/"))
                .isInstanceOf(AnalysisException.class)
                .extracting(e -> ((AnalysisException) e).reason())
                .isEqualTo(AnalysisException.Reason.DNS_FAILURE);
    }

    @Test
    void reportsAMeaningfulMessageWithoutTheUnderlyingExceptionDetails() {
        String sensitiveInternalDetail = "resolver thread pool-4-thread-7 socket fd=42 at 10.0.4.2";
        DnsAnalyzer failingAnalyzer = new DnsAnalyzer(hostname -> {
            throw new UnknownHostException(sensitiveInternalDetail);
        });

        AnalysisException thrown = (AnalysisException) catchThrowable(
                () -> failingAnalyzer.analyze("http://does-not-resolve.example/"));

        assertThat(thrown.getMessage()).isEqualTo("Could not resolve host: does-not-resolve.example");
        assertThat(thrown.getMessage()).doesNotContain(sensitiveInternalDetail);
        assertThat(thrown.getCause()).hasMessage(sensitiveInternalDetail);
    }
}
