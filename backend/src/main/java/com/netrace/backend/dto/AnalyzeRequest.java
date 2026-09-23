package com.netrace.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record AnalyzeRequest(

        @NotBlank(message = "url is required")
        String url

) {
}
