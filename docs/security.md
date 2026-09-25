# Security: SSRF Protection

Netrace's entire purpose is to make a real, server-side HTTP(S) request to a URL a public caller supplies. That makes it a textbook Server-Side Request Forgery (SSRF) surface: without protection, an attacker could submit a URL pointing at the backend's own loopback interface, its private network, a cloud provider's instance-metadata endpoint, or anywhere else a public caller should never be able to reach through this service - and read back the response through Netrace's own API.

This document covers what's protected, how, what was found and fixed, and - just as importantly - what is **not** fully closed and why.

## Threat model

The attacker controls exactly one input: the `url` field of a `POST /api/analyze` request. Everything downstream - which address(es) that URL resolves to, what status code and headers the target returns, where any redirect points - is attacker-influenced. The goal is to make sure that no matter what a hostname resolves to, or what a response tells the analyzer to do next, the analyzer never connects to an address it shouldn't.

## What's protected

### Resolved-address validation, not hostname-string matching

`SsrfGuard` (`backend/src/main/java/com/netrace/backend/security/SsrfGuard.java`) classifies an already-**resolved** `InetAddress`, never the original hostname text. Checking whether a hostname string looks like `"localhost"` or starts with `"127."` is both incomplete (custom DNS records, `/etc/hosts`, or any of the numeric tricks below can point a totally different-looking hostname at a blocked address) and unnecessary once every resolved address is checked instead - whatever representation a hostname resolves through, the resolver produces the same address bytes SsrfGuard inspects.

It blocks:

| Category | Ranges |
|---|---|
| Loopback | `127.0.0.0/8`, `::1` |
| Private IPv4 (RFC 1918) | `10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16` |
| Link-local (includes cloud metadata) | `169.254.0.0/16` (covers `169.254.169.254` - the AWS/GCP/Azure/DigitalOcean instance-metadata address), `fe80::/10` |
| IPv6 unique local (modern ULA) | `fc00::/7` |
| IPv6 site-local (deprecated) | `fec0::/10` |
| Wildcard / "this network" | `0.0.0.0`, `::` |
| Carrier-grade NAT (RFC 6598) | `100.64.0.0/10` |
| Limited broadcast | `255.255.255.255` |
| Multicast | all multicast ranges |
| IPv4-mapped IPv6 | `::ffff:0:0/96`, unwrapped and re-checked against every rule above (e.g. `::ffff:127.0.0.1` is blocked because the embedded `127.0.0.1` is) |

Deliberately **not** covered: every entry in IANA's special-purpose address registries (e.g. `192.0.0.0/24` IETF protocol assignments, `192.88.99.0/24` 6to4 relay anycast, `198.18.0.0/15` benchmarking). The ranges above are the ones a real internal network or cloud metadata service actually uses; the omitted ones are exotic enough that adding them without a concrete reason felt like speculative scope creep. Extending the table in `SsrfGuard` is a small, local change if a real need shows up.

### Alternate IP representations - verified, not assumed

Classic SSRF write-ups warn about decimal (`2130706433`), octal (`0177.0.0.1`), and hex (`0x7f000001`) encodings of `127.0.0.1` bypassing naive string-based checks. Since this project validates the *resolved* address rather than parsing hostname text, none of these can bypass `SsrfGuard` even in principle - but their actual behavior against this JDK was verified empirically (`SsrfGuardTest`), not assumed, and the result was more interesting than expected:

- **Decimal** (`2130706433`) and **shorthand dotted forms** (`127.1`, `127.0.1`) *do* resolve to `127.0.0.1` on JDK 21, and are correctly blocked.
- **A leading zero in a dotted octet** (`0177.0.0.1`) is *not* interpreted as octal by JDK 21's `InetAddress` - it resolves to `177.0.0.1`, a different, non-loopback address. This specific trick is not a bypass here.
- **Hexadecimal literals** (`0x7f000001`) are rejected outright by JDK 21's strict numeric-literal parser (`UnknownHostException`) - they never even reach `SsrfGuard`.

Both of the latter two behaviors are locked in by dedicated tests (`doesNotMisinterpretALeadingZeroOctetAsOctal`, `rejectsHexadecimalLiteralsOutright`) so a future JDK change would be caught rather than silently assumed away.

### Two independent validation points

The analyzer never connects to a single address that isn't reused everywhere else (see [docs/measurement.md](measurement.md)): `DnsAnalyzer`/`TcpAnalyzer`/`TlsAnalyzer` use one dedicated resolution, and `HttpAnalyzer`'s real request performs its own, entirely separate resolution internally. Both paths are independently guarded:

