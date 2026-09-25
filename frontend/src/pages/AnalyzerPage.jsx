import { useState } from 'react'
import AnalysisSummary from '../components/AnalysisSummary'
import Button from '../components/Button'
import ErrorMessage from '../components/ErrorMessage'
import TextInput from '../components/TextInput'
import { analyzeUrl } from '../services/analyzeService'

const STATUS = {
  IDLE: 'idle',
  LOADING: 'loading',
  SUCCESS: 'success',
  ERROR: 'error',
}

function AnalyzerPage() {
  const [url, setUrl] = useState('')
  const [status, setStatus] = useState(STATUS.IDLE)
  const [submittedUrl, setSubmittedUrl] = useState(null)
  const [result, setResult] = useState(null)
  const [error, setError] = useState(null)

  const isLoading = status === STATUS.LOADING

  async function handleSubmit(e) {
    e.preventDefault()
    const requestedUrl = url.trim()
    if (!requestedUrl || isLoading) {
      return
    }

    setStatus(STATUS.LOADING)
    setError(null)

    try {
      const data = await analyzeUrl(requestedUrl)
      setSubmittedUrl(requestedUrl)
      setResult(data)
      setStatus(STATUS.SUCCESS)
    } catch (err) {
      setError(err.message)
      setStatus(STATUS.ERROR)
    }
  }

  return (
    <main className="mx-auto flex min-h-screen max-w-2xl flex-col gap-8 px-4 py-16">
      <header className="flex flex-col gap-2">
        <h1 className="text-3xl font-semibold text-slate-900">Netrace</h1>
        <p className="text-slate-600">
          Enter a public HTTP or HTTPS URL to analyze its observed network
          performance — DNS, TCP, TLS, and request timing.
        </p>
      </header>

      <form className="flex flex-col gap-3 sm:flex-row" onSubmit={handleSubmit}>
        <TextInput
          type="url"
          placeholder="https://example.com"
          value={url}
          onChange={(e) => setUrl(e.target.value)}
          disabled={isLoading}
        />
        <Button type="submit" disabled={isLoading || !url.trim()}>
          {isLoading ? 'Analyzing…' : 'Analyze'}
        </Button>
      </form>

      {status === STATUS.ERROR && <ErrorMessage message={error} />}
      {status === STATUS.SUCCESS && result && (
        <AnalysisSummary url={submittedUrl} result={result} />
      )}
    </main>
  )
}

export default AnalyzerPage
