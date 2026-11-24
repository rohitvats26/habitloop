package com.habitloop.common.event;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Common envelope for all domain/integration events (see docs/07). {@code idempotencyKey}
 * identifies the business fact so replays are safe; {@code correlationId} ties events back to
 * the originating request.
 */
public record EventEnvelope<T>(
        String eventId,
        String eventType,
        String tenantId,
        OffsetDateTime occurredAt,
        String correlationId,
        String idempotencyKey,
        T payload
) {
    public static <T> EventEnvelope<T> create(String eventType, String tenantId,
                                              String correlationId, String idempotencyKey, T payload) {
        return new EventEnvelope<>(UUID.randomUUID().toString(), eventType, tenantId,
                OffsetDateTime.now(), correlationId, idempotencyKey, payload);
    }
}
