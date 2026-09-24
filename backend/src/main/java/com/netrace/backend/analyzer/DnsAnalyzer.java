package com.netrace.backend.analyzer;

import com.netrace.backend.dto.DnsResult;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;

/**
 * Resolves the hostname of an already-validated URL and reports the
 * resolved addresses and elapsed resolution time. durationMs is
 * wall-clock time around the JVM's blocking resolver call - it can
 * reflect an OS/JVM DNS cache hit rather than a real network round
 * trip, and says nothing about individual DNS packets, retries, or
 * which nameserver answered. There is no packet-level DNS timing here.
 */
@Component
public class DnsAnalyzer {

    @FunctionalInterface
    interface Resolver {
        InetAddress[] resolve(String hostname) throws UnknownHostException;
    }

    private final Resolver resolver;

    public DnsAnalyzer() {
        this(InetAddress::getAllByName);
    }

    DnsAnalyzer(Resolver resolver) {
        this.resolver = resolver;
    }

    public DnsResult analyze(String url) {
        String hostname = URI.create(url).getHost();

        long startNanos = System.nanoTime();
        InetAddress[] addresses;
        try {
            addresses = resolver.resolve(hostname);
        } catch (UnknownHostException e) {
            throw new AnalysisException(AnalysisException.Reason.DNS_FAILURE,
                    "Could not resolve host for " + url, e);
        }
        long durationMs = (System.nanoTime() - startNanos) / 1_000_000;

        List<String> resolvedIps = Arrays.stream(addresses)
                .map(InetAddress::getHostAddress)
                .toList();

        return new DnsResult(hostname, resolvedIps, durationMs);
    }
}
