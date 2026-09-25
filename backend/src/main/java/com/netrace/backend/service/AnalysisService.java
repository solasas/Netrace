package com.netrace.backend.service;

import com.netrace.backend.analyzer.DnsAnalyzer;
import com.netrace.backend.analyzer.HttpAnalyzer;
import com.netrace.backend.dto.AnalyzeRequest;
import com.netrace.backend.dto.AnalyzeResponse;
import com.netrace.backend.dto.DnsMetadata;
import com.netrace.backend.dto.DnsResult;
import com.netrace.backend.dto.HttpResult;
import com.netrace.backend.dto.PhaseResult;
import com.netrace.backend.validation.UrlValidator;
import org.springframework.stereotype.Service;

/**
 * Coordinates a single analysis: validate the requested URL, resolve
 * it via DnsAnalyzer, then run HttpAnalyzer against it, and combine
 * both into the response DTO. Keeps this orchestration out of the
 * controller. DNS failures short-circuit before the HTTP phase runs.
 * DnsAnalyzer's PhaseResult is unwrapped back into the existing
 * DnsResult shape here, so AnalyzeResponse's JSON contract is unchanged.
 */
@Service
public class AnalysisService {

    private final UrlValidator urlValidator;
    private final DnsAnalyzer dnsAnalyzer;
    private final HttpAnalyzer httpAnalyzer;

    public AnalysisService(UrlValidator urlValidator, DnsAnalyzer dnsAnalyzer, HttpAnalyzer httpAnalyzer) {
        this.urlValidator = urlValidator;
        this.dnsAnalyzer = dnsAnalyzer;
        this.httpAnalyzer = httpAnalyzer;
    }

    public AnalyzeResponse analyze(AnalyzeRequest request) {
        String url = request.url();
        if (!urlValidator.isValid(url)) {
            throw new InvalidUrlException(url);
        }
        PhaseResult<DnsMetadata> dnsPhase = dnsAnalyzer.analyze(url);
        HttpResult http = httpAnalyzer.analyze(url);
        DnsResult dns = new DnsResult(
                dnsPhase.metadata().hostname(), dnsPhase.metadata().resolvedIps(), dnsPhase.durationMs());
        return new AnalyzeResponse(http.url(), dns, http.statusCode(), http.totalTimeMs());
    }
}