- **`AnalysisService`** validates every address `DnsAnalyzer` resolves before `TcpAnalyzer`/`TlsAnalyzer` ever run.
- **`HttpAnalyzer`** performs its own resolution and validation immediately before *every* request it sends - the original URL, and every redirect target (see below) - since it does not reuse `DnsAnalyzer`'s result.

A blocked target surfaces as `AnalysisException.Reason.BLOCKED_TARGET`, mapped to `400 Bad Request` with `error: "BLOCKED_TARGET"`.

### Redirects

This was the most important gap found during this review. `HttpClient` was previously configured with `Redirect.NORMAL`, meaning it followed redirects **transparently, internally, with no hook to inspect or reject the target**. A URL could pass every check with a public IP on the first request, then respond with a redirect to `http://169.254.169.254/` (or any other blocked address) - which the client would silently follow, defeating every check above.

Fixed by configuring the shared `HttpClient` with `Redirect.NEVER` (`HttpClientConfig`) and having `HttpAnalyzer` follow redirects itself, one hop at a time: on every 3xx response, it resolves and validates the `Location` target through the same `SsrfGuard`-backed check as the original URL before ever connecting to it, and rejects any redirect to a non-`http(s)` scheme outright (closing a related trick: redirecting to `file://`, `gopher://`, etc.). The hop count is capped at `netrace.analyzer.max-redirects` (default 5); exceeding it fails with `INVALID_RESPONSE` rather than looping indefinitely.

### Off-by-default escape hatch

`netrace.analyzer.allow-private-targets` (default `false`) disables the guard entirely when explicitly set `true`. This exists for a genuinely legitimate case - a controlled internal deployment that deliberately wants to analyze targets on its own private network - and is reused by this project's own test suite to reach real local servers for scenarios (timeouts, connection-refused) that can't be reproduced reliably against real public hosts. **It is a full bypass, not scoped to specific hosts or callers.** Never set it true on any deployment reachable from an untrusted network.

## What this does *not* fully close

Documented deliberately, not silently:

- **DNS rebinding (TOCTOU).** `HttpAnalyzer`'s pre-flight check and the actual connection `httpClient.send()` performs moments later are two *separate* DNS resolutions. A narrow window exists in which an attacker's DNS server could answer the pre-flight check with a public IP and then answer the real connection's lookup with a private one. Closing this fully would require pinning the validated IP for the actual connection - not reusing it for another lookup at all - which the standard `java.net.http.HttpClient` API has no supported hook for (short of a JVM-wide custom `InetAddressResolverProvider`, a much larger and riskier change than this review's scope justified). Re-validating immediately before every hop, as implemented, narrows this window to the time between two resolver calls rather than closing it outright.
- **No shared time budget across redirect hops.** Each hop gets its own full `netrace.analyzer.request-timeout`; a chain of N redirects can take up to N times that, bounded only by `max-redirects`, not by one overall deadline.
- **The OS/JVM resolver is trusted.** No DNSSEC validation, no protection against a compromised or malicious local resolver - `InetAddress.getAllByName` is trusted to return the addresses it says a hostname points to.
- **`allow-private-targets` is all-or-nothing.** There's no per-host allowlist; enabling it opens every private/loopback/link-local address, not just an intended one.
- **IANA special-purpose ranges beyond the table above** (benchmarking, protocol-assignment, 6to4 relay, etc.) are not blocked - see the note in that section.

## Testing

- `SsrfGuardTest` - every blocked category from the table above, real public addresses just outside each range (to catch off-by-one boundary errors), and the alternate-representation findings above.
- `HttpAnalyzerSsrfTest` - end-to-end through `HttpAnalyzer`: a direct request to loopback and to the cloud metadata address blocked by the real (production) guard with no server needed; a redirect target rejected even when the initial hop passed (proving per-hop revalidation, not just an initial check); a redirect to a non-`http(s)` scheme rejected; exceeding `max-redirects` failing cleanly; `allow-private-targets=true` bypassing the guard as designed.
- `AnalysisServiceTest` - a DNS resolution landing on a private address is blocked before TCP/TLS ever run, and `allowPrivateTargets` bypasses it.
- `AnalyzeEndpointIntegrationTest.blocksALocalHttpUrlAsAnSsrfTarget` - proves the guard is wired through the *real* Spring stack end-to-end (controller → service → analyzer), not just at the unit level.
