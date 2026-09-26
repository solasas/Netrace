import { useState } from 'react'
import { PHASE_NAMES, PHASE_STATUS, PHASE_DESCRIPTIONS, PHASE_COLORS } from '../types/TimingPhase'

const MIN_BAR_PERCENT = 2

function percentOf(durationMs, maxDurationMs) {
  return maxDurationMs > 0 ? Math.max((durationMs / maxDurationMs) * 100, MIN_BAR_PERCENT) : 0
}

function PhaseTooltip({ phase }) {
  const { phase: phaseName, durationMs, status, error, metadata } = phase

  if (status === PHASE_STATUS.FAILED) {
    return (
      <>
        <div className="font-semibold text-red-300">{phaseName}</div>
        <div className="text-slate-300 text-xs">Status: Failed</div>
        <div className="text-slate-400 text-xs mt-1">{error}</div>
      </>
    )
  }

  if (status === PHASE_STATUS.SKIPPED) {
    return (
      <>
        <div className="font-semibold text-slate-300">{phaseName}</div>
        <div className="text-slate-300 text-xs">Not applicable</div>
        <div className="text-slate-400 text-xs mt-1">{phase.measurement_note}</div>
      </>
    )
  }

  // SUCCESS status
  return (
    <>
      <div className="font-semibold">{phaseName}</div>
      <div className="text-slate-300 text-xs">Duration: {durationMs} ms</div>
      <div className="text-slate-300 text-xs">Status: Measured</div>

      {/* Phase-specific metadata */}
      {phaseName === PHASE_NAMES.DNS && metadata.hostname && (
        <div className="border-t border-slate-700 mt-1 pt-1">
          <div className="text-slate-400 text-xs">Hostname: {metadata.hostname}</div>
          {metadata.resolvedIps && metadata.resolvedIps.length > 0 && (
            <div className="text-slate-400 text-xs">
              Resolved: {metadata.resolvedIps.join(', ')}
            </div>
          )}
        </div>
      )}

      {phaseName === PHASE_NAMES.TCP && metadata.ip && (
        <div className="border-t border-slate-700 mt-1 pt-1">
          <div className="text-slate-400 text-xs">
            {metadata.ip}:{metadata.port}
          </div>
        </div>
      )}

      {phaseName === PHASE_NAMES.TLS && metadata.tlsVersion && (
        <div className="border-t border-slate-700 mt-1 pt-1">
          <div className="text-slate-400 text-xs">Version: {metadata.tlsVersion}</div>
          {metadata.cipherSuite && (
            <div className="text-slate-400 text-xs">Cipher: {metadata.cipherSuite}</div>
          )}
          {metadata.certificateSubject && (
            <div className="text-slate-400 text-xs">Subject: {metadata.certificateSubject}</div>
          )}
          {metadata.certificateIssuer && (
            <div className="text-slate-400 text-xs">Issuer: {metadata.certificateIssuer}</div>
          )}
        </div>
      )}

      {phaseName === PHASE_NAMES.TTFB && metadata.httpVersion && (
        <div className="border-t border-slate-700 mt-1 pt-1">
          <div className="text-slate-400 text-xs">Protocol: {metadata.httpVersion}</div>
        </div>
      )}

      {phaseName === PHASE_NAMES.DOWNLOAD && metadata.responseSizeBytes !== undefined && (
        <div className="border-t border-slate-700 mt-1 pt-1">
          <div className="text-slate-400 text-xs">
            Size: {(metadata.responseSizeBytes / 1024).toFixed(1)} KB
          </div>
          {metadata.truncated && (
            <div className="text-orange-300 text-xs">Truncated at size limit</div>
          )}
        </div>
      )}

      {/* Description */}
      <div className="border-t border-slate-700 mt-2 pt-1">
        <div className="text-slate-400 text-[10px]">{PHASE_DESCRIPTIONS[phaseName]}</div>
      </div>
    </>
  )
}

