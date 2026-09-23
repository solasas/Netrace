package com.netrace.backend.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AnalyzeRequestTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @Test
    void acceptsANonBlankUrl() {
        Set<ConstraintViolation<AnalyzeRequest>> violations =
                validator.validate(new AnalyzeRequest("https://example.com"));

        assertThat(violations).isEmpty();
    }

    @Test
    void rejectsANullUrl() {
        Set<ConstraintViolation<AnalyzeRequest>> violations =
                validator.validate(new AnalyzeRequest(null));

        assertThat(violations)
                .extracting(ConstraintViolation::getPropertyPath)
                .extracting(Object::toString)
                .containsExactly("url");
    }

    @Test
    void rejectsAnEmptyUrl() {
        Set<ConstraintViolation<AnalyzeRequest>> violations =
                validator.validate(new AnalyzeRequest(""));

        assertThat(violations)
                .extracting(ConstraintViolation::getPropertyPath)
                .extracting(Object::toString)
                .containsExactly("url");
    }

    @Test
    void rejectsAWhitespaceOnlyUrl() {
        Set<ConstraintViolation<AnalyzeRequest>> violations =
                validator.validate(new AnalyzeRequest("   "));

        assertThat(violations)
                .extracting(ConstraintViolation::getPropertyPath)
                .extracting(Object::toString)
                .containsExactly("url");
    }
}
