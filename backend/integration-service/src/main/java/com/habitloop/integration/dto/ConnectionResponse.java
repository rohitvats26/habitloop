package com.habitloop.integration.dto;

import com.habitloop.integration.entity.ConnectionStatus;
import com.habitloop.integration.entity.Partner;

import java.time.OffsetDateTime;

/** {@code apiKey} and {@code hmacSecret} are populated only in the create response (shown once) and null when listing. */
public record ConnectionResponse(
        String connectionId,
        Partner partner,
        ConnectionStatus status,
        String apiKey,
        String hmacSecret,
        String webhookUrl,
        OffsetDateTime connectedAt,
        OffsetDateTime lastEventAt
) {
}
