import StatusBadge from './StatusBadge'
import SummaryField from './SummaryField'

function TcpDetails({ host, port, durationMs, status = 'connected' }) {
  const isConnected = status === 'connected'

  return (
    <section className="flex flex-col gap-4 rounded-md border border-slate-200 bg-white p-6">
      <div className="flex items-center justify-between gap-4">
        <h3 className="text-sm font-semibold uppercase tracking-wide text-slate-500">TCP</h3>
        <StatusBadge
          label={isConnected ? 'Connected' : 'Failed'}
          variant={isConnected ? 'success' : 'error'}
        />
      </div>

      <div className="grid grid-cols-2 gap-4 sm:grid-cols-3">
        <SummaryField label="Destination host" value={host} />
        <SummaryField label="Port" value={port} />
        <SummaryField label="TCP duration" value={`${durationMs} ms`} />
      </div>
    </section>
  )
}

export default TcpDetails
