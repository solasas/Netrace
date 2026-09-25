import { useState } from 'react'
import AnalyzerPage from './pages/AnalyzerPage'
import ComparePage from './pages/ComparePage'

const MODE = {
  SINGLE: 'single',
  COMPARE: 'compare',
}

function ModeButton({ active, onClick, children }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`rounded-md px-3 py-1.5 text-sm font-medium transition-colors ${
        active ? 'bg-indigo-600 text-white' : 'text-slate-600 hover:bg-slate-100'
      }`}
    >
      {children}
    </button>
  )
}

function App() {
  const [mode, setMode] = useState(MODE.SINGLE)

  return (
    <div className="min-h-screen">
      <nav className="mx-auto flex max-w-2xl gap-2 px-4 pt-8">
        <ModeButton active={mode === MODE.SINGLE} onClick={() => setMode(MODE.SINGLE)}>
          Analyze
        </ModeButton>
        <ModeButton active={mode === MODE.COMPARE} onClick={() => setMode(MODE.COMPARE)}>
          Compare
        </ModeButton>
      </nav>
      {mode === MODE.SINGLE ? <AnalyzerPage /> : <ComparePage />}
    </div>
  )
}

export default App
