package com.habitloop.challenge.dto;

import com.habitloop.challenge.entity.Visibility;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class CreateChallengeRequestValidationTest {

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
    void createRejectsEndBeforeStart() {
        var request = request(new BigDecimal("30"), LocalDate.of(2026, 12, 31),
                LocalDate.of(2026, 12, 1));

        assertThat(validator.validate(request)).anySatisfy(violation ->
                assertThat(violation.getPropertyPath().toString()).isEqualTo("dateRangeValid"));
    }

    @Test
    void createRejectsTargetOutsideDatabasePrecision() {
        var oversized = request(new BigDecimal("10000000000"), LocalDate.of(2026, 12, 1),
                LocalDate.of(2026, 12, 31));
        var excessScale = request(new BigDecimal("1.234"), LocalDate.of(2026, 12, 1),
                LocalDate.of(2026, 12, 31));

        assertThat(validator.validate(oversized)).anySatisfy(violation ->
                assertThat(violation.getPropertyPath().toString()).isEqualTo("target"));
        assertThat(validator.validate(excessScale)).anySatisfy(violation ->
                assertThat(violation.getPropertyPath().toString()).isEqualTo("target"));
    }

    private CreateChallengeRequest request(BigDecimal target, LocalDate startsOn, LocalDate endsOn) {
        return new CreateChallengeRequest("30-day movement", null, Visibility.PUBLIC,
                "DAYS_COMPLETED", target, startsOn, endsOn);
    }
}
