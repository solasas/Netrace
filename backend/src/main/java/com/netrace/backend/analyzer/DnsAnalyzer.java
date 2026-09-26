package com.netrace.backend.analyzer;

import com.netrace.backend.dto.DnsMetadata;
import com.netrace.backend.dto.PhaseResult;
import org.springframework.stereotype.Component;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;

/**
 * Resolves the hostname of an already-validated URL and reports the
 * resolved addresses, split into IPv4 and IPv6, and elapsed resolution
 * time.
 * <p>
 * <b>What is measured:</b> wall-clock time from immediately before to
 * immediately after one call to {@code InetAddress.getAllByName}
 * (real resolver; {@link Resolver} is the test seam). That is the
 * entire measurement - a single timer around a single blocking JDK
 * call.
 * <p>
 * <b>What that does and does not mean:</b> it is real, observed time
 * this specific call took - not a synthetic or estimated figure. It is
 * <i>not</i> the time for one DNS packet exchange: a single call can
 * involve separate A and AAAA queries, a search-domain fallback, or
 * multiple recursive hops behind the scenes, all collapsed into this
 * one number. There is no visibility here into which nameserver
 * answered, whether UDP or TCP was used, or how many packets or
 * retries were involved - none of that is observable through this
 * (or any standard) JDK API without packet capture.
 * <p>
 * <b>Caching - the biggest source of a misleading-looking number:</b>
 * the JVM's own resolver cache
 * ({@code sun.net.InetAddressCachePolicy}, governed by the
 * {@code networkaddress.cache.ttl} security property) caches
 * <i>successful</i> lookups for a configurable TTL - and when no
 * {@code SecurityManager} is installed (the normal case for this
 * application), the default is to cache successful results
 * <b>indefinitely for the life of the JVM process</b>. Failed lookups
 * are cached far more briefly ({@code networkaddress.cache.negative.ttl},
 * default 10 seconds). Below the JVM, the operating system may run its
 * own resolver/cache (e.g. systemd-resolved) that this class has no
 * visibility into or control over. Practically: resolving the same
 * hostname twice in one running instance of this application can
 * report a near-zero durationMs the second time - not because DNS got
 * faster, but because no network round trip happened at all. This
 * class does not disable that caching (see the class-level rationale
 * in docs/measurement.md's DNS section for why not: doing so would be
 * an invasive, process-wide JVM setting affecting every DNS lookup the
 * whole application makes, and would make the measurement less
 * representative of real-world behavior, not more - most real
 * applications benefit from exactly this caching). The number is
 * disclosed as what it is instead: a real measurement that may reflect
 * a cache hit, not a fresh authoritative query.
 * <p>
 * <b>Resolver control:</b> this class does not implement or select a
 * DNS resolver itself. It fully delegates to {@code InetAddress},
 * which delegates to whatever resolver the OS is configured to use
 * (e.g. {@code /etc/resolv.conf} on Linux). There is no way from here
 * to force a specific nameserver or bypass every layer of caching
 * described above.
 * <p>
 * <b>IPv4 vs IPv6:</b> resolved addresses are split by concrete type
 * ({@code Inet4Address} vs {@code Inet6Address}) into two separate
 * lists. This reflects which address families a DNS lookup actually
 * returned (A vs AAAA records) - it does not indicate a "preferred" or
 * "faster" address; which specific address the OS/JVM would actually
 * choose to connect to is governed by separate address-selection rules
 * (RFC 6724) that this class does not apply or report on.
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

        List<String> resolvedIpv4 = Arrays.stream(addresses)
                .filter(Inet4Address.class::isInstance)
                .map(InetAddress::getHostAddress)
                .toList();
        List<String> resolvedIpv6 = Arrays.stream(addresses)
                .filter(Inet6Address.class::isInstance)
                .map(InetAddress::getHostAddress)
                .toList();

        return PhaseResult.success(PHASE, durationMs, new DnsMetadata(hostname, resolvedIpv4, resolvedIpv6));
    }
}