function ProbeBar({ phase, maxDurationMs, isSelected, onSelect }) {
  const { phase: phaseName, durationMs, status } = phase
  const widthPercent = percentOf(durationMs, maxDurationMs)
  const colorClass = PHASE_COLORS[phaseName] || 'bg-slate-400'

  if (status === PHASE_STATUS.SKIPPED) {
    return (
      <div className="flex items-center gap-3 cursor-default">
        <span className="w-20 shrink-0 text-xs font-medium text-slate-600">{phaseName}</span>
        <div className="group relative flex-1">
          <div className="h-5 flex-1 rounded border border-dashed border-slate-200" />
          <div className="pointer-events-none absolute -top-12 left-0 z-10 hidden w-max max-w-56 rounded bg-slate-900 px-2 py-1 text-xs text-white shadow group-hover:block group-focus-within:block">
            <PhaseTooltip phase={phase} />
          </div>
        </div>
        <span className="w-20 shrink-0 text-right text-xs text-slate-400">n/a</span>
      </div>
    )
  }

  return (
    <div
      className={`flex items-center gap-3 cursor-pointer transition-colors ${
        isSelected ? 'bg-slate-50 -mx-2 px-2 py-1 rounded' : 'hover:bg-slate-50'
      }`}
      onClick={() => onSelect(phaseName)}
      role="button"
      tabIndex={0}
      onKeyDown={(e) => {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault()
          onSelect(phaseName)
        }
      }}
    >
      <span className="w-20 shrink-0 text-xs font-medium text-slate-600">{phaseName}</span>
      <div className="group relative flex-1">
        <div className={`h-5 w-full rounded ${isSelected ? 'ring-2 ring-indigo-300' : ''}`}>
          <div
            className={`h-5 rounded ${colorClass} outline-none transition-opacity ${status === PHASE_STATUS.FAILED ? 'opacity-50' : ''}`}
            style={{ width: `${widthPercent}%` }}
            tabIndex={0}
            role="img"
            aria-label={`${phaseName}: ${durationMs} ms`}
          />
        </div>
        <div className="pointer-events-none absolute -top-12 left-0 z-10 hidden w-max max-w-56 rounded bg-slate-900 px-2 py-1 text-xs text-white shadow group-hover:block group-focus-within:block">
          <PhaseTooltip phase={phase} />
        </div>
      </div>
      <span className="w-20 shrink-0 text-right text-xs text-slate-500">
        {status === PHASE_STATUS.FAILED ? 'failed' : `0–${durationMs} ms`}
      </span>
    </div>
  )
}

function RequestTimelineBar({ ttfbPhase, downloadPhase, maxDurationMs, selectedPhase, onSelect }) {
  if (!ttfbPhase) return null

  const ttfbMs = ttfbPhase.durationMs
  const downloadMs = downloadPhase?.durationMs || 0
  const totalMs = ttfbMs + downloadMs
  const ttfbPercent = percentOf(ttfbMs, maxDurationMs)
  const downloadPercent = percentOf(downloadMs, maxDurationMs)

  return (
    <div className="flex items-center gap-3 pb-4">
      <span className="w-20 shrink-0 text-xs font-medium text-slate-600">Request</span>
      <div className="relative flex-1">
        <div className={`flex h-5 w-full overflow-hidden rounded ${selectedPhase === PHASE_NAMES.TTFB || selectedPhase === PHASE_NAMES.DOWNLOAD ? 'ring-2 ring-indigo-300' : ''}`}>
          <div
            className={`group/ttfb relative h-5 bg-emerald-500 outline-none cursor-pointer transition-all ${
              selectedPhase === PHASE_NAMES.TTFB ? 'ring-2 ring-indigo-300' : ''
            }`}
            style={{ width: `${ttfbPercent}%` }}
            tabIndex={0}
            role="button"
            aria-label={`TTFB: 0 ms to ${ttfbMs} ms (click for details)`}
            onClick={() => onSelect(PHASE_NAMES.TTFB)}
            onKeyDown={(e) => {
              if (e.key === 'Enter' || e.key === ' ') {
                e.preventDefault()
                onSelect(PHASE_NAMES.TTFB)
              }
            }}
          >
            <div className="pointer-events-none absolute -top-12 left-0 z-10 hidden w-max max-w-56 rounded bg-slate-900 px-2 py-1 text-xs text-white shadow group-hover/ttfb:block group-focus-within/ttfb:block">
              <PhaseTooltip phase={ttfbPhase} />
            </div>
          </div>
          {downloadPhase && downloadMs > 0 && (
            <div
              className={`group/download relative h-5 border-l border-white bg-emerald-700 outline-none cursor-pointer transition-all ${
                selectedPhase === PHASE_NAMES.DOWNLOAD ? 'ring-2 ring-indigo-300' : ''
              }`}
              style={{ width: `${downloadPercent}%` }}
              tabIndex={0}
              role="button"
              aria-label={`Download: ${ttfbMs} ms to ${totalMs} ms (click for details)`}
              onClick={() => onSelect(PHASE_NAMES.DOWNLOAD)}
              onKeyDown={(e) => {
                if (e.key === 'Enter' || e.key === ' ') {
                  e.preventDefault()
                  onSelect(PHASE_NAMES.DOWNLOAD)
                }
              }}
            >
              <div className="pointer-events-none absolute -top-12 left-0 z-10 hidden w-max max-w-56 rounded bg-slate-900 px-2 py-1 text-xs text-white shadow group-hover/download:block group-focus-within/download:block">
                <PhaseTooltip phase={downloadPhase} />
              </div>
            </div>
          )}
        </div>
        <div
          className="absolute top-full mt-0.5 -translate-x-1/2 whitespace-nowrap text-[10px] text-slate-400"
          style={{ left: `${ttfbPercent}%` }}
        >
          {ttfbMs} ms
        </div>
      </div>
      <span className="w-20 shrink-0 text-right text-xs text-slate-500">0–{totalMs} ms</span>
    </div>
  )
}

