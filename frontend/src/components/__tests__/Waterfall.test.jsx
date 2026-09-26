import { describe, it, expect, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import Waterfall from '../Waterfall'
import { buildTimingPhases, PHASE_NAMES, PHASE_STATUS } from '../../types/TimingPhase'

/**
 * Test suite for single-URL Waterfall component.
 * Tests rendering, phase display, interactivity, and edge cases.
 */

describe('Waterfall Component', () => {
  const mockResult = {
    url: 'https://example.com',
    statusCode: 200,
    protocol: 'HTTP/2',
    ttfbMs: 121,
    downloadMs: 38,
    responseSizeBytes: 48321,
    bodyTruncated: false,
    probes: {
      dns: {
        hostname: 'example.com',
        resolvedIpv4: ['93.184.216.34'],
        resolvedIpv6: [],
        durationMs: 18,
        success: true,
      },
      tcp: {
        host: '93.184.216.34',
        port: 443,
        durationMs: 27,
        success: true,
        failureReason: null,
      },
      tls: {
        tlsVersion: 'TLSv1.3',
        cipherSuite: 'TLS_AES_256_GCM_SHA384',
        certificateSubject: 'CN=example.com',
        certificateIssuer: 'CN=Example CA',
        durationMs: 43,
      },
    },
  }

  it('renders null when phases array is empty', () => {
    const { container } = render(<Waterfall phases={[]} />)
    expect(container.firstChild).toBeNull()
  })

  it('renders waterfall section when phases are provided', () => {
    const phases = buildTimingPhases(mockResult)
    render(<Waterfall phases={phases} />)
    expect(screen.getByText(/Waterfall/i)).toBeInTheDocument()
  })

  it('renders DNS phase successfully', () => {
    const phases = buildTimingPhases(mockResult)
    render(<Waterfall phases={phases} />)
    expect(screen.getByText('DNS')).toBeInTheDocument()
  })

  it('renders TCP phase successfully', () => {
    const phases = buildTimingPhases(mockResult)
    render(<Waterfall phases={phases} />)
    expect(screen.getByText('TCP')).toBeInTheDocument()
  })

  it('renders TLS phase for HTTPS URL', () => {
    const phases = buildTimingPhases(mockResult)
    render(<Waterfall phases={phases} />)
    expect(screen.getByText('TLS')).toBeInTheDocument()
  })

  it('renders TTFB phase', () => {
    const phases = buildTimingPhases(mockResult)
    render(<Waterfall phases={phases} />)
    expect(screen.getByText('TTFB')).toBeInTheDocument()
  })

  it('renders DOWNLOAD phase', () => {
    const phases = buildTimingPhases(mockResult)
    render(<Waterfall phases={phases} />)
    expect(screen.getByText('DOWNLOAD')).toBeInTheDocument()
  })

  it('displays correct durations for each phase', () => {
    const phases = buildTimingPhases(mockResult)
    render(<Waterfall phases={phases} />)
    expect(screen.getByText(/18 ms/)).toBeInTheDocument() // DNS
    expect(screen.getByText(/27 ms/)).toBeInTheDocument() // TCP
    expect(screen.getByText(/43 ms/)).toBeInTheDocument() // TLS
    expect(screen.getByText(/121 ms/)).toBeInTheDocument() // TTFB
    expect(screen.getByText(/38 ms/)).toBeInTheDocument() // Download
  })

  it('renders TLS as not applicable for HTTP URL', () => {
    const httpResult = { ...mockResult, probes: { ...mockResult.probes, tls: null } }
    const phases = buildTimingPhases(httpResult)
    render(<Waterfall phases={phases} />)
    expect(screen.getByText('n/a')).toBeInTheDocument()
  })

  it('handles failed phase rendering', () => {
    const phases = buildTimingPhases(mockResult)
    phases[0] = {
      ...phases[0],
      status: PHASE_STATUS.FAILED,
      error: 'DNS resolution timeout',
    }
    render(<Waterfall phases={phases} />)
    expect(screen.getByText('failed')).toBeInTheDocument()
  })

  it('allows phase selection via click', async () => {
    const user = userEvent.setup()
    const phases = buildTimingPhases(mockResult)
    render(<Waterfall phases={phases} />)

    const dnsBar = screen.getByText('DNS').parentElement.querySelector('div[role="img"]')
    await user.click(dnsBar)

    // Check that detail panel appears (should contain DNS details)
    expect(screen.getByText(/DNS Details/)).toBeInTheDocument()
  })

  it('displays phase metadata in detail panel', async () => {
    const user = userEvent.setup()
    const phases = buildTimingPhases(mockResult)
    render(<Waterfall phases={phases} />)

    const dnsBar = screen.getByText('DNS').parentElement.querySelector('div[role="img"]')
    await user.click(dnsBar)

    expect(screen.getByText('example.com')).toBeInTheDocument()
    expect(screen.getByText('93.184.216.34')).toBeInTheDocument()
  })

  it('displays TLS details when TLS phase is clicked', async () => {
    const user = userEvent.setup()
    const phases = buildTimingPhases(mockResult)
    render(<Waterfall phases={phases} />)

    const tlsBar = screen.getByText('TLS').parentElement.querySelector('div[role="img"]')
    await user.click(tlsBar)

    expect(screen.getByText(/TLS Version/)).toBeInTheDocument()
    expect(screen.getByText('TLSv1.3')).toBeInTheDocument()
    expect(screen.getByText(/Cipher Suite/)).toBeInTheDocument()
  })

  it('shows measurement description in detail panel', async () => {
    const user = userEvent.setup()
    const phases = buildTimingPhases(mockResult)
    render(<Waterfall phases={phases} />)

    const ttfbBar = screen.getAllByRole('img').find((el) =>
      el.getAttribute('aria-label')?.includes('TTFB')
    )
    await user.click(ttfbBar)

    expect(
      screen.getByText(/Observed time until response data became available/i)
    ).toBeInTheDocument()
  })

  it('handles large timing values correctly', () => {
    const largeResult = {
      ...mockResult,
      ttfbMs: 5000,
      downloadMs: 10000,
      probes: {
        ...mockResult.probes,
        dns: { ...mockResult.probes.dns, durationMs: 2000 },
      },
    }
    const phases = buildTimingPhases(largeResult)
    render(<Waterfall phases={phases} />)
    expect(screen.getByText(/5000 ms/)).toBeInTheDocument()
    expect(screen.getByText(/10000 ms/)).toBeInTheDocument()
  })

  it('renders request timeline section separately from probes', () => {
    const phases = buildTimingPhases(mockResult)
    render(<Waterfall phases={phases} />)

    expect(screen.getByText(/Connection probes/i)).toBeInTheDocument()
    expect(screen.getByText(/Request timeline/i)).toBeInTheDocument()
  })
})
