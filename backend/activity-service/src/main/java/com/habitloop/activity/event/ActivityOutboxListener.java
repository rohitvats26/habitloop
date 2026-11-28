package com.habitloop.activity.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.habitloop.common.event.EventEnvelope;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class ActivityOutboxListener {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public ActivityOutboxListener(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(ActivityLoggedEvent event) {
        var envelope = EventEnvelope.create("activity.logged", event.tenantId(), null, event.idempotencyKey(), event);
        try {
            jdbc.update("INSERT INTO activity_outbox (id, partition_key, envelope) VALUES (?, ?, ?)",
                    UUID.fromString(envelope.eventId()), event.tenantId() + ":" + event.userId(),
                    mapper.writeValueAsString(envelope));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize activity event for the outbox", e);
        }
    }
}
