import { useState } from 'react'
import { PHASE_NAMES, PHASE_STATUS, PHASE_DESCRIPTIONS, PHASE_COLORS, buildTimingPhases } from '../types/TimingPhase'

const MIN_BAR_PERCENT = 2

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

function NormalizedComparison({ results, selectedPhase, onSelectPhase }) {
  return (
    <div className="flex flex-col gap-4">
      <div className="text-xs text-slate-600 bg-slate-50 border border-slate-200 rounded p-3">
        <strong>Relative Observed Phase Durations:</strong> Each URL's total HTTP time = 100%.
        Percentages show approximate contribution of each measured phase. Note: DNS, TCP, and TLS
        are independent measurements, not part of the sequential HTTP request.
      </div>

      <div className="flex flex-col gap-6">
        {results.map((result) => {
          if (!result.success) {
            return (
              <div key={result.url} className="flex flex-col gap-2">
                <div className="text-xs font-medium text-slate-700">{result.url}</div>
                <div className="text-xs text-red-600">{result.error}</div>
              </div>
            )
          }

          const totalMs = result.totalTimeMs
          const ttfbMs = result.ttfbMs || 0
          const downloadMs = result.downloadMs || 0
          const dnsMs = result.probes?.dns?.durationMs || 0
          const tcpMs = result.probes?.tcp?.durationMs || 0
          const tlsMs = result.probes?.tls?.durationMs || 0

          const ttfbPercent = (ttfbMs / totalMs) * 100
          const downloadPercent = (downloadMs / totalMs) * 100
          const dnsPercent = (dnsMs / totalMs) * 100
          const tcpPercent = (tcpMs / totalMs) * 100
          const tlsPercent = tlsMs ? (tlsMs / totalMs) * 100 : 0

          const phases = [
            { name: PHASE_NAMES.DNS, percent: dnsPercent, color: PHASE_COLORS[PHASE_NAMES.DNS] },
            { name: PHASE_NAMES.TCP, percent: tcpPercent, color: PHASE_COLORS[PHASE_NAMES.TCP] },
            ...(tlsMs ? [{ name: PHASE_NAMES.TLS, percent: tlsPercent, color: PHASE_COLORS[PHASE_NAMES.TLS] }] : []),
            { name: PHASE_NAMES.TTFB, percent: ttfbPercent, color: PHASE_COLORS[PHASE_NAMES.TTFB] },
            { name: PHASE_NAMES.DOWNLOAD, percent: downloadPercent, color: PHASE_COLORS[PHASE_NAMES.DOWNLOAD] },
          ]

          return (
            <div key={result.url} className="flex flex-col gap-2">
              <div className="text-xs font-medium text-slate-700">{result.url}</div>
              <div className="flex h-6 w-full rounded bg-slate-100 overflow-hidden border border-slate-200">
                {phases.map((phase) => (
                  <div
                    key={phase.name}
                    className={`${phase.color} cursor-pointer hover:opacity-80 transition-opacity relative group`}
                    style={{ width: `${Math.max(phase.percent, 2)}%` }}
                    onClick={() => onSelectPhase(result.url, phase.name)}
                    role="button"
                    tabIndex={0}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter' || e.key === ' ') {
                        e.preventDefault()
                        onSelectPhase(result.url, phase.name)
                      }
                    }}
                  >
                    {phase.percent > 5 && (
                      <span className="text-[10px] font-bold text-white opacity-80 absolute inset-0 flex items-center justify-center">
                        {Math.round(phase.percent)}%
                      </span>
                    )}
                    <div className="pointer-events-none absolute -top-8 left-0 z-10 hidden w-max rounded bg-slate-900 px-2 py-1 text-xs text-white shadow group-hover:block">
                      <div className="font-semibold">{phase.name}</div>
                      <div className="text-slate-300">{Math.round(phase.percent)}%</div>
                    </div>
                  </div>
                ))}
              </div>
              <div className="flex flex-wrap gap-2 text-[10px]">
                {phases.map((phase) => (
                  <span key={phase.name} className="text-slate-600">
                    {phase.name} {Math.round(phase.percent)}%
                  </span>
                ))}
              </div>
            </div>
          )
        })}
      </div>
    </div>
  )
}

