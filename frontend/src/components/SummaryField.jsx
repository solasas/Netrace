function SummaryField({ label, value }) {
  return (
    <div>
      <dt className="text-xs uppercase tracking-wide text-slate-500">{label}</dt>
      <dd className="truncate text-base font-medium text-slate-900">{value}</dd>
    </div>
  )
}

export default SummaryField
