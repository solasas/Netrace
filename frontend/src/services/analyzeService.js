export async function analyzeUrl(url) {
  const response = await fetch('/api/analyze', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ url }),
  })

  const body = await response.json()

  if (!response.ok) {
    throw new Error(body.message || 'Analysis failed')
  }

  return body
}
