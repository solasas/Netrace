package com.netrace.backend.controller;

import com.netrace.backend.dto.CompareRequest;
import com.netrace.backend.dto.CompareResponse;
import com.netrace.backend.service.ComparisonService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CompareController {

    private final ComparisonService comparisonService;

    public CompareController(ComparisonService comparisonService) {
        this.comparisonService = comparisonService;
    }

    @PostMapping("/api/compare")
    public CompareResponse compare(@Valid @RequestBody CompareRequest request) {
        return comparisonService.compare(request);
    }
}
