package com.habitloop.common.time;

import java.time.ZoneId;

public final class TimeZones {

    public static final String INDIA_TIMEZONE = "Asia/Kolkata";
    public static final ZoneId INDIA = ZoneId.of(INDIA_TIMEZONE);

    private TimeZones() {
    }
}
