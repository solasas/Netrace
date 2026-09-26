/**
 * Represents a single phase in the network timeline.
 *
 * @typedef {Object} TimingPhase
 * @property {string} phase - Phase name: 'DNS', 'TCP', 'TLS', 'TTFB', 'DOWNLOAD', 'TOTAL'
 * @property {number} durationMs - Duration in milliseconds
 * @property {boolean} measured - true if directly measured, false if derived
 * @property {string} [status] - Status: 'SUCCESS', 'FAILED', 'SKIPPED', 'TIMEOUT'
 * @property {string} [measurement_note] - Brief explanation of what this timing represents
 * @property {Object} [metadata] - Phase-specific details (hostname, IP, port, version, etc.)
 * @property {string} [error] - Error message if status is 'FAILED'
 * @property {number} [startMs] - Position on the request timeline (only for sequential phases)
 * @property {number} [endMs] - End position on the request timeline (only for sequential phases)
 *
 * Timeline Positioning:
 * - startMs/endMs are ONLY set for sequential phases (TTFB, DOWNLOAD)
 * - For independent phases (DNS, TCP, TLS), startMs/endMs are undefined
 * - Do NOT manufacture startMs by summing independent measurements
 * - Sequential constraint: DOWNLOAD.startMs === TTFB.endMs
 *
 * Measured vs. Derived:
 * - Measured: directly obtained from analyzer (DNS duration, TCP duration, TLS duration, TTFB, Download)
 * - Derived: calculated from other measurements (totalMs = ttfbMs + downloadMs)
 *
 * Independent vs. Sequential:
 * - DNS, TCP, TLS: independent measurements on separate connections (no startMs/endMs)
 * - TTFB, DOWNLOAD: sequential measurements on the same HTTP request (have startMs/endMs)
 *   (DOWNLOAD always begins when TTFB ends: totalMs = ttfbMs + downloadMs)
 *
 * Phase-specific notes:
 *
 * DNS:
 *   - Duration: hostname resolution time
 *   - Measured: true (from DnsAnalyzer)
 *   - Metadata: { hostname, resolvedIps: string[] }
 *   - Independent measurement on dedicated probe connection
 *   - NO startMs/endMs (not positioned on request timeline)
 *
 * TCP:
 *   - Duration: connection establishment time
 *   - Measured: true (from TcpAnalyzer)
 *   - Metadata: { ip, port }
 *   - Independent measurement on dedicated probe connection
 *   - NO startMs/endMs (not positioned on request timeline)
 *
 * TLS:
 *   - Duration: handshake time
 *   - Measured: true (from TlsAnalyzer), null if HTTP
 *   - Metadata: { tlsVersion, cipherSuite, certificateSubject, certificateIssuer }
 *   - Independent measurement on dedicated probe connection
 *   - Skipped for HTTP URLs
 *   - NO startMs/endMs (not positioned on request timeline)
 *
 * TTFB (Observed Time to First Byte):
 *   - Duration: request send to response headers available
 *   - Measured: true (from HttpAnalyzer)
 *   - Metadata: { httpVersion }
 *   - Bundles network latency + server processing + response transmission initiation
 *   - NOT equivalent to backend processing time in isolation
 *   - HAS startMs (always 0) and endMs (= durationMs) on request timeline
 *
 * DOWNLOAD:
 *   - Duration: response body download time
 *   - Measured: true (from HttpAnalyzer), but derived as (totalMs - ttfbMs)
 *   - Metadata: { responseSizeBytes, truncated: boolean }
 *   - Sequential with TTFB on same HTTP request
 *   - HAS startMs (= TTFB.endMs) and endMs (= TTFB.endMs + durationMs) on request timeline
 *
 * TOTAL (implicit):
 *   - Duration: complete HTTP transaction time
 *   - Measured: true (from HttpAnalyzer)
 *   - Derived property: totalMs = ttfbMs + downloadMs (guaranteed for HTTP)
 */

