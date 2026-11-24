package com.habitloop.activity.dto;

import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** {@code date} defaults to today (in the request's handling) when omitted. */
public record CompletionRequest(
        LocalDate date,
        @Size(max = 280) String note
) {
}
