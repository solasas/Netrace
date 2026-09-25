package com.netrace.backend.security;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Classifies an already-resolved IP address as safe or unsafe to make
 * an outbound connection to. This project fetches arbitrary
 * user-supplied URLs server-side, which makes it a Server-Side Request
 * Forgery (SSRF) surface: without this check, a URL could point at the
 * host's own loopback interface, its private network, a link-local
 * cloud metadata endpoint (e.g. 169.254.169.254), or other addresses a
 * public caller should never be able to reach through this service.
 * <p>
 * Deliberately validates the resolved IP address, not the original
 * hostname string. Hostname-based blocklisting (checking whether the
 * URL's host text is "localhost", starts with "127.", etc.) is
 * trivially bypassed - by alternate numeric representations (decimal,
 * octal, hex), custom DNS records, or /etc/hosts-style overrides - and
 * is unnecessary once every resolved address is checked here instead.
 * Whatever representation a hostname resolves through, the resolver
 * ultimately produces the same InetAddress byte pattern this class
 * inspects, so alternate representations are covered without any
 * hostname-string parsing.
 * <p>
 * See docs/security.md for the full threat model and, importantly,
 * this project's known remaining limitations. This class only
 * classifies one already-resolved address; it does not by itself close
 * gaps like DNS rebinding or redirect-based bypasses - those are
 * addressed, with disclosed limits, at the call sites that use it
 * (AnalysisService and HttpAnalyzer).
 */
public final class SsrfGuard {

    private SsrfGuard() {
    }

    public static boolean isBlocked(InetAddress address) {
        if (address instanceof Inet6Address v6) {
            InetAddress mapped = extractIpv4Mapped(v6);
            if (mapped != null) {
                return isBlocked(mapped);
            }
        }

        if (address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isAnyLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }

        if (address instanceof Inet4Address v4) {
            return isCarrierGradeNat(v4) || isLimitedBroadcast(v4);
        }
        if (address instanceof Inet6Address v6) {
            return isUniqueLocalIpv6(v6);
        }
        return false;
    }

    /**
     * 100.64.0.0/10 - RFC 6598 "shared address space" reserved for
     * carrier-grade NAT. Not covered by isSiteLocalAddress() or
     * isLinkLocalAddress(), but routinely used for internal-only
     * infrastructure and never a legitimate public analysis target.
     */
    private static boolean isCarrierGradeNat(Inet4Address address) {
        byte[] b = address.getAddress();
        int first = b[0] & 0xFF;
        int second = b[1] & 0xFF;
        return first == 100 && (second & 0xC0) == 64;
    }

    /** 255.255.255.255 - the limited broadcast address. */
    private static boolean isLimitedBroadcast(Inet4Address address) {
        for (byte octet : address.getAddress()) {
            if ((octet & 0xFF) != 255) {
                return false;
            }
        }
        return true;
    }

    /**
     * fc00::/7 - IPv6 unique local addresses (ULA), the modern
     * replacement for the deprecated site-local range that
     * isSiteLocalAddress() already covers (fec0::/10). Not caught by
     * any built-in InetAddress classifier.
     */
    private static boolean isUniqueLocalIpv6(Inet6Address address) {
        byte[] b = address.getAddress();
        return (b[0] & 0xFE) == 0xFC;
    }

    /**
     * If address is an IPv4-mapped IPv6 address (::ffff:a.b.c.d, the
     * ::ffff:0:0/96 block), returns the embedded IPv4 address so it can
     * be classified by the same rules as a literal IPv4 address, rather
     * than relying on whether Inet6Address's own built-in classifier
     * methods happen to unwrap mapped addresses for every check used
     * above (isLoopbackAddress documents that it does; the others do
     * not make the same guarantee). Returns null for any other address.
     */
    private static InetAddress extractIpv4Mapped(Inet6Address address) {
        byte[] bytes = address.getAddress();
        for (int i = 0; i < 10; i++) {
            if (bytes[i] != 0) {
                return null;
            }
        }
        if ((bytes[10] & 0xFF) != 0xFF || (bytes[11] & 0xFF) != 0xFF) {
            return null;
        }
        byte[] v4 = {bytes[12], bytes[13], bytes[14], bytes[15]};
        try {
            return InetAddress.getByAddress(v4);
        } catch (UnknownHostException e) {
            // getByAddress only throws for the wrong array length (here
            // always 4), so this is unreachable in practice.
            return null;
        }
    }
}
