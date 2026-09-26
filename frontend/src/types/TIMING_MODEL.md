# Network Timing Data Model

## Overview

The timing data model represents phases of network analysis in a way that:
- Distinguishes measured vs. derived values
- Clarifies independent vs. sequential measurements
- Supports extensibility for future phases
- Powers the waterfall visualization

## Architecture

```
Backend (AnalyzeResponse / CompareResult)
  ↓
  ├─ dns: DnsResult
  ├─ tcp: TcpResult
  ├─ tls: TlsResult (optional, null for HTTP)
  ├─ ttfbMs: long
  ├─ downloadMs: long
  └─ statusCode: int
  
         ↓ buildTimingPhases()
         
TimingPhase[]
  ├─ DNS (measured, independent)
  ├─ TCP (measured, independent)
  ├─ TLS (measured, independent) or (skipped)
  ├─ TTFB (measured, sequential)
  └─ DOWNLOAD (measured, sequential)
  
         ↓
         
Waterfall Component
  └─ Renders bars with proper grouping
     - Probes (DNS, TCP, TLS) - all on 0-to-duration scale, not chained
     - Request (TTFB + DOWNLOAD) - sequential on same timeline
```

## TimingPhase Structure

### Independent Phases (DNS, TCP, TLS)

```javascript
{
  phase: 'DNS',                    // Phase name
  durationMs: 18,                  // Measured duration
  measured: true,                  // Directly measured (not derived)
  status: 'SUCCESS',               // SUCCESS | FAILED | SKIPPED | TIMEOUT
  measurement_note: '...',         // Explanation for this measurement
  metadata: {                      // Phase-specific data
    hostname: 'example.com',
    resolvedIps: ['93.184.216.34']
  },
  error: null,                     // Error message if status is FAILED
  
  // Note: NO startMs/endMs (independent measurement on separate connection)
}
```

### Sequential Phases (TTFB, DOWNLOAD)

```javascript
{
  phase: 'TTFB',                   // Phase name
  durationMs: 121,                 // Measured duration
  measured: true,                  // Directly measured
  status: 'SUCCESS',               // SUCCESS | FAILED | SKIPPED | TIMEOUT
  measurement_note: '...',         // Explanation for this measurement
  metadata: {                      // Phase-specific data
    httpVersion: 'HTTP/2'
  },
  error: null,                     // Error message if status is FAILED
  
  // Timeline positioning (sequential on same HTTP request)
  startMs: 0,                      // Start position on request timeline
  endMs: 121                       // End position (= startMs + durationMs)
}
```

For DOWNLOAD, the startMs equals the TTFB's endMs, creating a continuous timeline.

## Measured vs. Derived

**Measured Phases:**
- DNS duration: directly measured by DnsAnalyzer
- TCP duration: directly measured by TcpAnalyzer
- TLS duration: directly measured by TlsAnalyzer
- TTFB: directly measured by HttpAnalyzer (request to headers available)
- DOWNLOAD: directly measured by HttpAnalyzer (body download time)

**Derived Phases:**
- None at this level (all are measured independently)
- Total HTTP time is verified: totalMs = ttfbMs + downloadMs (guaranteed)

## Independent vs. Sequential

**Independent Measurements:**
- DNS: runs on its own dedicated connection
- TCP: runs on its own dedicated connection
- TLS: runs on its own dedicated connection

These phases have NO startMs/endMs. They are visualized as separate bars on a 0-to-duration scale. Do NOT chain them end-to-end.

**Sequential Measurements:**
- TTFB → DOWNLOAD: both on the same HTTP request

These phases HAVE startMs/endMs positioning. DOWNLOAD always begins exactly when TTFB ends:
- TTFB.startMs = 0
- TTFB.endMs = ttfbMs
- DOWNLOAD.startMs = TTFB.endMs
- DOWNLOAD.endMs = TTFB.endMs + downloadMs
- Total timeline: `ttfbMs + downloadMs = totalMs`

The positioning is guaranteed: `DOWNLOAD.startMs === TTFB.endMs` always holds.

## Phase Descriptions

Use these to populate tooltips and explanations:

- **DNS**: "Hostname resolution time. Independent measurement on a dedicated connection."
- **TCP**: "Time to establish TCP connection. Independent measurement on a dedicated connection."
- **TLS**: "TLS handshake time. Independent measurement on a dedicated connection."
- **TTFB**: "Observed time until response data became available. From request send to response headers parsed. Bundles network latency, request transmission, server processing, and response transmission initiation."
- **DOWNLOAD**: "Time spent downloading response body. Sequential with TTFB on the same request."

## Usage Example

```javascript
import { buildTimingPhases, PHASE_NAMES } from '../types/TimingPhase'

function AnalyzePage({ result }) {
  const phases = buildTimingPhases(result)
  
  return (
    <Waterfall phases={phases} />
  )
}
```

## Future Extensibility

To add a new phase in the future:

1. Add to PHASE_NAMES
2. Add to PHASE_DESCRIPTIONS
3. Add to buildTimingPhases() logic
4. Update Waterfall component rendering
5. No breaking changes to existing phases

Example: HTTP/3 (future)
```javascript
export const PHASE_NAMES = {
  ...existing,
  HTTP3_SETUP: 'HTTP/3 Setup',  // New phase
}
```

## Visualization Rules

**For Independent Phases (DNS, TCP, TLS):**
- Plot each bar from 0 to its durationMs (ignore startMs/endMs, they don't exist)
- Use a shared scale (the max duration across all phases)
- Group them under a "Connection Probes" section
- Label clearly that these are independent measurements, not chained

Example:
```
DNS    ███ (0-18ms of its own connection)
TCP      █████ (0-27ms of its own connection)
TLS           ███████ (0-43ms of its own connection)
```

**For Sequential Phases (TTFB, DOWNLOAD):**
- Plot TTFB from startMs (0) to endMs (121)
- Plot DOWNLOAD from startMs (121) to endMs (159)
- Group them under a "Request Timeline" section
- The boundary between them is the visible transition point
- This IS a real sequential timeline

Example:
```
Request timeline: |←──TTFB──→|←─DOWNLOAD─→|
                  0ms      121ms         159ms
```

**Scale Considerations:**
- Use the same scale for all bars (both probes and request) for visual comparability
- This makes bar lengths directly comparable
- But it does NOT mean probe bars occur at those times in the request
- Add visual or textual separation between probes and request sections

## Limitations

- DNS, TCP, TLS durations are independent measurements on separate connections
- They do NOT occur sequentially in the same request
- Waterfall scale is shared (for comparability) but probe start times are not chained
- Only TTFB and DOWNLOAD are guaranteed sequential (same request)
- Visualized as phase duration comparison (probes) + request timeline (TTFB+DOWNLOAD), not a packet trace

See docs/measurement-methodology.md for full backend methodology.