function OverlayWaterfall({ results, maxDurationMs, selectedPhase, onSelectPhase }) {
  const phaseNames = [PHASE_NAMES.DNS, PHASE_NAMES.TCP, PHASE_NAMES.TLS, PHASE_NAMES.TTFB, PHASE_NAMES.DOWNLOAD]

  return (
    <div className="flex flex-col gap-4">
      <div className="text-xs text-slate-600 bg-slate-50 border border-slate-200 rounded p-3">
        <strong>Phase Duration Comparison:</strong> Each phase shown for all URLs on a shared scale.
        DNS, TCP, and TLS are independent measurements (not sequential). TTFB and Download are
        sequential on the same request.
      </div>

      <div className="flex flex-col gap-4">
        {phaseNames.map((phase) => (
          <div key={phase} className="flex flex-col gap-1">
            <div className="text-xs font-medium text-slate-700">{phase}</div>
            <div className="flex flex-col gap-1 ml-4">
              {results.map((result) => {
                const phases = buildTimingPhases(result)
                const phaseData = phases.find((p) => p.phase === phase)

                if (!phaseData) return null

                const widthPercent = percentOf(phaseData.durationMs, maxDurationMs)
                const colorClass = PHASE_COLORS[phase] || 'bg-slate-400'
                const isSelected = selectedPhase === phase

                return (
                  <div key={result.url} className="flex items-center gap-2">
                    <span className="w-24 shrink-0 text-xs text-slate-600 truncate">{result.url}</span>
                    <div
                      className={`flex-1 h-2 rounded bg-slate-100 cursor-pointer transition-all ${
                        isSelected ? 'ring-2 ring-indigo-300' : 'hover:opacity-80'
                      }`}
                      onClick={() => onSelectPhase(result.url, phase)}
                      role="button"
                      tabIndex={0}
                      onKeyDown={(e) => {
                        if (e.key === 'Enter' || e.key === ' ') {
                          e.preventDefault()
                          onSelectPhase(result.url, phase)
                        }
                      }}
                    >
                      {phaseData.status !== PHASE_STATUS.SKIPPED && (
                        <div
                          className={`h-2 rounded ${colorClass} ${
                            phaseData.status === PHASE_STATUS.FAILED ? 'opacity-50' : ''
                          }`}
                          style={{ width: `${widthPercent}%` }}
                        />
                      )}
                    </div>
                    <span className="w-12 shrink-0 text-right text-xs text-slate-500">
                      {phaseData.status === PHASE_STATUS.SKIPPED
                        ? 'n/a'
                        : phaseData.status === PHASE_STATUS.FAILED
                          ? 'fail'
                          : `${phaseData.durationMs} ms`}
                    </span>
                  </div>
                )
              })}
            </div>
          </div>
        ))}
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
  const [displayMode, setDisplayMode] = useState('sideBySide') // 'sideBySide', 'overlay', or 'normalized'
  const [visibleUrls, setVisibleUrls] = useState(results.map((r) => r.url))

  if (!results || results.length === 0) {
    return null
  }

  function toggleUrlVisibility(url) {
    setVisibleUrls((prev) =>
      prev.includes(url) ? prev.filter((u) => u !== url) : [...prev, url]
    )
  }

  const filteredResults = results.filter((r) => visibleUrls.includes(r.url))

  // Calculate global max duration from all successful results
  const allPhases = filteredResults.flatMap((r) => buildTimingPhases(r))
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
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex-1">
          <h3 className="text-sm font-semibold uppercase tracking-wide text-slate-500 mb-1">
            Network Timeline Comparison
          </h3>
          <p className="text-xs text-slate-500">
            {displayMode === 'sideBySide'
              ? 'Each URL's phase durations on a shared scale. DNS, TCP, and TLS are independent measurements; Request is the sequential TTFB + Download pair.'
              : displayMode === 'overlay'
                ? 'Phase Duration Comparison — relative durations on a normalized scale. Independent phases (DNS, TCP, TLS) and sequential phases (TTFB + Download) shown for comparison.'
                : 'Relative observed phase durations as percentage of total HTTP time. Shows approximate contribution of each measured phase.'}
          </p>
        </div>

        <div className="flex gap-1 bg-slate-100 rounded p-1 shrink-0 flex-wrap">
          <button
            onClick={() => setDisplayMode('sideBySide')}
            className={`text-xs font-medium px-3 py-1 rounded transition-colors ${
              displayMode === 'sideBySide'
                ? 'bg-white text-slate-900 shadow-sm'
                : 'text-slate-600 hover:text-slate-900'
            }`}
          >
            Side-by-side
          </button>
          <button
            onClick={() => setDisplayMode('overlay')}
            className={`text-xs font-medium px-3 py-1 rounded transition-colors ${
              displayMode === 'overlay'
                ? 'bg-white text-slate-900 shadow-sm'
                : 'text-slate-600 hover:text-slate-900'
            }`}
          >
            Overlay
          </button>
          <button
            onClick={() => setDisplayMode('normalized')}
            className={`text-xs font-medium px-3 py-1 rounded transition-colors ${
              displayMode === 'normalized'
                ? 'bg-white text-slate-900 shadow-sm'
                : 'text-slate-600 hover:text-slate-900'
            }`}
          >
            Normalized
          </button>
        </div>
      </div>

      {/* URL Visibility Toggle */}
      {results.length > 1 && (
        <div className="flex flex-wrap gap-2 border-b border-slate-200 pb-4">
          {results.map((result) => (
            <button
              key={result.url}
              onClick={() => toggleUrlVisibility(result.url)}
              className={`text-xs px-2 py-1 rounded transition-colors ${
                visibleUrls.includes(result.url)
                  ? 'bg-slate-200 text-slate-900'
                  : 'bg-slate-100 text-slate-500 opacity-50'
              }`}
            >
              {result.url} {!visibleUrls.includes(result.url) ? '(hidden)' : ''}
            </button>
          ))}
        </div>
      )}

      {displayMode === 'sideBySide' ? (
        <div className="flex flex-col gap-6">
          {filteredResults.map((result) => (
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
      ) : displayMode === 'overlay' ? (
        <OverlayWaterfall
          results={filteredResults}
          maxDurationMs={maxDurationMs}
          selectedPhase={selectedPhase}
          onSelectPhase={handleSelectPhase}
        />
      ) : (
        <NormalizedComparison
          results={filteredResults}
          selectedPhase={selectedPhase}
          onSelectPhase={handleSelectPhase}
        />
      )}

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
