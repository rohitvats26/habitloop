package com.habitloop.challenge.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InviteRequestValidationTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        validatorFactory.close();
    }

    @Test
    void userIdMustBeAUuidRatherThanAnEmailAddress() {
        assertThat(validator.validate(new InviteRequest("a68f706b-3ba9-482b-949b-5ed0aa6728e3"))).isEmpty();

        assertThat(validator.validate(new InviteRequest("rohitvats26@gmail.com")))
                .anySatisfy(violation -> {
                    assertThat(violation.getPropertyPath().toString()).isEqualTo("userId");
                    assertThat(violation.getMessage()).contains("valid user ID (UUID)");
                });
    }
}
