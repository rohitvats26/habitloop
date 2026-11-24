package com.habitloop.common.error;

import java.time.OffsetDateTime;
import java.util.List;

/** Consistent error body returned by every service (see docs/05). */
public record ErrorResponse(
        OffsetDateTime timestamp,
        String path,
        int status,
        String code,
        String message,
        List<String> details
) {
    public static ErrorResponse of(String path, int status, String code, String message, List<String> details) {
        return new ErrorResponse(OffsetDateTime.now(), path, status, code, message,
                details == null ? List.of() : details);
    }
}
