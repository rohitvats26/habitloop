package com.habitloop.activity.dto;

import com.habitloop.activity.entity.Frequency;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HabitRequestValidationTest {

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
    void createRejectsInvalidTargetAndCustomSchedule() {
        var invalidTarget = new CreateHabitRequest("Read", null, Frequency.DAILY, null, 0);
        var customWithoutDays = new CreateHabitRequest("Read", null, Frequency.CUSTOM, null, 1);
        var invalidDay = new CreateHabitRequest("Read", null, Frequency.CUSTOM,
                Map.of("daysOfWeek", List.of(0)), 1);

        assertThat(validator.validate(invalidTarget)).isNotEmpty();
        assertThat(validator.validate(customWithoutDays)).anySatisfy(violation ->
                assertThat(violation.getPropertyPath().toString()).isEqualTo("scheduleValid"));
        assertThat(validator.validate(invalidDay)).anySatisfy(violation ->
                assertThat(violation.getPropertyPath().toString()).isEqualTo("scheduleValid"));
        assertThat(validator.validate(new CreateHabitRequest("Read", null, Frequency.DAILY,
                Map.of("targetTime", "25:61"), 1))).anySatisfy(violation ->
                assertThat(violation.getPropertyPath().toString()).isEqualTo("scheduleValid"));
        assertThat(validator.validate(new CreateHabitRequest("Read", null, Frequency.DAILY,
                Map.of("unknown", true), 1))).anySatisfy(violation ->
                assertThat(violation.getPropertyPath().toString()).isEqualTo("scheduleValid"));
    }

    @Test
    void createAcceptsValidCustomSchedule() {
        var request = new CreateHabitRequest("Read", null, Frequency.CUSTOM,
                Map.of("daysOfWeek", List.of(1, 3, 5), "targetTime", "07:30"), 1);
        var weeklyTarget = new CreateHabitRequest("Read", null, Frequency.CUSTOM,
                Map.of("perWeek", 3), 1);

        assertThat(validator.validate(request)).isEmpty();
        assertThat(validator.validate(weeklyTarget)).isEmpty();
    }

    @Test
    void updateRejectsEmptyPatchBlankNameAndNonPositiveTarget() {
        assertThat(validator.validate(new UpdateHabitRequest(null, null, null, null, null, null)))
                .anySatisfy(violation ->
                        assertThat(violation.getPropertyPath().toString()).isEqualTo("updatePresent"));
        assertThat(validator.validate(new UpdateHabitRequest("  ", null, null, null, null, null)))
                .anySatisfy(violation ->
                        assertThat(violation.getPropertyPath().toString()).isEqualTo("name"));
        assertThat(validator.validate(new UpdateHabitRequest(null, null, null, null, 0, null)))
                .anySatisfy(violation ->
                        assertThat(violation.getPropertyPath().toString()).isEqualTo("targetPerPeriod"));
    }

    @Test
    void completionNoteCannotExceedStorageLimit() {
        assertThat(validator.validate(new CompletionRequest(null, "n".repeat(281))))
                .anySatisfy(violation ->
                        assertThat(violation.getPropertyPath().toString()).isEqualTo("note"));
    }
}
