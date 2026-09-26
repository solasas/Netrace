import { useState } from 'react'
import AnalysisSummary from '../components/AnalysisSummary'
import Button from '../components/Button'
import DnsDetails from '../components/DnsDetails'
import ErrorMessage from '../components/ErrorMessage'
import HttpDetails from '../components/HttpDetails'
import LoadingState from '../components/LoadingState'
import TcpDetails from '../components/TcpDetails'
import TextInput from '../components/TextInput'
import TlsDetails from '../components/TlsDetails'
import Waterfall from '../components/Waterfall'
import { buildTimingPhases } from '../types/TimingPhase'
import { AnalysisError, analyzeUrl } from '../services/analyzeService'
import { friendlyHeadline } from '../utils/errorMessages'
import { urlValidationMessage } from '../utils/urlValidation'

const STATUS = {
  IDLE: 'idle',
  LOADING: 'loading',
  SUCCESS: 'success',
  ERROR: 'error',
}

const EXAMPLE_URL = 'https://example.com'

function AnalyzerPage() {
  const [url, setUrl] = useState('')
  const [touched, setTouched] = useState(false)
  const [status, setStatus] = useState(STATUS.IDLE)
  const [submittedUrl, setSubmittedUrl] = useState(null)
  const [result, setResult] = useState(null)
  const [error, setError] = useState(null)

  const isLoading = status === STATUS.LOADING
  const trimmedUrl = url.trim()
  const currentValidationMessage = touched ? urlValidationMessage(trimmedUrl) : null

  async function handleSubmit(e) {
    e.preventDefault()
    if (isLoading) {
      return
    }
    setTouched(true)

    const message = urlValidationMessage(trimmedUrl)
    if (message) {
      return
    }

    setStatus(STATUS.LOADING)
    setSubmittedUrl(trimmedUrl)
    setError(null)

    try {
      const data = await analyzeUrl(trimmedUrl)
      setResult(data)
      setStatus(STATUS.SUCCESS)
    } catch (err) {
      const errorCode = err instanceof AnalysisError ? err.errorCode : null
      setError({ headline: friendlyHeadline(errorCode, err.message), message: err.message })
      setStatus(STATUS.ERROR)
    }
  }

  function handleClear() {
    setUrl('')
    setTouched(false)
  }

  function handleUseExample() {
    setUrl(EXAMPLE_URL)
    setTouched(false)
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

      <div className="flex flex-col gap-2">
        <form className="flex flex-col gap-3 sm:flex-row" onSubmit={handleSubmit}>
          <TextInput
            type="url"
            placeholder="https://example.com"
            value={url}
            onChange={(e) => setUrl(e.target.value)}
            onBlur={() => setTouched(true)}
            onClear={handleClear}
            disabled={isLoading}
            invalid={Boolean(currentValidationMessage)}
            aria-invalid={Boolean(currentValidationMessage)}
          />
          <Button type="submit" disabled={isLoading}>
            {isLoading ? 'Analyzing…' : 'Analyze'}
          </Button>
        </form>

        <div className="flex items-center justify-between gap-3 text-xs">
          {currentValidationMessage ? (
            <p className="text-red-600">{currentValidationMessage}</p>
          ) : (
            <span />
          )}
          <button
            type="button"
            onClick={handleUseExample}
            className="shrink-0 text-indigo-600 hover:underline"
          >
            Try {EXAMPLE_URL}
          </button>
        </div>
      </div>

      {status === STATUS.LOADING && <LoadingState url={submittedUrl} />}
      {status === STATUS.ERROR && error && (
        <ErrorMessage headline={error.headline} message={error.message} />
      )}
      {status === STATUS.SUCCESS && result && (
        <>
          <AnalysisSummary url={submittedUrl} result={result} />
          <Waterfall phases={buildTimingPhases(result)} />
          <DnsDetails dns={result.probes?.dns} />
          {result.probes?.tcp && (
            <TcpDetails
              host={result.probes.tcp.host}
              port={result.probes.tcp.port}
              durationMs={result.probes.tcp.durationMs}
            />
          )}
          <TlsDetails tls={result.probes?.tls} />
          <HttpDetails
            statusCode={result.statusCode}
            protocol={result.protocol}
            ttfbMs={result.ttfbMs}
            downloadMs={result.downloadMs}
            contentType={result.contentType}
            contentLength={result.contentLength}
          />
        </>
      )}
    </main>
  )
}

export default AnalyzerPage
