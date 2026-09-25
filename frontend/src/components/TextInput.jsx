function TextInput({ invalid, onClear, value, ...props }) {
  return (
    <div className="relative w-full">
      <input
        value={value}
        className={`w-full rounded-md border bg-white px-3 py-2 pr-9 text-slate-900 placeholder:text-slate-400 focus:outline-none focus:ring-1 ${
          invalid
            ? 'border-red-400 focus:border-red-500 focus:ring-red-500'
            : 'border-slate-300 focus:border-indigo-500 focus:ring-indigo-500'
        }`}
        {...props}
      />
      {value && onClear && (
        <button
          type="button"
          onClick={onClear}
          aria-label="Clear URL"
          className="absolute right-2 top-1/2 -translate-y-1/2 rounded px-1 text-sm leading-none text-slate-400 hover:text-slate-600"
        >
          ×
        </button>
      )}
    </div>
  )
}

export default TextInput
