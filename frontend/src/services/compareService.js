import { AnalysisError } from './analyzeService'

export async function compareUrls(urls) {
  const response = await fetch('/api/compare', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ urls }),
  })

  const body = await response.json()

  if (!response.ok) {
    throw new AnalysisError(body.message || 'Comparison failed', body.error)
  }

  return body.results
}
