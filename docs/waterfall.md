# Network Waterfall Visualization

## Overview

The waterfall visualization shows the timing breakdown of network requests. It helps answer the question: **"Where did the observed request time go?"**

Two views are available:
- **Single URL**: Detailed waterfall for one URL with interactive phase selection
- **Comparison**: Side-by-side, overlay, or normalized view for 2–5 URLs

## Phases Explained

### Independent Phases (Diagnostic Measurements)

These phases run on separate dedicated connections and do **not** occur sequentially in the actual request:

#### DNS (Hostname Resolution)
- **What**: Time to resolve hostname to IP address
- **Starts**: DNS lookup initiation
- **Ends**: IP address obtained
- **Measurement**: From DnsAnalyzer on separate connection
- **Independent**: Does not share start time with TCP or TLS

#### TCP (Connection Establishment)
- **What**: Time to establish TCP connection to resolved IP
- **Starts**: Connection attempt to IP:port
- **Ends**: TCP handshake complete
- **Measurement**: From TcpAnalyzer on separate connection
- **Independent**: Does not share start time with DNS or TLS

#### TLS (Handshake)
- **What**: Time to negotiate TLS/SSL encryption
- **Starts**: TLS negotiation
- **Ends**: Handshake complete, ready to send HTTP request
- **Measurement**: From TlsAnalyzer on separate connection
- **Independent**: Does not share start time with DNS or TCP
- **Applicability**: HTTPS only (null for HTTP)

### Sequential Phases (Actual Request)

These phases occur on the **same HTTP request**, in sequence:

#### TTFB (Observed Time to First Byte)
- **What**: Time from request send to response headers available
- **Starts**: HTTP request sent (0 ms on request timeline)
- **Ends**: First byte of response available
- **Includes**: Network latency + server processing + response transmission initiation
- **NOT**: Backend processing time in isolation (bundles network + processing + latency)
- **Measurement**: From HttpAnalyzer

#### DOWNLOAD (Response Body Download)
- **What**: Time to download response body
- **Starts**: Exactly when TTFB ends
- **Ends**: Response fully received or size limit reached
- **Includes**: HTTP/2 multiplexing effects, network latency for body transmission
- **Measurement**: From HttpAnalyzer
- **Sequential**: Always follows TTFB (totalTimeMs = ttfbMs + downloadMs)

## Timing Relationship

```
Independent Measurements (on separate connections):
├─ DNS: 0-18 ms of its own connection
├─ TCP: 0-27 ms of its own connection
└─ TLS: 0-43 ms of its own connection

Sequential Measurement (same HTTP request):
└─ HTTP: 0-247 ms total
   ├─ TTFB: 0-121 ms (headers)
   └─ DOWNLOAD: 121-159 ms (body)
```

**Important**: The DNS, TCP, and TLS durations do NOT add up to the HTTP total time, because they're independent measurements on separate connections.

## Display Modes

### Side-by-side (Default)
Shows each URL's phases with individual bars on a shared scale. Proper grouping of independent and sequential phases.

### Overlay
Shows phase durations for all URLs on a shared scale. Labeled as "Phase Duration Comparison" (not a chronological trace).

### Normalized
Shows each phase as a percentage of total HTTP time. Useful for understanding relative time distribution across the request.

## Visual Semantics

### Colors
- **DNS**: Indigo-400
- **TCP**: Indigo-500
- **TLS**: Indigo-600
- **TTFB**: Emerald-500
- **DOWNLOAD**: Emerald-700

Colors are consistent across all views and components.

### Status Indicators

#### Success (Colored)
Phase executed successfully, duration measured.

#### Failed (Dimmed)
Phase attempted but failed. Dimmed opacity. Error message in details.

#### Skipped (Dashed Line)
Phase not applicable (e.g., TLS for HTTP URLs).

## Interactivity

### Hover
- Shows tooltip with phase name, duration, and description
- Phase-specific metadata (IPs, versions, certificate details)

### Click
- Selects phase for detailed view
- Opens expandable panel below waterfall
- Shows full metadata and explanation

### URL Visibility (Comparison Mode)
- Toggle individual URLs on/off
- No additional backend request
- Re-calculates scale based on visible URLs

## Key Measurements

### Total Time
The wall-clock duration from HTTP request send to response receive (or size limit).

### TTFB vs Backend Processing
- **TTFB includes**: Network latency (to server), request transmission, server processing, response transmission initiation
- **TTFB does NOT isolate**: Backend processing time alone
- To estimate backend processing, you'd need to subtract network round-trip from TTFB

### Response Size
**Actual bytes read**: Subject to configured maximum (default 128 MB)
**Content-Length header**: What server declared (may differ if truncated)

## Limitations

1. **No per-redirect timing**: Aggregate redirect count and final URL shown, but not individual redirect timings
2. **Independent measurements don't sum**: DNS + TCP + TLS ≠ TTFB or total HTTP time
3. **Shared scale may hide small phases**: Phases under ~2% width are floored to minimum for readability
4. **No request queuing time**: Measures connection and transfer, not browser queuing
5. **No rendering time**: Only network and transfer, not DOM parsing or rendering

## Methodology

### DNS Measurement
- Uses system DNS resolution
- Measured from Java's `InetAddress.getAllByName()`
- Does not reuse previous DNS results

### TCP Measurement
- Fresh connection attempt
- Measures from connection start to handshake complete
- Separate from TLS negotiation

### TLS Measurement
- Separate TLS handshake on fresh connection
- Includes handshake only (not connection setup)
- Captures negotiated version, cipher suite, and certificate info

### HTTP Measurement
- Single real request via `HttpClient`
- TTFB: From request send to response headers parsed
- Download: Remaining body receive time
- No buffering; response body discarded as received (bytes counted only)

### Total Time
Measured as complete HTTP transaction:
- **Start**: Request initiation
- **End**: Response body complete (or size limit reached)
- **Relationship**: totalTimeMs = ttfbMs + downloadMs (guaranteed for HTTP phase)

## Future Improvements

1. **Per-redirect timing**: Break down individual redirect chain timing
2. **Request queuing**: Show browser queue wait time (if available)
3. **Earlier insights**: Connection hints, early hints (103), etc.
4. **Rendering metrics**: Connect to Core Web Vitals (LCP, FID, etc.)
5. **Resource waterfall**: Show asset loading timeline for full page
6. **Service worker timing**: Show SW processing time
7. **Certificate chain details**: Full chain verification timeline

## Debugging with Waterfall

### High TTFB
- Geographic distance to server (high network latency)
- Server processing/backend delay
- Query database performance
- External API calls from server

### High Download
- Large response size
- Slow network (cellular, WiFi quality)
- Compression not enabled
- Streaming responses not chunked

### High DNS
- New hostname (not cached)
- DNS resolver performance
- Network path to resolver
- Geographic distance (BGP routing)

### Connection Preformance
- Compare probes across multiple URLs
- Identify pattern (all slow DNS = resolver issue vs. one URL slow DNS = per-domain issue)
- Use normalized view to see relative phase contributions

## See Also

- [Timing Model](./timing-model.md) — Formal definitions and relationships
- [Measurement Methodology](./measurement-methodology.md) — Backend implementation details
