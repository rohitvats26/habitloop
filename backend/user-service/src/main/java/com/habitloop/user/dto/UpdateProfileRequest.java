package com.habitloop.user.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @Size(max = 80) @Pattern(regexp = "(?s).*\\S.*", message = "must not be blank") String displayName,
        @Size(max = 64) String timezone,
        @Size(max = 512) String avatarUrl
) {
    @AssertTrue(message = "timezone must be Asia/Kolkata")
    public boolean isTimezoneValid() {
        return TimezoneRules.isValid(timezone);
    }

    @AssertTrue(message = "provide at least one profile field to update")
    public boolean isUpdatePresent() {
        return displayName != null || timezone != null || avatarUrl != null;
    }
}
