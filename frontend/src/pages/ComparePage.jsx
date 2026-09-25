import { useState } from 'react'
import Button from '../components/Button'
import ComparisonBarChart from '../components/ComparisonBarChart'
import ComparisonTable from '../components/ComparisonTable'
import ErrorMessage from '../components/ErrorMessage'
import TextInput from '../components/TextInput'
import { AnalysisError } from '../services/analyzeService'
import { compareUrls } from '../services/compareService'
import { friendlyHeadline } from '../utils/errorMessages'
import { urlValidationMessage } from '../utils/urlValidation'

const STATUS = {
  IDLE: 'idle',
  LOADING: 'loading',
  SUCCESS: 'success',
  ERROR: 'error',
}

const MIN_URLS = 2
const MAX_URLS = 5

function ComparePage() {
  const [urls, setUrls] = useState([])
  const [urlInput, setUrlInput] = useState('')
  const [touched, setTouched] = useState(false)
  const [status, setStatus] = useState(STATUS.IDLE)
  const [results, setResults] = useState(null)
  const [error, setError] = useState(null)

  const isLoading = status === STATUS.LOADING
  const trimmedInput = urlInput.trim()
  const atMaxUrls = urls.length >= MAX_URLS
  const inputValidationMessage = touched && !atMaxUrls ? urlValidationMessage(trimmedInput) : null

  function handleAddUrl(e) {
    e.preventDefault()
    if (atMaxUrls || isLoading) {
      return
    }
    setTouched(true)
    if (urlValidationMessage(trimmedInput)) {
      return
    }
    setUrls((current) => [...current, trimmedInput])
    setUrlInput('')
    setTouched(false)
  }

  function handleRemoveUrl(urlToRemove) {
    setUrls((current) => current.filter((u) => u !== urlToRemove))
  }

  async function handleCompare() {
    if (urls.length < MIN_URLS || isLoading) {
      return
    }

    setStatus(STATUS.LOADING)
    setError(null)
    setTouched(false)

    try {
      const data = await compareUrls(urls)
      setResults(data)
      setStatus(STATUS.SUCCESS)
    } catch (err) {
      const errorCode = err instanceof AnalysisError ? err.errorCode : null
      setError({ headline: friendlyHeadline(errorCode, err.message), message: err.message })
      setStatus(STATUS.ERROR)
    }
  }

  return (
    <main className="mx-auto flex min-h-screen max-w-2xl flex-col gap-8 px-4 py-16">
      <header className="flex flex-col gap-2">
        <h1 className="text-3xl font-semibold text-slate-900">Compare URLs</h1>
        <p className="text-slate-600">
          Add 2 to 5 public HTTP or HTTPS URLs to compare their observed request performance.
        </p>
      </header>

      <div className="flex flex-col gap-2">
        <form className="flex flex-col gap-3 sm:flex-row" onSubmit={handleAddUrl}>
          <TextInput
            type="url"
            placeholder="https://example.com"
            value={urlInput}
            onChange={(e) => setUrlInput(e.target.value)}
            onBlur={() => setTouched(true)}
            onClear={() => setUrlInput('')}
            disabled={isLoading || atMaxUrls}
            invalid={Boolean(inputValidationMessage)}
            aria-invalid={Boolean(inputValidationMessage)}
          />
          <Button type="submit" disabled={isLoading || atMaxUrls}>
            Add URL
          </Button>
        </form>
        {atMaxUrls ? (
          <p className="text-xs text-slate-500">Maximum of {MAX_URLS} URLs reached.</p>
        ) : (
          inputValidationMessage && <p className="text-xs text-red-600">{inputValidationMessage}</p>
        )}
      </div>

      {urls.length > 0 && (
        <ul className="flex flex-col gap-2">
          {urls.map((u) => (
            <li
              key={u}
              className="flex items-center justify-between gap-3 rounded-md border border-slate-200 bg-white px-3 py-2 text-sm text-slate-800"
            >
              <span className="break-all">{u}</span>
              <button
                type="button"
                onClick={() => handleRemoveUrl(u)}
                disabled={isLoading}
                aria-label={`Remove ${u}`}
                className="shrink-0 text-slate-400 hover:text-slate-600"
              >
                Remove
              </button>
            </li>
          ))}
        </ul>
      )}

      <div className="flex flex-col gap-2">
        <Button
          type="button"
          onClick={handleCompare}
          disabled={isLoading || urls.length < MIN_URLS}
        >
          {isLoading ? 'Comparing…' : 'Compare'}
        </Button>
        {urls.length > 0 && urls.length < MIN_URLS && (
          <p className="text-xs text-slate-500">Add at least {MIN_URLS} URLs to compare.</p>
        )}
      </div>

      {status === STATUS.LOADING && (
        <section className="flex items-center gap-3 rounded-md border border-slate-200 bg-white p-6 text-sm text-slate-600">
          <span
            className="h-4 w-4 shrink-0 animate-spin rounded-full border-2 border-slate-300 border-t-indigo-600"
            role="status"
            aria-label="Comparing"
          />
          <span>Comparing {urls.length} URLs — each runs a real HTTP request.</span>
        </section>
      )}

      {status === STATUS.ERROR && error && (
        <ErrorMessage headline={error.headline} message={error.message} />
      )}

      {status === STATUS.SUCCESS && results && (
        <>
          <ComparisonTable results={results} />
          <ComparisonBarChart results={results} />
        </>
      )}
    </main>
  )
}

export default ComparePage
