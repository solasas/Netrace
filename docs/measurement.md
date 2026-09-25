# Measurement Methodology

This document explains exactly what each timing field in `AnalyzeResponse` measures, why the fields cannot be freely added together, and the one arithmetic identity the response actually guarantees.

## The two independent timelines

A single `/api/analyze` call performs **two kinds of work that never share a connection**:

1. **The real HTTP request** (`HttpAnalyzer`, via `java.net.http.HttpClient`) — this is the request whose `statusCode`, `protocol`, `contentLength`, `contentType`, `ttfbMs`, `downloadMs`, `bodyTruncated`, and `totalTimeMs` this response reports.
2. **Three independent, one-off diagnostic probes** (`DnsAnalyzer`, `TcpAnalyzer`, `TlsAnalyzer`, grouped under `probes` in the response) — each performs its *own* fresh DNS resolution, its own `Socket`, or its own `SSLSocket`, purely to measure that one phase in isolation. **None of these connections is reused by the real HTTP request.** `HttpClient` does its own internal DNS lookup, TCP connect, and (for HTTPS) TLS handshake as part of `httpClient.send()`, entirely separately from the probes.

This is why the response shape nests `probes` as a sibling of the request's own fields rather than flattening `dns`/`tcp`/`tls` alongside `statusCode`/`totalTimeMs`: structurally, they are not phases of the same timeline.

## Why the probes can't be summed with the request, or with each other

Naively, it's tempting to compute `dns.durationMs + tcp.durationMs + tls.durationMs + ttfbMs + downloadMs` as a "total connection + transfer waterfall." This produces a number with no real meaning, for three separate reasons:

**1. The probes measure a *different* connection than the real request.**
`dns.durationMs`, `tcp.durationMs`, and `tls.durationMs` describe dedicated, throwaway connections opened purely to observe each phase. The real request's own internal DNS/TCP/TLS setup — bundled invisibly inside `ttfbMs` — is a *separate* occurrence of the same kind of work, not the same event measured twice. Adding the probe durations to `ttfbMs` double-counts network setup that happens twice in reality (once for the probe, once for the real request), not once.

**2. `tls.durationMs` already includes its own TCP connect.**
`TlsAnalyzer` measures "wall-clock time from opening the dedicated TCP socket through handshake completion" (see its Javadoc) — because a TLS handshake cannot happen without a TCP connection underneath it, and Java's `SSLSocket` API doesn't offer a way to start timing *after* an already-open socket without also timing the connect that opened it in a way that's separable from handshake-only cost. So even `tcp.durationMs + tls.durationMs` alone double-counts one TCP handshake: `tls.durationMs` is not "TLS on top of tcp.durationMs," it *contains* an equivalent connect.

**3. `ttfbMs` (and therefore `totalTimeMs`) bundles `HttpClient`'s own internal, redundant DNS+TCP+TLS setup.**
`java.net.http.HttpClient`'s public API gives no way to inject a pre-resolved address or a pre-established connection, and exposes no hook to observe "TCP connect complete" or "TLS handshake complete" separately from "response headers received." So `ttfbMs` is: internal DNS + internal TCP connect + internal TLS handshake (if HTTPS) + server processing time + network transit for the first response byte, all fused into one number by the JDK's HTTP client internals. There's no way to subtract out the connection-setup portion of `ttfbMs` to compare it against the probes.

**Concrete illustration:** for a typical HTTPS request you might see `dns.durationMs=15`, `tcp.durationMs=40` (which itself contains a ~25ms connect), `tls.durationMs=90` (which *also* contains that same ~25ms connect, redone from scratch), `ttfbMs=180` (which contains yet another full DNS+TCP+TLS cycle inside it, plus server think time), and `totalTimeMs=210`. Summing everything gives `15+40+90+180=325`, a number roughly 50% larger than the real `totalTimeMs=210` — not because anything is slow, but because two TCP connects and three DNS lookups happened across four independent operations, and the naive sum counts all of them as if they were one sequential pipeline.

## The one identity that actually holds

```
totalTimeMs == ttfbMs + downloadMs
```

This is true by construction, not something that needs runtime validation: `HttpAnalyzer` defines `downloadMs = elapsedMs - ttfbMs`, where `elapsedMs` is returned as `totalTimeMs`. It holds for every response, including truncated ones (the analyzer abandons the download early once `netrace.analyzer.max-response-size` is exceeded, but `downloadMs` is still computed the same way, over whatever time elapsed before abandonment) and slow-body responses. This identity is covered by tests in `HttpAnalyzerTest` across a normal response, a slow-body response, and a truncated response.

No such identity exists for `dns`, `tcp`, or `tls` in relation to each other or to the request's own fields — they must be presented and interpreted as independent measurements only.

## Field reference

