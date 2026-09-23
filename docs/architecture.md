# Architecture

## Components

```
┌────────────┐      POST /api/analyze      ┌────────────┐      real HTTP(S) request      ┌──────────────┐
│  Frontend  │ ──────────────────────────▶ │  Backend   │ ──────────────────────────────▶ │  Target URL  │
│ React+Vite │ ◀────────────────────────── │ Spring Boot│ ◀────────────────────────────── │ (user-given) │
└────────────┘      JSON measurements      └────────────┘      real response              └──────────────┘
```

- **Frontend** (`frontend/`) — a React/Vite single-page app. Collects a URL from the user, calls the backend's `/api/analyze` endpoint, and renders the returned measurements (dashboard + waterfall timeline). It never talks to the target URL directly — browsers can't observe raw TCP/TLS/DNS timing, and CORS would block most cross-origin targets anyway.
- **Backend** (`backend/`) — a Spring Boot application. It is the only component that performs the actual network request to the user-supplied URL, using standard Java networking APIs so the timing data reflects a real observed request rather than a synthetic or client-side estimate.
- **Target URL** — any public HTTP/HTTPS endpoint the user asks about. Untrusted input: the backend treats fetching it as a security-sensitive operation (see [Security](#security)).

## Why measurement happens server-side

Browsers do not expose DNS resolution time, raw TCP connect time, or TLS handshake time to JavaScript (the Resource Timing / Navigation Timing APIs only offer approximations, and are further limited by cross-origin restrictions). To report real, unapproximated measurements, the request has to be made by the backend using lower-level Java networking primitives, not `fetch()` in the browser.

## Security

Because the backend accepts an arbitrary user-supplied URL and fetches it, it is treated as a Server-Side Request Forgery (SSRF) surface: the backend is a trusted service capable of reaching internal/private network addresses that end users normally cannot reach directly. As URL fetching is implemented, this needs to account for restricting or flagging requests to private, loopback, and link-local address ranges. This is called out here rather than deferred silently, per the project's engineering principles.

## Non-goals (Stage 1)

No persistence, no authentication, no multi-URL comparison, no historical tracking. See the [README](../README.md) and [measurement phases](measurement-phases.md) for what Stage 1 does cover.
