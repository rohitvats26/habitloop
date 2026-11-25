package com.habitloop.activity.service;

import com.habitloop.common.exception.InvalidRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ActivityQueryValidationTest {

    @Test
    void rejectsReverseDateRangeBeforeQuerying() {
        var service = new ActivityQueryService(null, null);

        assertThatThrownBy(() -> service.search(null,
                OffsetDateTime.parse("2026-12-02T00:00:00Z"),
                OffsetDateTime.parse("2026-12-01T00:00:00Z"),
                PageRequest.of(0, 20)))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("from must not be after to");
    }

    @Test
    void rejectsUnsupportedActivityTypeLength() {
        var service = new ActivityQueryService(null, null);

        assertThatThrownBy(() -> service.search("x".repeat(41), null, null, PageRequest.of(0, 20)))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("type must not exceed 40 characters");
    }
}
