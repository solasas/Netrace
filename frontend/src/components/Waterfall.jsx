const MIN_BAR_PERCENT = 2

function WaterfallBar({ label, durationMs, colorClass, maxDurationMs }) {
  const widthPercent =
    maxDurationMs > 0 ? Math.max((durationMs / maxDurationMs) * 100, MIN_BAR_PERCENT) : 0

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
            aria-label={`${label}: ${durationMs} ms`}
          />
        </div>
        <div className="pointer-events-none absolute -top-8 left-0 z-10 hidden whitespace-nowrap rounded bg-slate-900 px-2 py-1 text-xs text-white shadow group-hover:block group-focus-within:block">
          {label}: {durationMs} ms
        </div>
      </div>
      <span className="w-16 shrink-0 text-right text-xs text-slate-500">{durationMs} ms</span>
    </div>
  )
}

function NotApplicableBar({ label }) {
  return (
    <div className="flex items-center gap-3">
      <span className="w-20 shrink-0 text-xs font-medium text-slate-600">{label}</span>
      <div className="h-5 flex-1 rounded border border-dashed border-slate-200" />
      <span className="w-16 shrink-0 text-right text-xs text-slate-400">n/a</span>
    </div>
  )
}

function AxisTicks({ maxDurationMs }) {
  const ticks = [0, 0.25, 0.5, 0.75, 1].map((fraction) => Math.round(maxDurationMs * fraction))

  return (
    <div className="ml-[5.75rem] mr-[4.75rem] flex justify-between text-[10px] text-slate-400">
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

      <div className="flex flex-col gap-4">
        <div>
          <p className="mb-2 text-xs font-medium text-slate-500">
            Connection probes (independent, one-off measurements)
          </p>
          <div className="flex flex-col gap-2">
            <WaterfallBar
              label="DNS"
              durationMs={dnsMs}
              colorClass="bg-indigo-400"
              maxDurationMs={maxDurationMs}
            />
            <WaterfallBar
              label="TCP"
              durationMs={tcpMs}
              colorClass="bg-indigo-500"
              maxDurationMs={maxDurationMs}
            />
            {tlsMs != null ? (
              <WaterfallBar
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
            Request timeline (sequential: TTFB then download)
          </p>
          <div className="flex flex-col gap-2">
            <WaterfallBar
              label="TTFB"
              durationMs={ttfbMs}
              colorClass="bg-emerald-500"
              maxDurationMs={maxDurationMs}
            />
            <WaterfallBar
              label="Download"
              durationMs={downloadMs}
              colorClass="bg-emerald-600"
              maxDurationMs={maxDurationMs}
            />
          </div>
        </div>

        <AxisTicks maxDurationMs={maxDurationMs} />
      </div>

      <p className="text-xs text-slate-500">
        DNS, TCP, and TLS are separate, dedicated-connection probes, not part of the real
        request&apos;s own connection - their bars share this chart&apos;s scale for
        comparison, but are not chained end-to-end with TTFB/download and must not be summed
        with them. Bars below the minimum visible width are floored for legibility; the label
        and tooltip always show the real measured duration. See docs/measurement.md.
      </p>
    </section>
  )
}

export default Waterfall
