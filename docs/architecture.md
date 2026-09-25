# Architecture

This describes Netrace's Stage 1 architecture, which is implemented as described below — see [Current Status](#current-status) at the bottom for exactly what that covers, and the README's [Current Roadmap](../README.md#13-current-roadmap) for what's deliberately not here yet.

## Components

```mermaid
flowchart LR
    User(["User"]) -->|enters URL| Frontend["Frontend\nReact + Vite"]
    Frontend -->|"POST /api/analyze\n{ url }"| Backend["Backend\nSpring Boot"]
    Backend -->|"real HTTP(S) request"| Target["Target URL\n(user-supplied)"]
    Target -->|"real response"| Backend
    Backend -->|"JSON measurements"| Frontend
    Frontend -->|renders dashboard + waterfall| User
```

- **Frontend** (`frontend/`) — a React/Vite single-page app. Collects a URL from the user, calls the backend's `/api/analyze` endpoint, and renders the returned measurements (dashboard + waterfall timeline). It never talks to the target URL directly — browsers can't observe raw TCP/TLS/DNS timing, and CORS would block most cross-origin targets anyway.
- **Backend** (`backend/`) — a Spring Boot application. It is the only component that performs the actual network request to the user-supplied URL, using standard Java networking APIs so the timing data reflects a real observed request rather than a synthetic or client-side estimate.
- **Target URL** — any public HTTP/HTTPS endpoint the user asks about. Untrusted input: the backend treats fetching it as a security-sensitive operation (see [Security](#security)).

## Why measurement happens server-side

Browsers do not expose DNS resolution time, raw TCP connect time, or TLS handshake time to JavaScript (the Resource Timing / Navigation Timing APIs only offer approximations, and are further limited by cross-origin restrictions). To report real, unapproximated measurements, the request has to be made by the backend using lower-level Java networking primitives, not `fetch()` in the browser.

## Security

Because the backend accepts an arbitrary user-supplied URL and fetches it, it is treated as a Server-Side Request Forgery (SSRF) surface: the backend is a trusted service capable of reaching internal/private network addresses that end users normally cannot reach directly. Outbound requests are validated against loopback, private, link-local (including cloud metadata endpoints), and other reserved address ranges before connecting - including on every redirect hop, not just the initial URL. See [docs/security.md](security.md) for the full threat model, what's protected, and this project's disclosed remaining limitations (notably DNS rebinding).

## Non-goals (Stage 1)

No persistence, no authentication, no multi-URL comparison, no historical tracking. See the [README](../README.md) and [measurement phases](measurement-phases.md) for what Stage 1 does cover.

## Current Status

The request flow above is implemented end to end:

- The backend exposes `POST /api/analyze`, backed by a controller, service, and DTO layer, performing the real DNS/TCP/TLS probes and HTTP request described here and validating every target against the SSRF guard (see [Security](#security)) before connecting.
- The frontend's Analyze button calls that endpoint through a real API client and renders the response as a summary, a waterfall timeline, and per-phase detail panels.

See the README's [Current Roadmap](../README.md#13-current-roadmap) for what's intentionally not built yet (persistence, auth, multi-URL comparison, historical tracking, and a short list of smaller Stage 1 polish items).
