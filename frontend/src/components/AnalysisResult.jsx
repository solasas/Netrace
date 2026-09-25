function Metric({ label, value }) {
  return (
    <div>
      <dt className="text-xs uppercase tracking-wide text-slate-500">{label}</dt>
      <dd className="text-base font-medium text-slate-900">{value}</dd>
    </div>
  )
}

function AnalysisResult({ result }) {
  return (
    <section className="flex flex-col gap-4 rounded-md border border-slate-200 bg-white p-6">
      <div className="flex items-center justify-between gap-4">
        <h2 className="truncate text-lg font-semibold text-slate-900">{result.url}</h2>
        <span className="shrink-0 rounded-full bg-emerald-100 px-3 py-1 text-sm font-medium text-emerald-700">
          {result.statusCode}
        </span>
      </div>

      <dl className="grid grid-cols-2 gap-4 sm:grid-cols-3">
        <Metric label="Protocol" value={result.protocol} />
        <Metric label="TTFB" value={`${result.ttfbMs} ms`} />
        <Metric label="Download" value={`${result.downloadMs} ms`} />
        <Metric label="Total" value={`${result.totalTimeMs} ms`} />
        <Metric label="Content type" value={result.contentType ?? '—'} />
        <Metric
          label="Content length"
          value={result.contentLength != null ? `${result.contentLength} bytes` : '—'}
        />
      </dl>

      {result.bodyTruncated && (
        <p className="text-sm text-amber-700">
          Response body was truncated at the configured size limit.
        </p>
      )}
    </section>
  )
}

export default AnalysisResult
