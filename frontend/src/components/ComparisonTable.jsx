import StatusBadge from './StatusBadge'

function ComparisonTable({ results }) {
  return (
    <section className="overflow-hidden rounded-md border border-slate-200 bg-white">
      <table className="w-full text-left text-sm">
        <thead className="bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
          <tr>
            <th className="px-4 py-3 font-medium">URL</th>
            <th className="px-4 py-3 font-medium">DNS</th>
            <th className="px-4 py-3 font-medium">TCP</th>
            <th className="px-4 py-3 font-medium">Total</th>
            <th className="px-4 py-3 font-medium">Status</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {results.map((result) => (
            <tr key={result.url}>
              <td className="max-w-xs break-all px-4 py-3 font-medium text-slate-900">
                {result.url}
              </td>
              <td className="px-4 py-3 text-slate-700">
                {result.dns ? (
                  result.dns.success ? (
                    `${result.dns.durationMs} ms`
                  ) : (
                    <span className="text-red-600">Failed</span>
                  )
                ) : (
                  <span className="text-slate-400">—</span>
                )}
              </td>
              <td className="px-4 py-3 text-slate-700">
                {result.tcp ? (
                  result.tcp.success ? (
                    `${result.tcp.durationMs} ms`
                  ) : (
                    <span className="text-red-600">Failed</span>
                  )
                ) : (
                  <span className="text-slate-400">—</span>
                )}
              </td>
              <td className="px-4 py-3 text-slate-700">
                {result.success ? (
                  `${result.totalTimeMs} ms`
                ) : (
                  <span className="text-red-600">{result.error}</span>
                )}
              </td>
              <td className="px-4 py-3">
                {result.success ? (
                  <StatusBadge
                    label={result.statusCode}
                    variant={result.statusCode >= 200 && result.statusCode < 400 ? 'success' : 'error'}
                  />
                ) : (
                  <StatusBadge label="Failed" variant="error" />
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  )
}

export default ComparisonTable
