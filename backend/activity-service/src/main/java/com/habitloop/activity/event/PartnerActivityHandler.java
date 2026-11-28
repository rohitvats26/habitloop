package com.habitloop.activity.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.habitloop.activity.service.ActivityIngestService;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class PartnerActivityHandler {
    private final ObjectMapper mapper;
    private final ActivityIngestService ingest;

    public PartnerActivityHandler(ObjectMapper mapper, ActivityIngestService ingest) {
        this.mapper = mapper;
        this.ingest = ingest;
    }

    public void handle(String message) {
        NormalizedPartnerActivity activity;
        try {
            JsonNode root = mapper.readTree(message);
            JsonNode payload = root == null ? null : root.get("payload");
            if (payload == null || !payload.isObject()) {
                throw new IllegalArgumentException("Missing normalized activity payload");
            }
            activity = mapper.treeToValue(payload, NormalizedPartnerActivity.class);
            if (activity.tenantId() == null || activity.userId() == null) {
                throw new IllegalArgumentException("Missing tenant or user ID");
            }
            UUID.fromString(activity.tenantId());
            UUID.fromString(activity.userId());
            if (activity.habitId() != null) {
                UUID.fromString(activity.habitId());
            }
            if (activity.occurredAt() == null || activity.activityType() == null || activity.activityType().isBlank()
                    || activity.activityType().length() > 40 || activity.externalEventId() == null
                    || activity.externalEventId().isBlank() || activity.externalEventId().length() > 128
                    || (activity.unit() != null && activity.unit().length() > 20)
                    || (activity.value() != null && (activity.value().signum() < 0
                    || Math.max(activity.value().scale(), 0) > 2
                    || Math.max(activity.value().precision() - activity.value().scale(), 0) > 10))) {
                throw new IllegalArgumentException("Invalid normalized activity fields");
            }
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new InvalidPartnerActivityException("Invalid partner.activity.normalized message", e);
        }
        ingest.recordExternalActivity(activity);
    }

    public static class InvalidPartnerActivityException extends RuntimeException {
        public InvalidPartnerActivityException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