function PhaseDetail({ phase }) {
  if (!phase) return null

  const { phase: phaseName, durationMs, status, metadata } = phase

  return (
    <div className="mt-4 rounded-md bg-slate-50 p-4 border border-slate-200">
      <div className="flex items-start justify-between mb-3">
        <div>
          <h4 className="text-sm font-semibold text-slate-900">{phaseName} Details</h4>
          <p className="text-xs text-slate-500 mt-1">{PHASE_DESCRIPTIONS[phaseName]}</p>
        </div>
        <span className="text-xs font-medium px-2 py-1 bg-slate-200 rounded text-slate-700">
          {durationMs} ms
        </span>
      </div>

      <dl className="grid grid-cols-1 gap-2 text-xs">
        {phaseName === PHASE_NAMES.DNS && (
          <>
            <div>
              <dt className="text-slate-600 font-medium">Hostname</dt>
              <dd className="text-slate-900">{metadata?.hostname || '—'}</dd>
            </div>
            {metadata?.resolvedIps && metadata.resolvedIps.length > 0 && (
              <div>
                <dt className="text-slate-600 font-medium">Resolved Addresses</dt>
                <dd className="text-slate-900">
                  {metadata.resolvedIps.map((ip, i) => (
                    <div key={i}>{ip}</div>
                  ))}
                </dd>
              </div>
            )}
          </>
        )}

        {phaseName === PHASE_NAMES.TCP && (
          <>
            <div>
              <dt className="text-slate-600 font-medium">Address</dt>
              <dd className="text-slate-900 font-mono">{metadata?.ip || '—'}</dd>
            </div>
            <div>
              <dt className="text-slate-600 font-medium">Port</dt>
              <dd className="text-slate-900">{metadata?.port || '—'}</dd>
            </div>
          </>
        )}

        {phaseName === PHASE_NAMES.TLS && (
          <>
            <div>
              <dt className="text-slate-600 font-medium">TLS Version</dt>
              <dd className="text-slate-900">{metadata?.tlsVersion || '—'}</dd>
            </div>
            <div>
              <dt className="text-slate-600 font-medium">Cipher Suite</dt>
              <dd className="text-slate-900 break-all">{metadata?.cipherSuite || '—'}</dd>
            </div>
            <div>
              <dt className="text-slate-600 font-medium">Certificate Subject</dt>
              <dd className="text-slate-900">{metadata?.certificateSubject || '—'}</dd>
            </div>
            <div>
              <dt className="text-slate-600 font-medium">Certificate Issuer</dt>
              <dd className="text-slate-900">{metadata?.certificateIssuer || '—'}</dd>
            </div>
          </>
        )}

        {phaseName === PHASE_NAMES.TTFB && (
          <>
            <div>
              <dt className="text-slate-600 font-medium">HTTP Version</dt>
              <dd className="text-slate-900">{metadata?.httpVersion || '—'}</dd>
            </div>
            <div className="border-t border-slate-300 pt-2 mt-2">
              <dt className="text-slate-600 font-medium">Measurement</dt>
              <dd className="text-slate-700 text-[11px] mt-1">
                Request initiation to response headers parsed (not literal first byte, but practical equivalent via Java HttpClient API).
              </dd>
            </div>
          </>
        )}

        {phaseName === PHASE_NAMES.DOWNLOAD && (
          <>
            <div>
              <dt className="text-slate-600 font-medium">Response Size</dt>
              <dd className="text-slate-900">{metadata?.responseSizeBytes ? `${(metadata.responseSizeBytes / 1024).toFixed(1)} KB` : '—'}</dd>
            </div>
            {metadata?.truncated && (
              <div className="bg-orange-50 border border-orange-200 rounded p-2">
                <dt className="text-orange-700 font-medium text-xs">Truncated</dt>
                <dd className="text-orange-600 text-xs">Response exceeded size limit during download.</dd>
              </div>
            )}
          </>
        )}

        <div className="border-t border-slate-300 pt-2 mt-2">
          <dt className="text-slate-600 font-medium">Duration</dt>
          <dd className="text-slate-900">{durationMs} ms</dd>
        </div>
      </dl>
    </div>
  )
}

