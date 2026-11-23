package com.devtaha.journalapp.rag;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AskRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void topKMustBeBetweenOneAndTenWhenGiven() {
        assertThat(validator.validate(new AskRequest("q", null))).isEmpty();
        assertThat(validator.validate(new AskRequest("q", 1))).isEmpty();
        assertThat(validator.validate(new AskRequest("q", 10))).isEmpty();
        assertThat(validator.validate(new AskRequest("q", 0))).hasSize(1);
        assertThat(validator.validate(new AskRequest("q", 11))).hasSize(1);
    }

    @Test
    void questionMustNotBeBlankOrHuge() {
        assertThat(validator.validate(new AskRequest(" ", 4))).hasSize(1);
        assertThat(validator.validate(new AskRequest("x".repeat(1001), 4))).hasSize(1);
    }
}