/**
 * Phases used in network analysis.
 * Do not include this in the timing phase list; it's informational only.
 */
export const PHASE_NAMES = {
  DNS: 'DNS',
  TCP: 'TCP',
  TLS: 'TLS',
  TTFB: 'TTFB',
  DOWNLOAD: 'DOWNLOAD',
}

/**
 * Status values for a phase.
 */
export const PHASE_STATUS = {
  SUCCESS: 'SUCCESS',
  FAILED: 'FAILED',
  SKIPPED: 'SKIPPED',
  TIMEOUT: 'TIMEOUT',
}

/**
 * Descriptions of what each phase measures (for tooltips/explanations).
 */
export const PHASE_DESCRIPTIONS = {
  DNS: 'Hostname resolution time. Independent measurement on a dedicated connection.',
  TCP: 'Time to establish TCP connection. Independent measurement on a dedicated connection.',
  TLS: 'TLS handshake time. Independent measurement on a dedicated connection.',
  TTFB: 'Observed time until response data became available. From request send to response headers parsed. Bundles network latency, request transmission, server processing, and response transmission initiation.',
  DOWNLOAD: 'Time spent downloading response body. Sequential with TTFB on the same request.',
}

/**
 * Visual styling for phases — consistent across all visualizations.
 * Uses a professional developer-tool color palette.
 */
export const PHASE_COLORS = {
  DNS: 'bg-indigo-400',
  TCP: 'bg-indigo-500',
  TLS: 'bg-indigo-600',
  TTFB: 'bg-emerald-500',
  DOWNLOAD: 'bg-emerald-700',
}

/**
 * Tailwind color classes for text/border variants of phase colors.
 */
export const PHASE_TEXT_COLORS = {
  DNS: 'text-indigo-600',
  TCP: 'text-indigo-700',
  TLS: 'text-indigo-800',
  TTFB: 'text-emerald-600',
  DOWNLOAD: 'text-emerald-700',
}

export const PHASE_BORDER_COLORS = {
  DNS: 'border-indigo-300',
  TCP: 'border-indigo-400',
  TLS: 'border-indigo-500',
  TTFB: 'border-emerald-400',
  DOWNLOAD: 'border-emerald-500',
}

/**
 * Creates a measured phase.
 *
 * @param {string} phase - Phase name from PHASE_NAMES
 * @param {number} durationMs - Measured duration
 * @param {Object} metadata - Phase-specific metadata
 * @param {string} status - Status from PHASE_STATUS (defaults to 'SUCCESS')
 * @returns {TimingPhase}
 */
export function createPhase(phase, durationMs, metadata = {}, status = PHASE_STATUS.SUCCESS) {
  return {
    phase,
    durationMs,
    measured: true,
    status,
    measurement_note: PHASE_DESCRIPTIONS[phase] || 'Phase measurement',
    metadata,
    error: null,
  }
}

/**
 * Creates a failed phase.
 *
 * @param {string} phase - Phase name from PHASE_NAMES
 * @param {string} error - Error message
 * @param {number} [durationMs] - Optional partial duration measured before failure
 * @returns {TimingPhase}
 */
export function createFailedPhase(phase, error, durationMs = 0) {
  return {
    phase,
    durationMs,
    measured: durationMs > 0,
    status: PHASE_STATUS.FAILED,
    measurement_note: PHASE_DESCRIPTIONS[phase] || 'Phase measurement',
    metadata: {},
    error,
  }
}

/**
 * Creates a skipped phase (e.g., TLS for HTTP URLs).
 *
 * @param {string} phase - Phase name from PHASE_NAMES
 * @param {string} reason - Why it was skipped
 * @returns {TimingPhase}
 */
export function createSkippedPhase(phase, reason) {
  return {
    phase,
    durationMs: 0,
    measured: false,
    status: PHASE_STATUS.SKIPPED,
    measurement_note: reason,
    metadata: {},
    error: null,
  }
}