function TimeGrid() {
  const ticks = [0, 0.25, 0.5, 0.75, 1]

  return (
    <div className="pointer-events-none absolute inset-y-0 left-[5.75rem] right-[5.75rem]">
      {ticks.map((fraction) => (
        <div
          key={fraction}
          className="absolute inset-y-0 w-px bg-slate-100"
          style={{ left: `${fraction * 100}%` }}
        />
      ))}
    </div>
  )
}

function AxisTicks({ maxDurationMs }) {
  const ticks = [0, 0.25, 0.5, 0.75, 1].map((fraction) => Math.round(maxDurationMs * fraction))

  return (
    <div className="ml-[5.75rem] mr-[5.75rem] flex justify-between text-[10px] text-slate-400">
      {ticks.map((tick, index) => (
        <span key={index}>{tick} ms</span>
      ))}
    </div>
  )
}

function Waterfall({ phases = [] }) {
  const [selectedPhase, setSelectedPhase] = useState(null)

  // Separate independent phases from sequential phases
  const independentPhases = phases.filter(
    (p) => ![PHASE_NAMES.TTFB, PHASE_NAMES.DOWNLOAD].includes(p.phase),
  )
  const ttfbPhase = phases.find((p) => p.phase === PHASE_NAMES.TTFB)
  const downloadPhase = phases.find((p) => p.phase === PHASE_NAMES.DOWNLOAD)

  // Find the selected phase object
  const selectedPhaseObj = phases.find((p) => p.phase === selectedPhase)

  // Calculate max duration from all phases for shared scale
  const knownDurations = phases
    .filter((p) => p.status !== PHASE_STATUS.SKIPPED && p.status !== PHASE_STATUS.FAILED)
    .map((p) => p.durationMs)
  const maxDurationMs = Math.max(...knownDurations, 1)

  if (phases.length === 0) {
    return null
  }

  return (
    <section className="flex flex-col gap-6 rounded-md border border-slate-200 bg-white p-6">
      <h3 className="text-sm font-semibold uppercase tracking-wide text-slate-500">Waterfall</h3>

      <div className="relative flex flex-col gap-4">
        <TimeGrid />

        {/* Connection Probes Section */}
        {independentPhases.length > 0 && (
          <div>
            <p className="mb-2 text-xs font-medium text-slate-500">
              Connection probes - independent, one-off measurements. Each bar is its own
              0-to-duration window; they do not share a start time with each other or with the
              request below. <span className="text-slate-400">Click a bar for details.</span>
            </p>
            <div className="flex flex-col gap-2">
              {independentPhases.map((phase) => (
                <ProbeBar
                  key={phase.phase}
                  phase={phase}
                  maxDurationMs={maxDurationMs}
                  isSelected={selectedPhase === phase.phase}
                  onSelect={setSelectedPhase}
                />
              ))}
            </div>
          </div>
        )}

        {/* Request Timeline Section */}
        {ttfbPhase && (
          <div>
            <p className="mb-2 text-xs font-medium text-slate-500">
              Request timeline - the one genuinely sequential pair: download begins exactly when
              TTFB ends. The boundary between them is marked below. <span className="text-slate-400">Click a segment for details.</span>
            </p>
            <RequestTimelineBar
              ttfbPhase={ttfbPhase}
              downloadPhase={downloadPhase}
              maxDurationMs={maxDurationMs}
              selectedPhase={selectedPhase}
              onSelect={setSelectedPhase}
            />
          </div>
        )}

        <AxisTicks maxDurationMs={maxDurationMs} />
      </div>

      {/* Selected Phase Detail Panel */}
      {selectedPhaseObj && <PhaseDetail phase={selectedPhaseObj} />}

      <p className="text-xs text-slate-500">
        <strong>Visualization assumptions:</strong> every bar shares one scale (0 to the
        largest real duration on this chart), so lengths are comparable, but that shared scale
        does not mean the probe bars share a start time - DNS, TCP, and TLS each run over
        their own dedicated connection, separate from the real request and from each other, so
        they are drawn from their own 0, not chained end-to-end. TTFB and download are drawn as
        one bar with a marked boundary because that sequence is real and verified
        (totalTimeMs always equals ttfbMs + downloadMs). Bars for durations too small to see are
        floored to a minimum width; the visible width is therefore not always exactly
        proportional at the low end, but the label and tooltip always show the real measured
        value. See docs/timing-model.md for the full methodology.
      </p>
    </section>
  )
}

export default Waterfall
