import { useState } from 'react'
import { PHASE_NAMES, PHASE_STATUS, PHASE_DESCRIPTIONS, buildTimingPhases } from '../types/TimingPhase'

const MIN_BAR_PERCENT = 2

const PHASE_COLORS = {
  [PHASE_NAMES.DNS]: 'bg-indigo-400',
  [PHASE_NAMES.TCP]: 'bg-indigo-500',
  [PHASE_NAMES.TLS]: 'bg-indigo-600',
  [PHASE_NAMES.TTFB]: 'bg-emerald-500',
  [PHASE_NAMES.DOWNLOAD]: 'bg-emerald-700',
}

function percentOf(durationMs, maxDurationMs) {
  return maxDurationMs > 0 ? Math.max((durationMs / maxDurationMs) * 100, MIN_BAR_PERCENT) : 0
}

function CompactPhaseBar({ phase, maxDurationMs, isSelected, onSelect }) {
  const { phase: phaseName, durationMs, status } = phase
  const widthPercent = percentOf(durationMs, maxDurationMs)
  const colorClass = PHASE_COLORS[phaseName] || 'bg-slate-400'

  if (status === PHASE_STATUS.SKIPPED) {
    return (
      <div className="h-3 w-full rounded border border-dashed border-slate-200" />
    )
  }

  return (
    <div
      className={`relative h-3 w-full rounded bg-slate-100 cursor-pointer group transition-all ${
        isSelected ? 'ring-2 ring-indigo-300' : 'hover:opacity-80'
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
      <div
        className={`h-3 rounded ${colorClass} transition-opacity ${status === PHASE_STATUS.FAILED ? 'opacity-50' : ''}`}
        style={{ width: `${widthPercent}%` }}
      />
      <div className="pointer-events-none absolute -top-10 left-0 z-10 hidden w-max max-w-48 rounded bg-slate-900 px-2 py-1 text-xs text-white shadow group-hover:block">
        <div className="font-semibold">{phaseName}: {durationMs} ms</div>
        <div className="text-slate-400 text-[10px] mt-0.5">{PHASE_DESCRIPTIONS[phaseName]}</div>
      </div>
    </div>
  )
}

function UrlWaterfall({ url, result, maxDurationMs, selectedPhase, onSelectPhase }) {
  const phases = buildTimingPhases(result)

  // Separate independent phases from sequential
  const independentPhases = phases.filter(
    (p) => ![PHASE_NAMES.TTFB, PHASE_NAMES.DOWNLOAD].includes(p.phase),
  )
  const ttfbPhase = phases.find((p) => p.phase === PHASE_NAMES.TTFB)
  const downloadPhase = phases.find((p) => p.phase === PHASE_NAMES.DOWNLOAD)

  const isSuccess = result.success
  const statusIcon = isSuccess ? '✓' : '✕'
  const statusColor = isSuccess ? 'text-emerald-600' : 'text-red-600'

  return (
    <div className="flex flex-col gap-2">
      {/* URL and Status */}
      <div className="flex items-start gap-3">
        <div className="flex-1 min-w-0">
          <div className="text-xs font-medium text-slate-700 break-all">{url}</div>
          {!isSuccess && result.error && (
            <div className="text-xs text-red-600 mt-0.5">{result.error}</div>
          )}
        </div>
        <span className={`shrink-0 text-sm font-semibold ${statusColor}`}>{statusIcon}</span>
      </div>

      {/* Waterfall Bars */}
      {isSuccess && (
        <div className="flex flex-col gap-1 ml-6">
          {/* Independent Phases */}
          {independentPhases.map((phase) => (
            <div key={phase.phase} className="flex items-center gap-2">
              <span className="w-16 shrink-0 text-[10px] font-medium text-slate-600">
                {phase.phase}
              </span>
              <CompactPhaseBar
                phase={phase}
                maxDurationMs={maxDurationMs}
                isSelected={selectedPhase === phase.phase}
                onSelect={() => onSelectPhase(url, phase.phase)}
              />
              <span className="w-12 shrink-0 text-right text-[10px] text-slate-500">
                {phase.durationMs} ms
              </span>
            </div>
          ))}

          {/* Sequential Phases (TTFB + Download as one bar) */}
          {ttfbPhase && (
            <div className="flex items-center gap-2">
              <span className="w-16 shrink-0 text-[10px] font-medium text-slate-600">
                Request
              </span>
              <div className="flex-1 flex h-3 rounded bg-slate-100 overflow-hidden">
                <CompactPhaseBar
                  phase={ttfbPhase}
                  maxDurationMs={maxDurationMs}
                  isSelected={selectedPhase === PHASE_NAMES.TTFB}
                  onSelect={() => onSelectPhase(url, PHASE_NAMES.TTFB)}
                />
                {downloadPhase && downloadPhase.durationMs > 0 && (
                  <div
                    className={`h-3 bg-emerald-700 cursor-pointer transition-opacity ${
                      selectedPhase === PHASE_NAMES.DOWNLOAD ? 'ring-2 ring-indigo-300' : 'hover:opacity-80'
                    }`}
                    style={{
                      width: `${percentOf(downloadPhase.durationMs, maxDurationMs)}%`,
                    }}
                    onClick={() => onSelectPhase(url, PHASE_NAMES.DOWNLOAD)}
                    role="button"
                    tabIndex={0}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter' || e.key === ' ') {
                        e.preventDefault()
                        onSelectPhase(url, PHASE_NAMES.DOWNLOAD)
                      }
                    }}
                  />
                )}
              </div>
              <span className="w-12 shrink-0 text-right text-[10px] text-slate-500">
                {ttfbPhase.durationMs + (downloadPhase?.durationMs || 0)} ms
              </span>
            </div>
          )}
        </div>
      )}
    </div>
  )
}

function ComparisonWaterfall({ results = [] }) {
  const [selectedUrl, setSelectedUrl] = useState(null)
  const [selectedPhase, setSelectedPhase] = useState(null)

  if (!results || results.length === 0) {
    return null
  }

  // Calculate global max duration from all successful results
  const allPhases = results.flatMap((r) => buildTimingPhases(r))
  const knownDurations = allPhases
    .filter((p) => p.status !== PHASE_STATUS.SKIPPED && p.status !== PHASE_STATUS.FAILED)
    .map((p) => p.durationMs)
  const maxDurationMs = Math.max(...knownDurations, 1)

  function handleSelectPhase(url, phase) {
    setSelectedUrl(url)
    setSelectedPhase(phase)
  }

  return (
    <section className="flex flex-col gap-4 rounded-md border border-slate-200 bg-white p-6">
      <div>
        <h3 className="text-sm font-semibold uppercase tracking-wide text-slate-500 mb-1">
          Network Timeline Comparison
        </h3>
        <p className="text-xs text-slate-500">
          Each URL's phase durations on a shared scale. DNS, TCP, and TLS are independent measurements;
          Request is the sequential TTFB + Download pair.
        </p>
      </div>

      <div className="flex flex-col gap-6">
        {results.map((result) => (
          <UrlWaterfall
            key={result.url}
            url={result.url}
            result={result}
            maxDurationMs={maxDurationMs}
            selectedPhase={selectedPhase}
            onSelectPhase={handleSelectPhase}
          />
        ))}
      </div>

      {/* Legend */}
      <div className="flex flex-wrap gap-3 text-[10px] border-t border-slate-200 pt-3 mt-2">
        <div className="flex items-center gap-1.5">
          <div className="w-3 h-2 rounded bg-indigo-400" />
          <span className="text-slate-600">DNS</span>
        </div>
        <div className="flex items-center gap-1.5">
          <div className="w-3 h-2 rounded bg-indigo-500" />
          <span className="text-slate-600">TCP</span>
        </div>
        <div className="flex items-center gap-1.5">
          <div className="w-3 h-2 rounded bg-indigo-600" />
          <span className="text-slate-600">TLS</span>
        </div>
        <div className="flex items-center gap-1.5">
          <div className="w-3 h-2 rounded bg-emerald-500" />
          <span className="text-slate-600">TTFB</span>
        </div>
        <div className="flex items-center gap-1.5">
          <div className="w-3 h-2 rounded bg-emerald-700" />
          <span className="text-slate-600">Download</span>
        </div>
      </div>
    </section>
  )
}

export default ComparisonWaterfall
