# Planned Measurement Phases

Stage 1's full goal is to report DNS resolution, TCP connect, TLS handshake, TTFB, download time, total duration, status code, resolved IP, and HTTP version for a single user-supplied URL. That is built up incrementally rather than all at once:

1. **Total request timing** — issue the real HTTP(S) request with a standard Java HTTP client, measure elapsed wall-clock time for the whole request/response cycle, and return status code + total time. Establishes the request/response plumbing (controller, service, DTOs, validation, error handling) without any timing breakdown yet.
2. **Time to First Byte (TTFB) and download time** — split total time into "time until the first response byte arrives" and "time spent streaming the rest of the body."
3. **DNS / TCP / TLS breakdown** — instrument the individual phases of establishing the connection (DNS resolution, TCP handshake, TLS handshake for HTTPS) as distinct, separately-timed steps, plus the resolved IP address and negotiated HTTP version.
4. **Waterfall visualization** — once the backend reports phase-level timings, the frontend renders them as a waterfall timeline alongside the existing dashboard values.

Each phase should be independently testable and shippable; later phases must not require re-architecting earlier ones. Phases beyond Stage 1 (persistence, multi-URL comparison, historical tracking, etc.) are out of scope here — see the [README](../README.md).
