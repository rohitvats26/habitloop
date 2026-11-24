package com.habitloop.analytics.store;

import java.time.LocalDate;

/** Builds the single-table PK/SK/GSI key strings (docs/04 §5). */
public final class AnalyticsKeys {

    private AnalyticsKeys() {
    }

    public static String pk(String tenantId, String userId) {
        return "TENANT#" + tenantId + "#USER#" + userId;
    }

    public static String skDaily(LocalDate date) {
        return "STAT#DAILY#" + date;
    }

    public static String skWeekly(String isoWeek) {
        return "STAT#WEEKLY#" + isoWeek;
    }

    public static final String SK_PATTERN = "PATTERN#SUMMARY";

    public static final String SK_DAILY_PREFIX = "STAT#DAILY#";

    public static final String SK_WEEKLY_PREFIX = "STAT#WEEKLY#";

    public static String skProcessed(String activityLogId) {
        return "PROCESSED#" + activityLogId;
    }

    public static String gsi1pk(String tenantId, LocalDate date) {
        return "TENANT#" + tenantId + "#DATE#" + date;
    }

    public static String gsi1sk(String userId) {
        return "USER#" + userId;
    }
}
