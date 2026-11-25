package com.habitloop.user.dto;

import com.habitloop.common.time.TimeZones;

final class TimezoneRules {

    private TimezoneRules() {
    }

    static boolean isValid(String timezone) {
        return timezone == null || timezone.isBlank()
                || TimeZones.INDIA_TIMEZONE.equals(timezone.trim());
    }
}
