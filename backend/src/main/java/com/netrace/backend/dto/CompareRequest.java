package com.netrace.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CompareRequest(

        @NotEmpty(message = "at least 2 urls are required")
        @Size(min = 2, max = 5, message = "between 2 and 5 urls are required")
        List<@NotBlank(message = "urls must not be blank") String> urls

) {
}
