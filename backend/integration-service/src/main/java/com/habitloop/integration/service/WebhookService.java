package com.habitloop.integration.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.habitloop.common.exception.InvalidRequestException;
import com.habitloop.common.exception.ResourceNotFoundException;
import com.habitloop.common.exception.UnauthorizedException;
import com.habitloop.integration.dto.WebhookResponse;
import com.habitloop.integration.entity.ConnectionStatus;
import com.habitloop.integration.entity.PartnerConnection;
import com.habitloop.integration.entity.ProcessingStatus;
import com.habitloop.integration.entity.RawPartnerEvent;
import com.habitloop.integration.event.PartnerEventPublisher;
import com.habitloop.integration.event.PartnerEventReceived;
import com.habitloop.integration.repository.PartnerConnectionRepository;
import com.habitloop.integration.repository.RawPartnerEventRepository;
import com.habitloop.integration.security.HmacSigner;
import com.habitloop.integration.security.SecretStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.UUID;

/**
 * Ingests partner webhook events: verifies the HMAC signature, de-duplicates by external event id, stores
 * the raw event in RDS, and emits {@code partner.event.received}. Unauthenticated by JWT — the connection
 * is identified by path and authenticated by HMAC (docs/06 §6, docs/08).
 */
@Service
public class WebhookService {

    private static final Logger log = LoggerFactory.getLogger(WebhookService.class);
    private static final int MAX_PAYLOAD_BYTES = 256 * 1024;
    private static final int MAX_EXTERNAL_EVENT_ID_LENGTH = 128;

    private final PartnerConnectionRepository connections;
    private final RawPartnerEventRepository rawEvents;
    private final SecretStore secretStore;
    private final PartnerEventPublisher publisher;
    private final ObjectMapper objectMapper;

    public WebhookService(PartnerConnectionRepository connections, RawPartnerEventRepository rawEvents,
                          SecretStore secretStore, PartnerEventPublisher publisher, ObjectMapper objectMapper) {
        this.connections = connections;
        this.rawEvents = rawEvents;
        this.secretStore = secretStore;
        this.publisher = publisher;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public WebhookResponse ingest(UUID connectionId, String signature, String rawBody) {
        validatePayloadSize(rawBody);
        PartnerConnection connection = connections.findById(connectionId)
                .orElseThrow(() -> new ResourceNotFoundException("Connection", connectionId));
        if (connection.getStatus() == ConnectionStatus.REVOKED) {
            throw new UnauthorizedException("Connection is revoked");
        }

        String secret = secretStore.resolve(connection.getHmacSecretRef());
        if (secret == null || !HmacSigner.verify(secret, rawBody, signature)) {
            log.warn("Rejected webhook for connection {}: invalid signature", connectionId);
            throw new UnauthorizedException("Invalid signature");
        }

        String externalEventId = extractExternalEventId(rawBody);

        if (rawEvents.existsByConnectionIdAndExternalEventId(connectionId, externalEventId)) {
            log.info("Duplicate webhook event {} for connection {} — no-op", externalEventId, connectionId);
            return WebhookResponse.duplicate(null);
        }

        RawPartnerEvent event = new RawPartnerEvent();
        event.setTenantId(connection.getTenantId());
        event.setConnectionId(connectionId);
        event.setExternalEventId(externalEventId);
        event.setPayload(rawBody);
        event.setProcessingStatus(ProcessingStatus.RECEIVED);
        try {
            event = rawEvents.save(event);
        } catch (DataIntegrityViolationException dup) {
            return WebhookResponse.duplicate(null);
        }

        connection.setLastEventAt(OffsetDateTime.now());
        connections.save(connection);

        publisher.publish(new PartnerEventReceived(
                connection.getTenantId().toString(), connectionId.toString(),
                connection.getUserId().toString(), connection.getPartner(), externalEventId, rawBody));

        return WebhookResponse.accepted(event.getId().toString());
    }

    private String extractExternalEventId(String rawBody) {
        try {
            JsonNode root = objectMapper.reader()
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .readTree(rawBody);
            if (!root.isObject()) {
                throw new InvalidRequestException("Webhook payload must be a JSON object");
            }
            JsonNode idNode = root.path("externalEventId");
            if (!idNode.isTextual()) {
                throw new InvalidRequestException("externalEventId must be a non-empty string");
            }
            String id = idNode.textValue();
            if (!StringUtils.hasText(id) || !id.equals(id.strip())
                    || id.chars().anyMatch(Character::isISOControl)) {
                throw new InvalidRequestException(
                        "externalEventId must be a non-empty string without surrounding whitespace");
            }
            if (id.length() > MAX_EXTERNAL_EVENT_ID_LENGTH) {
                throw new InvalidRequestException("externalEventId must not exceed 128 characters");
            }
            validateOptionalText(root, "type", 40);
            validateOptionalText(root, "unit", 20);
            validateOptionalValue(root.path("value"));
            validateOptionalOccurredAt(root.path("occurredAt"));
            validateOptionalHabitId(root.path("habitId"));
            return id;
        } catch (InvalidRequestException invalidRequest) {
            throw invalidRequest;
        } catch (Exception e) {
            throw new InvalidRequestException("Malformed JSON payload");
        }
    }

    private void validateOptionalText(JsonNode root, String field, int maxLength) {
        JsonNode value = root.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return;
        }
        if (!value.isTextual() || !StringUtils.hasText(value.textValue())
                || value.textValue().length() > maxLength) {
            throw new InvalidRequestException(field + " must be a non-empty string of at most "
                    + maxLength + " characters");
        }
    }

