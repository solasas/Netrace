const MIN_BAR_PERCENT = 2

function percentOf(durationMs, maxDurationMs) {
  return maxDurationMs > 0 ? Math.max((durationMs / maxDurationMs) * 100, MIN_BAR_PERCENT) : 0
}

function ProbeBar({ label, durationMs, colorClass, maxDurationMs }) {
  const widthPercent = percentOf(durationMs, maxDurationMs)

  return (
    <div className="flex items-center gap-3">
      <span className="w-20 shrink-0 text-xs font-medium text-slate-600">{label}</span>
      <div className="group relative flex-1">
        <div className="h-5 w-full rounded bg-slate-100">
          <div
            className={`h-5 rounded ${colorClass} outline-none`}
            style={{ width: `${widthPercent}%` }}
            tabIndex={0}
            role="img"
            aria-label={`${label}: ${durationMs} ms, its own dedicated connection - not positioned on the request's clock`}
          />
        </div>
        <div className="pointer-events-none absolute -top-12 left-0 z-10 hidden w-max max-w-56 rounded bg-slate-900 px-2 py-1 text-xs text-white shadow group-hover:block group-focus-within:block">
          <div className="font-semibold">
            {label}: {durationMs} ms
          </div>
          <div className="text-slate-300">0 ms – {durationMs} ms of its own dedicated connection</div>
        </div>
      </div>
      <span className="w-20 shrink-0 text-right text-xs text-slate-500">
        0–{durationMs} ms
      </span>
    </div>
  )
}

function NotApplicableBar({ label }) {
  return (
    <div className="flex items-center gap-3">
      <span className="w-20 shrink-0 text-xs font-medium text-slate-600">{label}</span>
      <div className="h-5 flex-1 rounded border border-dashed border-slate-200" />
      <span className="w-20 shrink-0 text-right text-xs text-slate-400">n/a</span>
    </div>
  )
}

function RequestTimelineBar({ ttfbMs, downloadMs, maxDurationMs }) {
  const totalMs = ttfbMs + downloadMs
  const ttfbPercent = percentOf(ttfbMs, maxDurationMs)
  const downloadPercent = percentOf(downloadMs, maxDurationMs)

  return (
    <div className="flex items-center gap-3 pb-4">
      <span className="w-20 shrink-0 text-xs font-medium text-slate-600">Request</span>
      <div className="relative flex-1">
        <div className="flex h-5 w-full overflow-hidden rounded bg-slate-100">
          <div
            className="group/ttfb relative h-5 bg-emerald-500 outline-none"
            style={{ width: `${ttfbPercent}%` }}
            tabIndex={0}
            role="img"
            aria-label={`TTFB: 0 ms to ${ttfbMs} ms`}
          >
            <div className="pointer-events-none absolute -top-12 left-0 z-10 hidden w-max rounded bg-slate-900 px-2 py-1 text-xs text-white shadow group-hover/ttfb:block group-focus-within/ttfb:block">
              <div className="font-semibold">TTFB: {ttfbMs} ms</div>
              <div className="text-slate-300">0 ms – {ttfbMs} ms</div>
            </div>
          </div>
          <div
            className="group/download relative h-5 border-l border-white bg-emerald-700 outline-none"
            style={{ width: `${downloadPercent}%` }}
            tabIndex={0}
            role="img"
            aria-label={`Download: ${ttfbMs} ms to ${totalMs} ms`}
          >
            <div className="pointer-events-none absolute -top-12 left-0 z-10 hidden w-max rounded bg-slate-900 px-2 py-1 text-xs text-white shadow group-hover/download:block group-focus-within/download:block">
              <div className="font-semibold">Download: {downloadMs} ms</div>
              <div className="text-slate-300">
                {ttfbMs} ms – {totalMs} ms
              </div>
            </div>
          </div>
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

function Waterfall({ dnsMs, tcpMs, tlsMs, ttfbMs, downloadMs }) {
  const knownDurations = [dnsMs, tcpMs, tlsMs, ttfbMs, downloadMs].filter(
    (value) => value != null,
  )
  const maxDurationMs = Math.max(...knownDurations, 1)

  return (
    <section className="flex flex-col gap-6 rounded-md border border-slate-200 bg-white p-6">
      <h3 className="text-sm font-semibold uppercase tracking-wide text-slate-500">Waterfall</h3>

      <div className="relative flex flex-col gap-4">
        <TimeGrid />

        <div>
          <p className="mb-2 text-xs font-medium text-slate-500">
            Connection probes - independent, one-off measurements. Each bar is its own
            0-to-duration window; they do not share a start time with each other or with the
            request below.
          </p>
          <div className="flex flex-col gap-2">
            <ProbeBar
              label="DNS"
              durationMs={dnsMs}
              colorClass="bg-indigo-400"
              maxDurationMs={maxDurationMs}
            />
            <ProbeBar
              label="TCP"
              durationMs={tcpMs}
              colorClass="bg-indigo-500"
              maxDurationMs={maxDurationMs}
            />
            {tlsMs != null ? (
              <ProbeBar
                label="TLS"
                durationMs={tlsMs}
                colorClass="bg-indigo-600"
                maxDurationMs={maxDurationMs}
              />
            ) : (
              <NotApplicableBar label="TLS" />
            )}
          </div>
        </div>

        <div>
          <p className="mb-2 text-xs font-medium text-slate-500">
            Request timeline - the one genuinely sequential pair: download begins exactly when
            TTFB ends. The boundary between them is marked below.
          </p>
          <RequestTimelineBar ttfbMs={ttfbMs} downloadMs={downloadMs} maxDurationMs={maxDurationMs} />
        </div>

        <AxisTicks maxDurationMs={maxDurationMs} />
      </div>

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
        value. See docs/measurement.md for the full methodology.
      </p>
    </section>
  )
}

export default Waterfall
