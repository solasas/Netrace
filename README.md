# Network Black Box

Network Black Box is a developer tool that analyzes the observed network performance of public HTTP/HTTPS URLs.

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

## Tech Stack

- **Backend:** Java 21, Spring Boot, Maven, Spring Web
- **Frontend:** React, JavaScript (no TypeScript), Vite, Tailwind CSS

## Project Structure

```
backend/    Spring Boot application (network analysis engine + REST API)
frontend/   React + Vite dashboard
```

## Engineering Principles

- Measurements reflect real, observed network behavior — never faked or presented as more precise than they are.
- Arbitrary URL fetching is treated as a security-sensitive operation (SSRF-aware).
- Standard library APIs are preferred over extra dependencies.
- No over-engineering: only what Stage 1 needs.
# Netrace