    private void validateOptionalValue(JsonNode value) {
        if (value.isMissingNode() || value.isNull()) {
            return;
        }
        if (!value.isNumber()) {
            throw new InvalidRequestException("value must be a non-negative number with at most 2 decimal places");
        }
        BigDecimal decimal = value.decimalValue();
        int integerDigits = Math.max(decimal.precision() - decimal.scale(), 0);
        int fractionDigits = Math.max(decimal.scale(), 0);
        if (decimal.signum() < 0 || integerDigits > 10 || fractionDigits > 2) {
            throw new InvalidRequestException("value must be a non-negative number with at most 10 integer "
                    + "digits and 2 decimal places");
        }
    }

    private void validateOptionalOccurredAt(JsonNode value) {
        if (value.isMissingNode() || value.isNull()) {
            return;
        }
        if (!value.isTextual()) {
            throw new InvalidRequestException("occurredAt must be an ISO-8601 date-time");
        }
        try {
            OffsetDateTime.parse(value.textValue());
        } catch (DateTimeParseException ex) {
            throw new InvalidRequestException("occurredAt must be an ISO-8601 date-time");
        }
    }

    private void validateOptionalHabitId(JsonNode value) {
        if (value.isMissingNode() || value.isNull()) {
            return;
        }
        if (!value.isTextual()) {
            throw new InvalidRequestException("habitId must be a valid UUID");
        }
        try {
            UUID habitId = UUID.fromString(value.textValue());
            if (!habitId.toString().equalsIgnoreCase(value.textValue())) {
                throw new IllegalArgumentException("UUID must use the standard format");
            }
        } catch (IllegalArgumentException ex) {
            throw new InvalidRequestException("habitId must be a valid UUID");
        }
    }

    private void validatePayloadSize(String rawBody) {
        if (!StringUtils.hasText(rawBody)) {
            throw new InvalidRequestException("Webhook payload is required");
        }
        if (rawBody.length() > MAX_PAYLOAD_BYTES
                || rawBody.getBytes(StandardCharsets.UTF_8).length > MAX_PAYLOAD_BYTES) {
            throw new InvalidRequestException("Webhook payload must not exceed 256 KB");
        }
    }
}
