function LoadingState({ url }) {
  return (
    <section className="flex items-start gap-3 rounded-md border border-slate-200 bg-white p-6 text-sm text-slate-600">
      <span
        className="mt-0.5 h-4 w-4 shrink-0 animate-spin rounded-full border-2 border-slate-300 border-t-indigo-600"
        role="status"
        aria-label="Analyzing"
      />
      <p>
        Analyzing <span className="font-medium text-slate-800">{url}</span> — running a real
        DNS lookup, TCP connect, TLS handshake (if HTTPS), and HTTP request against the target,
        so this can take a few seconds. The backend runs this as one request and doesn&apos;t
        report per-phase progress back to the browser, so this is a single in-progress
        indicator rather than a step-by-step tracker.
      </p>
    </section>
  )
}

export default LoadingState
