import SummaryField from './SummaryField'

function classifyIp(ip) {
  return ip.includes(':') ? 'IPv6' : 'IPv4'
}

function DnsDetails({ dns, error }) {
  if (error) {
    return (
      <section className="rounded-md border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
        {error}
      </section>
    )
  }

  if (!dns) {
    return (
      <section className="rounded-md border border-slate-200 bg-white px-4 py-3 text-sm text-slate-500">
        No DNS data available.
      </section>
    )
  }

  const resolvedIps = dns.resolvedIps ?? []

  return (
    <section className="flex flex-col gap-4 rounded-md border border-slate-200 bg-white p-6">
      <h3 className="text-sm font-semibold uppercase tracking-wide text-slate-500">DNS</h3>

      <div className="grid grid-cols-2 gap-4 sm:grid-cols-3">
        <SummaryField label="Hostname" value={dns.hostname} />
        <SummaryField label="DNS duration" value={`${dns.durationMs} ms`} />
      </div>

      <div>
        <dt className="text-xs uppercase tracking-wide text-slate-500">Resolved IP addresses</dt>
        {resolvedIps.length === 0 ? (
          <p className="mt-1 text-sm text-slate-500">No addresses resolved.</p>
        ) : (
          <ul className="mt-2 flex flex-col gap-1">
            {resolvedIps.map((ip) => (
              <li key={ip} className="flex items-center gap-2 text-base font-medium text-slate-900">
                <span className="rounded-full bg-slate-100 px-2 py-0.5 text-xs font-semibold text-slate-600">
                  {classifyIp(ip)}
                </span>
                {ip}
              </li>
            ))}
          </ul>
        )}
      </div>
    </section>
  )
}

export default DnsDetails
