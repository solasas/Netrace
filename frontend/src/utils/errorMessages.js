// Maps the backend's coarse error code (and, where the code alone is
// ambiguous, a pattern in its own message) to a short, user-facing
// headline. The backend's original message is always shown alongside
// the headline as supporting detail - this never replaces or hides it,
// only adds a friendlier category label. Never derived from anything
// but real fields the backend actually sent.
const RULES = [
  { errorCode: 'VALIDATION_FAILED', headline: 'Invalid request' },
  { errorCode: 'INVALID_URL', headline: 'Invalid URL' },
  { errorCode: 'DNS_FAILURE', headline: 'DNS resolution failed' },
  { errorCode: 'CONNECTION_FAILURE', match: /refused/i, headline: 'Connection refused' },
  { errorCode: 'CONNECTION_FAILURE', match: /TLS handshake/i, headline: 'TLS handshake failed' },
  { errorCode: 'CONNECTION_FAILURE', headline: 'Connection failed' },
  { errorCode: 'TIMEOUT', match: /^Connection to/i, headline: 'Connection timed out' },
  { errorCode: 'TIMEOUT', match: /^Request to/i, headline: 'Request timed out' },
  { errorCode: 'TIMEOUT', headline: 'Timed out' },
  { errorCode: 'INVALID_RESPONSE', headline: 'Invalid response from server' },
  { errorCode: 'ANALYSIS_FAILED', headline: 'Something went wrong' },
]

export function friendlyHeadline(errorCode, message) {
  if (!errorCode) {
    return 'Could not reach the server'
  }
  const rule = RULES.find(
    (candidate) =>
      candidate.errorCode === errorCode && (!candidate.match || candidate.match.test(message ?? '')),
  )
  return rule ? rule.headline : 'Something went wrong'
}
