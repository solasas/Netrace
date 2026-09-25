package com.netrace.backend.analyzer;

import com.netrace.backend.dto.DnsMetadata;
import com.netrace.backend.dto.PhaseResult;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;

/**
 * Resolves the hostname of an already-validated URL and reports the
 * resolved addresses (IPv4 and/or IPv6, all of them, in whatever order
 * the platform resolver returns) and elapsed resolution time. durationMs
 * is wall-clock time around the JVM's blocking resolver call - it can
 * reflect an OS/JVM DNS cache hit rather than a real network round
 * trip, and says nothing about individual DNS packets, retries, or
 * which nameserver answered. There is no packet-level DNS timing here.
 * <p>
 * Java's resolver cannot distinguish a nonexistent domain (NXDOMAIN)
 * from other resolution failures (e.g. the resolver itself being
 * unreachable) - both surface as the same UnknownHostException, so both
 * are reported identically here as DNS_FAILURE. The exception message
 * is one this class crafts itself; the underlying exception (whose
 * message can be OS-specific) is kept only as the cause, for
 * server-side logging, and is never sent to the client.
 */
@Component
public class DnsAnalyzer {

    public static final String PHASE = "DNS";

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

    public PhaseResult<DnsMetadata> analyze(String url) {
        String hostname = URI.create(url).getHost();

        long startNanos = System.nanoTime();
        InetAddress[] addresses;
        try {
            addresses = resolver.resolve(hostname);
        } catch (UnknownHostException e) {
            throw new AnalysisException(AnalysisException.Reason.DNS_FAILURE,
                    "Could not resolve host: " + hostname, e);
        }
        if (addresses.length == 0) {
            // Not expected from the real resolver (it throws rather than
            // returning nothing), but a defensive guard against ever
            // reporting a silent "success" with no addresses.
            throw new AnalysisException(AnalysisException.Reason.DNS_FAILURE,
                    "Could not resolve host: " + hostname, null);
        }
        long durationMs = (System.nanoTime() - startNanos) / 1_000_000;

        List<String> resolvedIps = Arrays.stream(addresses)
                .map(InetAddress::getHostAddress)
                .toList();

        return PhaseResult.success(PHASE, durationMs, new DnsMetadata(hostname, resolvedIps));
    }
}
