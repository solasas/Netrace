import { useState } from 'react'
import Button from '../components/Button'
import TextInput from '../components/TextInput'

function AnalyzerPage() {
  const [url, setUrl] = useState('')

  return (
    <main className="mx-auto flex min-h-screen max-w-2xl flex-col gap-8 px-4 py-16">
      <header className="flex flex-col gap-2">
        <h1 className="text-3xl font-semibold text-slate-900">Netrace</h1>
        <p className="text-slate-600">
          Enter a public HTTP or HTTPS URL to analyze its observed network
          performance — DNS, TCP, TLS, and request timing.
        </p>
      </header>

      <form className="flex flex-col gap-3 sm:flex-row">
        <TextInput
          type="url"
          placeholder="https://example.com"
          value={url}
          onChange={(e) => setUrl(e.target.value)}
        />
        <Button>Analyze</Button>
      </form>
    </main>
  )
}

export default AnalyzerPage