/**
 * Builds a timing phase list from an AnalyzeResponse or CompareResult.
 * Returns only phases that have valid data.
 *
 * For sequential phases (TTFB, DOWNLOAD), includes startMs/endMs positioning.
 * For independent phases (DNS, TCP, TLS), does NOT include positioning.
 *
 * @param {Object} result - AnalyzeResponse or CompareResult
 * @returns {TimingPhase[]}
 */
export function buildTimingPhases(result) {
  const phases = []

  // DNS (independent measurement, no positioning)
  if (result.dns && result.dns.success) {
    phases.push(
      createPhase(
        PHASE_NAMES.DNS,
        result.dns.durationMs,
        { hostname: result.dns.hostname, resolvedIps: [...(result.dns.resolvedIpv4 || []), ...(result.dns.resolvedIpv6 || [])] },
        PHASE_STATUS.SUCCESS,
      ),
    )
  } else if (result.dns && !result.dns.success) {
    phases.push(createFailedPhase(PHASE_NAMES.DNS, 'DNS resolution failed', result.dns.durationMs))
  }

  // TCP (independent measurement, no positioning)
  if (result.tcp && result.tcp.success) {
    phases.push(
      createPhase(
        PHASE_NAMES.TCP,
        result.tcp.durationMs,
        { ip: result.tcp.host, port: result.tcp.port },
        PHASE_STATUS.SUCCESS,
      ),
    )
  } else if (result.tcp && !result.tcp.success) {
    phases.push(createFailedPhase(PHASE_NAMES.TCP, result.tcp.failureReason || 'Connection failed', result.tcp.durationMs))
  }

  // TLS (independent measurement, no positioning)
  if (result.tls !== undefined) {
    if (result.tls === null) {
      phases.push(createSkippedPhase(PHASE_NAMES.TLS, 'HTTP URL (TLS not applicable)'))
    } else if (result.tls.tlsVersion) {
      phases.push(
        createPhase(
          PHASE_NAMES.TLS,
          result.tls.durationMs,
          {
            tlsVersion: result.tls.tlsVersion,
            cipherSuite: result.tls.cipherSuite,
            certificateSubject: result.tls.certificateSubject,
            certificateIssuer: result.tls.certificateIssuer,
          },
          PHASE_STATUS.SUCCESS,
        ),
      )
    } else {
      phases.push(createFailedPhase(PHASE_NAMES.TLS, 'TLS handshake failed', result.tls.durationMs))
    }
  }

  // TTFB (sequential measurement, has positioning)
  if (result.ttfbMs !== undefined && result.ttfbMs >= 0) {
    const ttfbStartMs = 0
    const ttfbEndMs = result.ttfbMs

    const ttfbPhase = createPhase(
      PHASE_NAMES.TTFB,
      result.ttfbMs,
      { httpVersion: result.protocol },
      result.statusCode >= 200 && result.statusCode < 600 ? PHASE_STATUS.SUCCESS : PHASE_STATUS.FAILED,
    )
    // Add timeline positioning for sequential measurement
    ttfbPhase.startMs = ttfbStartMs
    ttfbPhase.endMs = ttfbEndMs

    phases.push(ttfbPhase)

    // DOWNLOAD (sequential with TTFB, has positioning)
    const downloadStartMs = ttfbEndMs
    const downloadEndMs = ttfbEndMs + (result.downloadMs || 0)

    const downloadPhase = createPhase(
      PHASE_NAMES.DOWNLOAD,
      result.downloadMs || 0,
      {
        responseSizeBytes: result.responseSizeBytes || 0,
        truncated: result.bodyTruncated || false,
      },
      PHASE_STATUS.SUCCESS,
    )
    // Add timeline positioning for sequential measurement
    downloadPhase.startMs = downloadStartMs
    downloadPhase.endMs = downloadEndMs

    phases.push(downloadPhase)
  }

  return phases
}
