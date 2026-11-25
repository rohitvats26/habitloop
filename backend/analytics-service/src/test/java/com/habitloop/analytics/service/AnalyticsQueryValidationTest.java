package com.habitloop.analytics.service;

import com.habitloop.common.exception.InvalidRequestException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnalyticsQueryValidationTest {

    private final AnalyticsQueryService service = new AnalyticsQueryService(null);

    @Test
    void weeklyRangeMustBeBetweenOneAndFiftyTwo() {
        assertThatThrownBy(() -> service.weekly(0))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("weeks must be between 1 and 52");
        assertThatThrownBy(() -> service.weekly(53))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("weeks must be between 1 and 52");
    }

    @Test
    void dailyRangeMustNotBeReversed() {
        assertThatThrownBy(() -> service.daily(LocalDate.of(2026, 12, 2), LocalDate.of(2026, 12, 1)))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("from must not be after to");
    }
}
