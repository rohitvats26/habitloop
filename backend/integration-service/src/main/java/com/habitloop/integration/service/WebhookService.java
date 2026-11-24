package com.habitloop.integration.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.habitloop.common.exception.BusinessException;
import com.habitloop.common.exception.ResourceNotFoundException;
import com.habitloop.common.exception.UnauthorizedException;
import com.habitloop.integration.entity.ConnectionStatus;
import com.habitloop.integration.entity.PartnerConnection;
import com.habitloop.integration.entity.ProcessingStatus;
import com.habitloop.integration.entity.RawPartnerEvent;
import com.habitloop.integration.dto.WebhookResponse;
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

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Ingests partner webhook events: verifies the HMAC signature, de-duplicates by external event id, stores
 * the raw event in RDS, and emits {@code partner.event.received}. Unauthenticated by JWT — the connection
 * is identified by path and authenticated by HMAC (docs/06 §6, docs/08).
 */
@Service
public class WebhookService {

    private static final Logger log = LoggerFactory.getLogger(WebhookService.class);

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
            JsonNode root = objectMapper.readTree(rawBody);
            String id = root.path("externalEventId").asText(null);
            if (!StringUtils.hasText(id)) {
                throw new BusinessException("externalEventId is required");
            }
            return id;
        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            throw new BusinessException("Malformed JSON payload");
        }
    }
}
