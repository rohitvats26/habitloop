package com.habitloop.user.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TimezoneRequestValidationTest {

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
    void registrationAcceptsIndiaTimezoneAndRejectsOtherTimezones() {
        var india = new RegisterRequest("ria@example.com", "Password123!", "Ria",
                "Asia/Kolkata", "Acme", null);
        var other = new RegisterRequest("ria@example.com", "Password123!", "Ria",
                "America/New_York", "Acme", null);

        assertThat(validator.validate(india)).isEmpty();
        assertTimezoneViolation(validator.validate(other));
    }

    @Test
    void profileUpdateAcceptsIndiaTimezoneAndRejectsOtherTimezones() {
        assertThat(validator.validate(new UpdateProfileRequest("Ria", null, null))).isEmpty();
        assertThat(validator.validate(new UpdateProfileRequest("Ria", "Asia/Kolkata", null))).isEmpty();
        assertTimezoneViolation(validator.validate(new UpdateProfileRequest("Ria", "America/New_York", null)));
    }

    @Test
    void profileUpdateMustContainAtLeastOneField() {
        assertThat(validator.validate(new UpdateProfileRequest(null, null, null)))
                .anySatisfy(violation -> assertThat(violation.getPropertyPath().toString())
                        .isEqualTo("updatePresent"));
    }

    @Test
    void registrationRequiresExactlyOneTenantSelection() {
        var noTenant = new RegisterRequest("ria@example.com", "Password123!", "Ria",
                "Asia/Kolkata", null, null);
        var both = new RegisterRequest("ria@example.com", "Password123!", "Ria",
                "Asia/Kolkata", "Acme", "ACME2026");

        assertTenantSelectionViolation(validator.validate(noTenant));
        assertTenantSelectionViolation(validator.validate(both));
    }

    @Test
    void registrationRejectsMalformedEmailShortPasswordAndBlankName() {
        var request = new RegisterRequest("not-an-email", "short", "  ",
                "Asia/Kolkata", "Acme", null);
        var violations = validator.validate(request);

        assertThat(violations).anySatisfy(violation ->
                assertThat(violation.getPropertyPath().toString()).isEqualTo("email"));
        assertThat(violations).anySatisfy(violation ->
                assertThat(violation.getPropertyPath().toString()).isEqualTo("password"));
        assertThat(violations).anySatisfy(violation ->
                assertThat(violation.getPropertyPath().toString()).isEqualTo("displayName"));
    }

    @Test
    void loginRejectsValuesThatExceedStorageLimits() {
        var oversizedPassword = new LoginRequest("ria@example.com", "x".repeat(73), null);
        var oversizedTenantCode = new LoginRequest("ria@example.com", "Password123!", "A".repeat(17));

        assertThat(validator.validate(oversizedPassword)).anySatisfy(violation ->
                assertThat(violation.getPropertyPath().toString()).isEqualTo("password"));
        assertThat(validator.validate(oversizedTenantCode)).anySatisfy(violation ->
                assertThat(violation.getPropertyPath().toString()).isEqualTo("tenantCode"));
    }

    @Test
    void profileUpdateRejectsBlankDisplayName() {
        assertThat(validator.validate(new UpdateProfileRequest("  ", null, null)))
                .anySatisfy(violation ->
                        assertThat(violation.getPropertyPath().toString()).isEqualTo("displayName"));
    }

    private static <T> void assertTimezoneViolation(
            java.util.Set<jakarta.validation.ConstraintViolation<T>> violations) {
        assertThat(violations)
                .anySatisfy(violation -> assertThat(violation.getPropertyPath().toString())
                        .isEqualTo("timezoneValid"));
    }

    private static <T> void assertTenantSelectionViolation(
            java.util.Set<jakarta.validation.ConstraintViolation<T>> violations) {
        assertThat(violations)
                .anySatisfy(violation -> assertThat(violation.getPropertyPath().toString())
                        .isEqualTo("tenantSelectionValid"));
    }
}
