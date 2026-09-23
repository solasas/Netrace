# Netrace

Netrace is a developer tool that analyzes the observed network performance of public HTTP/HTTPS URLs.

## Stage 1

Given a public URL, the system performs a real HTTP(S) request and reports observed measurements:

1. DNS resolution
2. TCP connection establishment
3. TLS handshake (HTTPS)
4. HTTP request/response
5. Time to First Byte (TTFB)
6. Response download time
7. Total observed request duration
8. HTTP status code
9. Resolved IP address
10. HTTP protocol/version where available

Results are shown in a React dashboard with a waterfall-style timeline.

Stage 1 does not include persistence, authentication, multi-URL comparison, or historical tracking — those are later stages.

## Architecture

The frontend never talks to the target URL directly. It calls the backend's `POST /api/analyze` endpoint; the backend performs the real HTTP(S) request to the user-supplied URL and returns the observed measurements as JSON, which the frontend renders. Network-level timing (DNS/TCP/TLS) isn't observable from browser JavaScript, so it has to be measured server-side using real Java networking APIs. See [docs/architecture.md](docs/architecture.md) for the full breakdown, including SSRF considerations.

## Planned Measurement Phases

Stage 1's measurements are built up incrementally — total request timing first, then TTFB/download split, then a full DNS/TCP/TLS breakdown, then the waterfall visualization. See [docs/measurement-phases.md](docs/measurement-phases.md) for details.

## Tech Stack

- **Backend:** Java 21, Spring Boot, Maven, Spring Web
- **Frontend:** React, JavaScript (no TypeScript), Vite, Tailwind CSS

## Project Structure

```
backend/    Spring Boot application (network analysis engine + REST API)
frontend/   React + Vite dashboard
docs/       Architecture notes and roadmap
```

## Engineering Principles

- Measurements reflect real, observed network behavior — never faked or presented as more precise than they are.
- Arbitrary URL fetching is treated as a security-sensitive operation (SSRF-aware).
- Standard library APIs are preferred over extra dependencies.
- No over-engineering: only what Stage 1 needs.
