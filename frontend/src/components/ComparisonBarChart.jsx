const MIN_BAR_PERCENT = 2

function percentOf(value, max) {
  return max > 0 ? Math.max((value / max) * 100, MIN_BAR_PERCENT) : 0
}

function ComparisonBarChart({ results }) {
  const successfulTimes = results.filter((r) => r.success).map((r) => r.totalTimeMs)
  if (successfulTimes.length === 0) {
    return null
  }
  const maxTotalTimeMs = Math.max(...successfulTimes, 1)

  return (
    <section className="flex flex-col gap-3 rounded-md border border-slate-200 bg-white p-6">
      <h3 className="text-sm font-semibold uppercase tracking-wide text-slate-500">
        Observed Request Performance
      </h3>
      <p className="text-xs text-slate-500">
        Total time observed for one request to each URL, just now. This is a single observation,
        not a benchmark — network conditions vary between runs, so it does not establish that one
        URL is objectively faster than another.
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
              {result.success ? (
                <div
                  className="h-5 rounded bg-indigo-500"
                  style={{ width: `${percentOf(result.totalTimeMs, maxTotalTimeMs)}%` }}
                />
              ) : (
                <div className="h-5 rounded border border-dashed border-red-200" />
              )}
            </div>
            <span className="w-20 shrink-0 text-right text-xs text-slate-500">
              {result.success ? `${result.totalTimeMs} ms` : 'failed'}
            </span>
          </div>
        ))}
      </div>
    </section>
  )
}

export default ComparisonBarChart
