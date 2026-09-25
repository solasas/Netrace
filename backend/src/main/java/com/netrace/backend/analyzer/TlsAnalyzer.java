package com.netrace.backend.analyzer;

import com.netrace.backend.config.AnalyzerProperties;
import com.netrace.backend.dto.PhaseResult;
import com.netrace.backend.dto.TlsFailureReason;
import com.netrace.backend.dto.TlsMetadata;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.NoRouteToHostException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.security.GeneralSecurityException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.List;

/**
 * Opens a dedicated, fresh TCP connection to a resolved IP:port and
 * performs a real TLS handshake over it, measuring how long connection
 * establishment (TCP connect + TLS handshake) took and capturing what
 * the handshake reveals: negotiated TLS version, negotiated cipher
 * suite, and the server's leaf certificate subject/issuer.
 * <p>
 * hostname and resolvedIp are both required and serve different
 * purposes: resolvedIp is what the socket actually connects to (so
 * this doesn't quietly redo DNS resolution); hostname is what's sent
 * as the SNI value and what the server's certificate is verified
 * against - a resolved IP is never a valid SNI value or certificate
 * subject for a normal HTTPS host, so using it for that would either
 * break the handshake (servers that require SNI) or silently skip
 * hostname verification.
 * <p>
 * durationMs is wall-clock time from opening the dedicated TCP socket
 * through handshake completion, as observed by the JVM. This is not a
 * measurement of pure TLS cryptographic work: on modern hardware the
 * actual key exchange and signature verification are usually
 * sub-millisecond, so most of this number is typically network
 * round-trip time for the handshake messages (TLS 1.2 needs about two
 * round trips, TLS 1.3 about one) plus JVM/JSSE overhead for
 * certificate parsing and validation. There is no API in the JDK that
 * separates "time spent computing" from "time spent on the wire" for
 * a TLS handshake, so this class does not claim to.
 * <p>
 * Freshness: a brand new SSLContext (and therefore an empty TLS
 * session cache) is created for every analyze() call, specifically so
 * that repeated calls - even to the same host - can never benefit from
 * TLS session resumption, which would report an abbreviated handshake
 * as if it were a full one. This mirrors why TcpAnalyzer opens its own
 * dedicated socket instead of using a pooled connection.
 * <p>
 * A connection or handshake failure is reported as a normal
 * PhaseResult with Status.FAILURE and a TlsFailureReason (still
 * carrying a real elapsed duration), the same design used by
 * TcpAnalyzer and for the same reason: a refused connection, a timed
 * out handshake, or a rejected certificate are themselves meaningful,
 * directly measured outcomes, not conditions that prevent any
 * measurement from being taken.
 * <p>
 * Security: certificate trust validation and hostname verification are
 * never disabled - this class never installs a custom TrustManager and
 * always leaves SSLContext.init's trust managers as the JDK default
 * (the platform's real CA trust store), and endpoint identification is
 * explicitly enabled. ALLOWED_PROTOCOLS explicitly pins the negotiable
 * TLS versions to 1.3/1.2 rather than relying on whatever the ambient
 * JVM's default enabled-protocols happen to be - a server that only
 * offers something older correctly fails the handshake here.
 */
@Component
public class TlsAnalyzer {

    public static final String PHASE = "TLS";

    /**
     * The only TLS versions this analyzer will negotiate. Explicit on
     * purpose: relying on the JVM's ambient default enabled-protocols
     * (governed by the jdk.tls.disabledAlgorithms security property)
     * would make the actual security floor invisible from this code
     * and dependent on JVM/vendor/deployment configuration outside
     * this class's control.
     */
    static final List<String> ALLOWED_PROTOCOLS = List.of("TLSv1.3", "TLSv1.2");

    @FunctionalInterface
    interface Handshaker {
        TlsMetadata handshake(String hostname, String resolvedIp, int port, int timeoutMs) throws IOException;
    }

    private final int timeoutMs;
    private final Handshaker handshaker;

    @Autowired
    public TlsAnalyzer(AnalyzerProperties analyzerProperties) {
        this(analyzerProperties, TlsAnalyzer::performRealHandshake);
    }

