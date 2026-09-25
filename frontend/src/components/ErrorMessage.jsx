function ErrorMessage({ headline, message }) {
  return (
    <div className="rounded-md border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
      <p className="font-semibold">{headline}</p>
      {message && message !== headline && <p className="mt-1 text-red-600">{message}</p>}
    </div>
  )
}

export default ErrorMessage
