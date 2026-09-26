const MIN_BAR_PERCENT = 2

function percentOf(value, max) {
  return max > 0 ? Math.max((value / max) * 100, MIN_BAR_PERCENT) : 0
}

function TcpComparisonChart({ results }) {
  const successfulTcpResults = results
    .filter((r) => r.tcp && r.tcp.success)
    .map((r) => ({ url: r.url, durationMs: r.tcp.durationMs }))

  if (successfulTcpResults.length === 0) {
    return null
  }

  const maxDurationMs = Math.max(...successfulTcpResults.map((r) => r.durationMs), 1)

  return (
    <section className="flex flex-col gap-3 rounded-md border border-slate-200 bg-white p-6">
      <h3 className="text-sm font-semibold uppercase tracking-wide text-slate-500">
        Observed TCP Connection Time
      </h3>
      <p className="text-xs text-slate-500">
        Fresh connection establishment to each destination. One measurement per run; network
        conditions vary, so repeated measurements would differ.
      </p>
      <div className="flex flex-col gap-2">
        {results.map((result) => (
          <div key={result.url} className="flex items-center gap-3">
            <span
              className="w-40 shrink-0 truncate text-xs font-medium text-slate-600"
              title={result.url}
            >
              {result.url}
            </span>
            <div className="h-5 flex-1 rounded bg-slate-100">
              {result.tcp && result.tcp.success ? (
                <div
                  className="h-5 rounded bg-amber-500"
                  style={{ width: `${percentOf(result.tcp.durationMs, maxDurationMs)}%` }}
                />
              ) : (
                <div className="h-5 rounded border border-dashed border-red-200" />
              )}
            </div>
            <span className="w-20 shrink-0 text-right text-xs text-slate-500">
              {result.tcp && result.tcp.success ? `${result.tcp.durationMs} ms` : 'failed'}
            </span>
          </div>
        ))}
      </div>
    </section>
  )
}

export default TcpComparisonChart
