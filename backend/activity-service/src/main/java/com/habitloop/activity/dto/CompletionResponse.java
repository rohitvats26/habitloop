package com.habitloop.activity.dto;

import java.time.LocalDate;

public record CompletionResponse(
        String completionId,
        LocalDate date,
        StreakDto streak
) {
}
