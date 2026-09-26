import SummaryField from './SummaryField'

function AnalysisSummary({ url, result }) {
  const dns = result.probes?.dns
  const resolvedIp = dns?.resolvedIpv4?.[0] ?? dns?.resolvedIpv6?.[0] ?? '—'

  return (
    <section className="flex flex-col gap-4 rounded-md border border-slate-200 bg-white p-6">
      <dl className="grid grid-cols-2 gap-4 sm:grid-cols-3">
        <SummaryField label="URL" value={url} />
        <SummaryField label="Final URL" value={result.url} />
        <SummaryField label="Status code" value={result.statusCode} />
        <SummaryField label="Total observed time" value={`${result.totalTimeMs} ms`} />
        <SummaryField label="Resolved IP" value={resolvedIp} />
        <SummaryField label="HTTP protocol" value={result.protocol} />
      </dl>
    </section>
  )
}

export default AnalysisSummary
