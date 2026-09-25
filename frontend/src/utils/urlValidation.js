export function isValidHttpUrl(value) {
  try {
    const parsed = new URL(value)
    return parsed.protocol === 'http:' || parsed.protocol === 'https:'
  } catch {
    return false
  }
}

export function urlValidationMessage(trimmedUrl) {
  if (trimmedUrl.length === 0) {
    return 'Enter a URL to analyze.'
  }
  if (!isValidHttpUrl(trimmedUrl)) {
    return 'Enter a valid http or https URL.'
  }
  return null
}
