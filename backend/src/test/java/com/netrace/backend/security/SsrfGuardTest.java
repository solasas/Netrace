package com.netrace.backend.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SsrfGuardTest {

    @ParameterizedTest
    @ValueSource(strings = {
            // Loopback
            "127.0.0.1",
            "127.0.0.2",
            "127.255.255.255",
            "::1",
            // Private IPv4 (RFC 1918)
            "10.0.0.1",
            "10.255.255.255",
            "172.16.0.1",
            "172.31.255.255",
            "192.168.0.1",
            "192.168.255.255",
            // Link-local, including the AWS/GCP/Azure/DigitalOcean cloud
            // metadata address
            "169.254.0.1",
            "169.254.169.254",
            "fe80::1",
            // IPv6 unique local addresses (modern ULA, fc00::/7)
            "fc00::1",
            "fd12:3456:789a::1",
            // Deprecated IPv6 site-local (fec0::/10)
            "fec0::1",
            // Wildcard / "this network"
            "0.0.0.0",
            "::",
            // Carrier-grade NAT (RFC 6598)
            "100.64.0.1",
            "100.127.255.255",
            // Limited broadcast
            "255.255.255.255",
            // Multicast
            "224.0.0.1",
            "ff02::1",
            // Alternate representations: IPv4-mapped IPv6
            "::ffff:127.0.0.1",
            "::ffff:10.0.0.1",
            "::ffff:169.254.169.254",
            // Alternate representations JDK 21's InetAddress does
            // normalize: a plain decimal integer, and shorthand/
            // compressed dotted forms. See
            // doesNotMisinterpretALeadingZeroOctetAsOctal and
            // rejectsHexadecimalLiteralsOutright below for two classic
            // SSRF bypass tricks that do NOT apply to this JDK version,
            // verified rather than assumed.
            "2130706433",
            "127.1",
            "127.0.1",
    })
    void blocksKnownUnsafeAddresses(String literal) throws UnknownHostException {
        assertThat(SsrfGuard.isBlocked(InetAddress.getByName(literal)))
                .as("expected %s to be blocked", literal)
                .isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // Real public IPv4 addresses
            "8.8.8.8",
            "1.1.1.1",
            "93.184.216.34",
            // Just outside each private/reserved IPv4 range
            "9.255.255.255",
            "11.0.0.0",
            "172.15.255.255",
            "172.32.0.0",
            "192.167.255.255",
            "192.169.0.0",
            "169.253.255.255",
            "169.255.0.0",
            "100.63.255.255",
            "100.128.0.0",
            // Real public IPv6 addresses
            "2606:4700:10::ac42:93f3",
            "2001:4860:4860::8888",
            // A public address in IPv4-mapped IPv6 form
            "::ffff:8.8.8.8",
    })
    void doesNotBlockRealPublicAddresses(String literal) throws UnknownHostException {
        assertThat(SsrfGuard.isBlocked(InetAddress.getByName(literal)))
                .as("expected %s not to be blocked", literal)
                .isFalse();
    }

    @Test
    void doesNotMisinterpretALeadingZeroOctetAsOctal() throws UnknownHostException {
        // Classic SSRF folklore treats a leading zero in a dotted IPv4
        // octet as an octal-bypass trick for 127.0.0.1 (0177 == 127 in
        // octal). Verified against this JDK: it does not. "0177" is
        // read as decimal 177, producing a real, non-loopback address -
        // so this specific technique is not a bypass here, and
        // SsrfGuard correctly does not block it either, since it
        // genuinely isn't loopback.
        InetAddress address = InetAddress.getByName("0177.0.0.1");
        assertThat(address.getHostAddress()).isEqualTo("177.0.0.1");
        assertThat(address.isLoopbackAddress()).isFalse();
        assertThat(SsrfGuard.isBlocked(address)).isFalse();
    }

    @Test
    void rejectsHexadecimalLiteralsOutright() {
        // Another classic SSRF trick - a hex-encoded IPv4 literal like
        // 0x7f000001 for 127.0.0.1 - is not merely unrecognized as
        // loopback here, it fails to resolve at all: JDK 21's strict
        // numeric-literal parser refuses it outright, so it can never
        // reach SsrfGuard.isBlocked in the first place.
        assertThatThrownBy(() -> InetAddress.getByName("0x7f000001"))
                .isInstanceOf(UnknownHostException.class);
    }
}