### Request fields (top level)

| Field | What it measures |
|---|---|
| `statusCode` | HTTP status code of the real request's final response (after following redirects). |
| `protocol` | Negotiated protocol for the real request: `HTTP/1.1` or `HTTP/2`. The JDK's `HttpClient` has no HTTP/3 support to detect, so it is never reported. |
| `contentLength` | `Content-Length` header value, or `null` if the server didn't send one (e.g. chunked transfer) — never coerced to `0`. |
| `contentType` | `Content-Type` header value, or `null` if absent. |
| `ttfbMs` | Wall-clock time from just before issuing the real request until the first response byte is observed by the JDK's `HttpClient`. Includes the real request's own internal DNS resolution, TCP connect, and (for HTTPS) TLS handshake, plus server processing time and first-byte network transit — these cannot be separated from each other through the public `HttpClient` API. |
| `downloadMs` | `totalTimeMs - ttfbMs`: time spent receiving the rest of the body after the first byte, up to `netrace.analyzer.max-response-size` (the download is abandoned, and `bodyTruncated=true`, if the body exceeds that limit). |
| `bodyTruncated` | `true` if the download was abandoned early because the body exceeded `netrace.analyzer.max-response-size`. |
| `totalTimeMs` | Wall-clock time for the entire real request/response cycle. |

### `probes` (independent diagnostic measurements)

| Field | What it measures |
|---|---|
| `probes.dns.durationMs` | Time for one dedicated DNS resolution of the hostname, performed separately from the real request. |
| `probes.tcp.durationMs` | Time to open one dedicated TCP connection to the already-resolved address, performed separately from the real request. |
| `probes.tls.durationMs` | Time from opening a dedicated TCP socket through TLS handshake completion, for HTTPS URLs only (`null` for HTTP). Includes its own TCP connect — see above. |
| `probes.tls.tlsVersion`, `cipherSuite`, `certificateSubject`, `certificateIssuer` | Captured from that dedicated handshake; not fabricated or inferred from the real request. |

## Waterfall visualization assumptions

The frontend's `Waterfall` component (`frontend/src/components/Waterfall.jsx`) renders `dns`/`tcp`/`tls` and `ttfbMs`/`downloadMs` as proportional bars. Because the fields above are not all mutually sequential, the chart makes a deliberate structural choice rather than drawing all five as one chained bar:

- **One shared scale, two separate groups.** Every bar's width is `duration / max(all known durations)`, so lengths are honestly comparable against each other. But the chart is split into a "connection probes" group (DNS, TCP, TLS) and a "request timeline" group (TTFB, download) — sharing a scale is not the same as sharing a start time, and only the second group's start times are real.
- **Probe bars are not chained.** DNS, TCP, and TLS are each drawn from their own `0` to their own duration, on separate rows, never laid end-to-end. They ran over separate, dedicated connections (see above) with no verified ordering or overlap relationship to report, so the chart does not draw one. Showing them chained (DNS ends where TCP begins, etc.) would visually assert a single timeline that the measurement methodology does not support, even though each individual duration value is real.
- **The request timeline is chained, because that sequence is real.** TTFB and download are the one pair with a verified identity (`totalTimeMs == ttfbMs + downloadMs`, see above), so they're drawn as one bar with two segments and an explicit boundary marker at the TTFB/download crossover point (labeled with the exact millisecond).
- **No fabricated timestamps.** The backend reports only durations, not wall-clock start/end times per phase. "Start/end" labels shown in the UI (e.g. "0–23 ms") are derived arithmetically only where the sequence is verified (the request timeline); probe bars are labeled "0–duration ms" to mean "this probe's own elapsed window," not a position on any shared clock.
- **Minimum visible width for small durations.** A bar's rendered width is floored to a small minimum percentage so near-zero durations (down to 0ms) stay visible instead of disappearing. This only affects pixel width — the label and hover tooltip always show the unmodified real duration, and the floor is disclosed in the component's own caption.
- **Missing TLS.** For HTTP URLs, `probes.tls` is `null`. The chart renders an explicit dashed "not applicable" placeholder in that row rather than a zero-width bar or a fabricated value.

## Why this design, rather than fixing the timing itself

An alternative would be to refactor `TlsAnalyzer` to decouple TCP-connect time from handshake-only time (e.g. by accepting a pre-connected socket and timing only the handshake). That's a legitimate improvement, but it doesn't fix the deeper issue: `ttfbMs` would still bundle `HttpClient`'s own internal setup, which the public JDK API gives no way to observe separately. The probes would always remain measurements of a *different* connection than the real request measures. Given that, correctness lives in making the independence visible in the response shape itself (`probes` nested apart from the request's own fields) and documenting it here, rather than chasing an arithmetic identity across probes and the request that the underlying APIs cannot support.
