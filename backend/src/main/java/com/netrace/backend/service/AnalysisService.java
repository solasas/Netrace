package com.netrace.backend.service;

import com.netrace.backend.analyzer.HttpAnalyzer;
import com.netrace.backend.dto.AnalyzeRequest;
import com.netrace.backend.dto.AnalyzeResponse;
import com.netrace.backend.validation.UrlValidator;
import org.springframework.stereotype.Service;

/**
 * Coordinates a single analysis: validate the requested URL, run the
 * HTTP analyzer against it, and hand back the response DTO. Keeps this
 * orchestration out of the controller.
 */
@Service
public class AnalysisService {

    private final UrlValidator urlValidator;
    private final HttpAnalyzer httpAnalyzer;

    public AnalysisService(UrlValidator urlValidator, HttpAnalyzer httpAnalyzer) {
        this.urlValidator = urlValidator;
        this.httpAnalyzer = httpAnalyzer;
    }

    public AnalyzeResponse analyze(AnalyzeRequest request) {
        String url = request.url();
        if (!urlValidator.isValid(url)) {
            throw new InvalidUrlException(url);
        }
        return httpAnalyzer.analyze(url);
    }
}
