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
}