    TlsAnalyzer(AnalyzerProperties analyzerProperties, Handshaker handshaker) {
        long configuredTimeoutMs = analyzerProperties.connectTimeout().toMillis();
        if (configuredTimeoutMs <= 0) {
            throw new IllegalStateException(
                    "netrace.analyzer.connect-timeout must be positive, was "
                            + analyzerProperties.connectTimeout());
        }
        this.timeoutMs = Math.toIntExact(configuredTimeoutMs);
        this.handshaker = handshaker;
    }

    public PhaseResult<TlsMetadata> analyze(String hostname, String resolvedIp, int port) {
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("Invalid destination port: " + port);
        }

        long startNanos = System.nanoTime();
        try {
            TlsMetadata metadata = handshaker.handshake(hostname, resolvedIp, port, timeoutMs);
            return PhaseResult.success(PHASE, elapsedMs(startNanos), metadata);
        } catch (SocketTimeoutException e) {
            return failure(hostname, resolvedIp, port, startNanos, TlsFailureReason.TIMEOUT);
        } catch (SSLHandshakeException e) {
            return failure(hostname, resolvedIp, port, startNanos, TlsFailureReason.HANDSHAKE_FAILURE);
        } catch (ConnectException e) {
            return failure(hostname, resolvedIp, port, startNanos, TlsFailureReason.CONNECTION_FAILURE);
        } catch (NoRouteToHostException e) {
            return failure(hostname, resolvedIp, port, startNanos, TlsFailureReason.CONNECTION_FAILURE);
        } catch (IOException e) {
            return failure(hostname, resolvedIp, port, startNanos, TlsFailureReason.UNKNOWN);
        }
    }

    private static TlsMetadata performRealHandshake(String hostname, String resolvedIp, int port, int timeoutMs)
            throws IOException {
        try (Socket plainSocket = new Socket()) {
            plainSocket.connect(new InetSocketAddress(resolvedIp, port), timeoutMs);

            SSLContext sslContext = freshSslContext();
            try (SSLSocket sslSocket =
                    (SSLSocket) sslContext.getSocketFactory().createSocket(plainSocket, hostname, port, true)) {
                sslSocket.setSoTimeout(timeoutMs);

                SSLParameters sslParameters = sslSocket.getSSLParameters();
                List<SNIServerName> serverNames = List.of(new SNIHostName(hostname));
                sslParameters.setServerNames(serverNames);
                sslParameters.setEndpointIdentificationAlgorithm("HTTPS");
                sslParameters.setProtocols(ALLOWED_PROTOCOLS.toArray(new String[0]));
                sslSocket.setSSLParameters(sslParameters);

                sslSocket.startHandshake();

                SSLSession session = sslSocket.getSession();
                String tlsVersion = session.getProtocol();
                String cipherSuite = session.getCipherSuite();
                String certificateSubject = null;
                String certificateIssuer = null;
                try {
                    Certificate[] peerCertificates = session.getPeerCertificates();
                    if (peerCertificates.length > 0 && peerCertificates[0] instanceof X509Certificate leaf) {
                        certificateSubject = leaf.getSubjectX500Principal().getName();
                        certificateIssuer = leaf.getIssuerX500Principal().getName();
                    }
                } catch (SSLPeerUnverifiedException ignored) {
                    // No verified peer certificate to report (e.g. an
                    // anonymous cipher suite); leave subject/issuer absent.
                }

                return new TlsMetadata(
                        hostname, resolvedIp, port, tlsVersion, cipherSuite, certificateSubject, certificateIssuer,
                        null);
            }
        }
    }

    private static SSLContext freshSslContext() {
        try {
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, null, null);
            return context;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No default TLS provider available", e);
        }
    }

    private PhaseResult<TlsMetadata> failure(
            String hostname, String resolvedIp, int port, long startNanos, TlsFailureReason reason) {
        return PhaseResult.failure(PHASE, elapsedMs(startNanos), new TlsMetadata(hostname, resolvedIp, port, reason));
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
