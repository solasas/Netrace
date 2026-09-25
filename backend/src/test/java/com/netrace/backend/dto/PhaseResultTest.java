package com.netrace.backend.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PhaseResultTest {

    @Test
    void successFactoryPopulatesAllFieldsWithSuccessStatus() {
        PhaseResult<String> result = PhaseResult.success("DNS", 12L, "some metadata");

        assertThat(result.phase()).isEqualTo("DNS");
        assertThat(result.durationMs()).isEqualTo(12L);
        assertThat(result.status()).isEqualTo(PhaseResult.Status.SUCCESS);
        assertThat(result.metadata()).isEqualTo("some metadata");
    }

    @Test
    void failureFactoryPopulatesAllFieldsWithFailureStatus() {
        PhaseResult<String> result = PhaseResult.failure("TCP", 30L, "some metadata");

        assertThat(result.phase()).isEqualTo("TCP");
        assertThat(result.durationMs()).isEqualTo(30L);
        assertThat(result.status()).isEqualTo(PhaseResult.Status.FAILURE);
        assertThat(result.metadata()).isEqualTo("some metadata");
    }
}
