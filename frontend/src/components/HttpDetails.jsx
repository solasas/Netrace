import StatusBadge from './StatusBadge'
import SummaryField from './SummaryField'

function statusVariant(statusCode) {
  return statusCode >= 200 && statusCode < 400 ? 'success' : 'error'
}

function HttpDetails({ statusCode, protocol, ttfbMs, downloadMs, contentType, contentLength }) {
  return (
    <section className="flex flex-col gap-4 rounded-md border border-slate-200 bg-white p-6">
      <div className="flex items-center justify-between gap-4">
        <h3 className="text-sm font-semibold uppercase tracking-wide text-slate-500">HTTP</h3>
        <StatusBadge label={statusCode} variant={statusVariant(statusCode)} />
      </div>

      <div className="grid grid-cols-2 gap-4 sm:grid-cols-3">
        <SummaryField label="Protocol" value={protocol} />
        <SummaryField label="TTFB" value={`${ttfbMs} ms`} />
        <SummaryField label="Download duration" value={`${downloadMs} ms`} />
        <SummaryField label="Content type" value={contentType ?? '—'} />
        <SummaryField
          label="Content length"
          value={contentLength != null ? `${contentLength} bytes` : '—'}
        />
      </div>
    </section>
  )
}

export default HttpDetails
