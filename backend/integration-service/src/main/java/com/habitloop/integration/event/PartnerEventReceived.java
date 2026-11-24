package com.habitloop.integration.event;

import com.habitloop.integration.entity.Partner;

/**
 * Emitted to EventBridge (AWS) / Kafka (local) when a valid, de-duplicated partner event is stored. The
 * normalizer Lambda consumes this, enriches/normalizes, and produces {@code partner.activity.normalized}.
 */
public record PartnerEventReceived(
        String tenantId,
        String connectionId,
        String userId,
        Partner partner,
        String externalEventId,
        String rawPayload
) {
}
