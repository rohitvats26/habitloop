package com.habitloop.integration.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.habitloop.common.event.EventEnvelope;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Records a partner event in the same transaction as the webhook receipt. */
@Component
public class PartnerEventPublisher {
    public static final String TOPIC = "partner.event.received";
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public PartnerEventPublisher(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(PartnerEventReceived event) {
        EventEnvelope<PartnerEventReceived> envelope = EventEnvelope.create(
                "partner.event.received", event.tenantId(), null,
                event.connectionId() + ":" + event.externalEventId(), event);
        try {
            jdbc.update("INSERT INTO partner_event_outbox (id, partition_key, envelope) VALUES (?, ?, ?)",
                    UUID.fromString(envelope.eventId()), event.connectionId(), objectMapper.writeValueAsString(envelope));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize partner event for the outbox", e);
        }
    }
}
