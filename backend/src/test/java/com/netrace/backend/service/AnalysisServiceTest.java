package com.netrace.backend.service;

import com.netrace.backend.analyzer.AnalysisException;
import com.netrace.backend.analyzer.HttpAnalyzer;
import com.netrace.backend.dto.AnalyzeRequest;
import com.netrace.backend.dto.AnalyzeResponse;
import com.netrace.backend.validation.UrlValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalysisServiceTest {

    @Mock
    private UrlValidator urlValidator;

    @Mock
    private HttpAnalyzer httpAnalyzer;

    @Test
    void delegatesToTheAnalyzerWhenTheUrlIsValid() {
        String url = "https://example.com";
        AnalyzeResponse expected = new AnalyzeResponse(url, 200, 42L);
        when(urlValidator.isValid(url)).thenReturn(true);
        when(httpAnalyzer.analyze(url)).thenReturn(expected);
        AnalysisService service = new AnalysisService(urlValidator, httpAnalyzer);

        AnalyzeResponse actual = service.analyze(new AnalyzeRequest(url));

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void rejectsAnInvalidUrlWithoutCallingTheAnalyzer() {
        String url = "not a url";
        when(urlValidator.isValid(url)).thenReturn(false);
        AnalysisService service = new AnalysisService(urlValidator, httpAnalyzer);

        assertThatThrownBy(() -> service.analyze(new AnalyzeRequest(url)))
                .isInstanceOf(InvalidUrlException.class);

        verifyNoInteractions(httpAnalyzer);
    }

    @Test
    void propagatesAnalysisExceptionsFromTheAnalyzerUnchanged() {
        String url = "https://example.com";
        AnalysisException failure = new AnalysisException(
                AnalysisException.Reason.CONNECTION_FAILURE, "boom", new RuntimeException());
        when(urlValidator.isValid(url)).thenReturn(true);
        when(httpAnalyzer.analyze(url)).thenThrow(failure);
        AnalysisService service = new AnalysisService(urlValidator, httpAnalyzer);

        assertThatThrownBy(() -> service.analyze(new AnalyzeRequest(url)))
                .isSameAs(failure);
    }
}
