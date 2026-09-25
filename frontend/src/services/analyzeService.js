export class AnalysisError extends Error {
  constructor(message, errorCode) {
    super(message)
    this.name = 'AnalysisError'
    this.errorCode = errorCode
  }
}

export async function analyzeUrl(url) {
  const response = await fetch('/api/analyze', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ url }),
  })

  const body = await response.json()

  if (!response.ok) {
    throw new AnalysisError(body.message || 'Analysis failed', body.error)
  }

  return body
}
