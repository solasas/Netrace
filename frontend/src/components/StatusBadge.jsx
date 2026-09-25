const VARIANT_STYLES = {
  success: 'bg-emerald-100 text-emerald-700',
  error: 'bg-red-100 text-red-700',
}

function StatusBadge({ label, variant = 'success' }) {
  return (
    <span
      className={`shrink-0 rounded-full px-3 py-1 text-sm font-medium ${VARIANT_STYLES[variant]}`}
    >
      {label}
    </span>
  )
}

export default StatusBadge
