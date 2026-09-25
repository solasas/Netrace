# Netrace

Netrace is a developer tool that analyzes the observed network performance of public HTTP/HTTPS URLs — DNS resolution, TCP connect, TLS handshake, TTFB, download time, total request duration, status code, resolved IP, and HTTP version — by making a real request to the URL, not by estimating or simulating one.

## Stage 1 Scope

Stage 1's target: a user submits a single public URL, the backend makes a real HTTP(S) request to it, and the frontend displays the observed measurements in a dashboard with a waterfall-style timeline:

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

Stage 1 explicitly excludes persistence, authentication, multi-URL comparison, and historical tracking — those are later stages. Within Stage 1, the measurements above are built up incrementally (see [docs/measurement-phases.md](docs/measurement-phases.md)); not all of them are implemented yet — see [Current Limitations](#current-limitations) below for exactly what exists today.

## Technology Stack

- **Backend:** Java 21, Spring Boot, Maven, Spring Web
- **Frontend:** React, JavaScript (no TypeScript), Vite, Tailwind CSS

## Current Architecture

What exists right now, as of this commit:

- **Backend** (`backend/`) — a Spring Boot application with the Spring Web dependency. It currently exposes no REST endpoints of its own (no `/api/analyze`, no controllers, services, or DTOs yet) — just the generated application skeleton and its default test.
- **Frontend** (`frontend/`) — a React + Vite + Tailwind single-page app with one page (`AnalyzerPage`) showing the title, a description, a URL input, and an Analyze button. The button does not call anything yet; there is no API client (`src/services/` is empty) and no request/response wiring to the backend.

Nothing in this repo currently makes a real network request end-to-end. The intended target architecture — frontend calls backend, backend makes the real request to the target URL — is documented with a diagram in [docs/architecture.md](docs/architecture.md), along with why measurement has to happen server-side and the SSRF considerations that come with fetching arbitrary user-supplied URLs.

## Running the Backend

```bash
cd backend
./mvnw spring-boot:run
```

Starts on `http://localhost:8080` by default. There are currently no application endpoints to call — this only confirms the Spring Boot application itself starts.

To run the test suite:

```bash
cd backend
./mvnw test
```

## Configuration

The analyzer's outbound HTTP timeouts are configurable via `backend/src/main/resources/application.properties`, environment variables, or `--` command-line arguments (standard Spring Boot property sources):

| Property | Default | Controls |
|---|---|---|
| `netrace.analyzer.connect-timeout` | `5s` | Max time to establish a TCP connection to the target URL. Exceeding it surfaces as a `TIMEOUT` error. |
| `netrace.analyzer.request-timeout` | `10s` | Max time for the full request/response exchange once connected, including downloading the response body. Exceeding it also surfaces as a `TIMEOUT` error. |
| `netrace.analyzer.max-redirects` | `5` | Max number of redirects followed for one analysis. Each hop is individually validated against the SSRF guard (see [docs/security.md](docs/security.md)) before it's followed. Exceeding it surfaces as an `INVALID_RESPONSE` error. |
| `netrace.analyzer.allow-private-targets` | `false` | When true, disables the SSRF guard entirely, allowing analysis of loopback/private/link-local targets. Off by default; only appropriate for a controlled internal/testing deployment - see [docs/security.md](docs/security.md) for the risk. |

Values accept Spring Boot's duration shorthand (e.g. `5s`, `500ms`, `2m`). Example override:

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments=--netrace.analyzer.request-timeout=3s
```

## Running the Frontend

```bash
cd frontend
npm install
npm run dev
```

Starts the Vite dev server (default `http://localhost:5173`) and serves the Analyzer page. The Analyze button is present but not yet wired to any backend call.

## Current Limitations

- No backend REST endpoints exist yet — `/api/analyze` has not been implemented, so the frontend has nothing to call.
- The frontend's Analyze button does not perform any action yet.
- No DNS/TCP/TLS timing breakdown, TTFB split, or waterfall visualization exist yet — only UI and backend scaffolding are in place.
- No SSRF protections are implemented yet. This must be addressed before the backend starts making real outbound requests to user-supplied URLs.
- No tests exist beyond the default Spring Boot context-load test.

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
