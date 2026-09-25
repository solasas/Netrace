import SummaryField from './SummaryField'

function TlsDetails({ tls }) {
  if (!tls) {
    return (
      <section className="rounded-md border border-slate-200 bg-white px-4 py-3 text-sm text-slate-500">
        TLS not applicable
      </section>
    )
  }

  return (
    <section className="flex flex-col gap-4 rounded-md border border-slate-200 bg-white p-6">
      <h3 className="text-sm font-semibold uppercase tracking-wide text-slate-500">TLS</h3>

      <div className="grid grid-cols-2 gap-4 sm:grid-cols-3">
        <SummaryField label="TLS version" value={tls.tlsVersion} />
        <SummaryField label="Cipher suite" value={tls.cipherSuite} />
        <SummaryField label="TLS duration" value={`${tls.durationMs} ms`} />
        <SummaryField label="Certificate subject" value={tls.certificateSubject} />
        <SummaryField label="Certificate issuer" value={tls.certificateIssuer} />
      </div>
    </section>
  )
}

export default TlsDetails
