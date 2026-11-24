package com.habitloop.integration.dto;

public record WebhookResponse(
        String status,
        String rawEventId,
        boolean deduplicated
) {
    public static WebhookResponse accepted(String rawEventId) {
        return new WebhookResponse("ACCEPTED", rawEventId, false);
    }

    public static WebhookResponse duplicate(String rawEventId) {
        return new WebhookResponse("DUPLICATE", rawEventId, true);
    }
}
