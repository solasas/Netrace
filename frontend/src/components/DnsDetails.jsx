import SummaryField from './SummaryField'

function AddressList({ label, addresses }) {
  return (
    <div>
      <dt className="text-xs uppercase tracking-wide text-slate-500">{label}</dt>
      {addresses.length === 0 ? (
        <p className="mt-1 text-sm text-slate-500">None.</p>
      ) : (
        <ul className="mt-1 flex flex-col gap-0.5">
          {addresses.map((ip) => (
            <li key={ip} className="text-base font-medium text-slate-900">
              {ip}
            </li>
          ))}
        </ul>
      )}
    </div>
  )
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

  const resolvedIpv4 = dns.resolvedIpv4 ?? []
  const resolvedIpv6 = dns.resolvedIpv6 ?? []

  return (
    <section className="flex flex-col gap-4 rounded-md border border-slate-200 bg-white p-6">
      <h3 className="text-sm font-semibold uppercase tracking-wide text-slate-500">DNS</h3>

      <div className="grid grid-cols-2 gap-4 sm:grid-cols-3">
        <SummaryField label="Hostname" value={dns.hostname} />
        <SummaryField label="Observed resolution" value={`${dns.durationMs} ms`} />
        <SummaryField label="Resolution status" value={dns.success ? 'Success' : 'Failed'} />
      </div>

      <div className="grid grid-cols-2 gap-4 sm:grid-cols-3">
        <AddressList label="IPv4" addresses={resolvedIpv4} />
        <AddressList label="IPv6" addresses={resolvedIpv6} />
      </div>

      <p className="text-xs text-slate-500">
        This duration can reflect a cached result rather than a fresh query - see
        docs/measurement.md for why.
      </p>
    </section>
  )
}

export default DnsDetails
