package com.habitloop.normalizer;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequestEntry;

import java.util.Map;

/**
 * AWS Lambda handler for partner-event normalization (docs/06 §6). Triggered by EventBridge on
 * {@code partner.event.received}; reuses {@link EventNormalizer} (the same logic the local Kafka
 * consumer uses) and publishes the normalized event back onto the same EventBridge bus
 * ({@code EVENT_BUS_NAME}) as {@code partner.activity.normalized}, which activity-service consumes to
 * store an activity log (PDF Session 3 step 5). Throwing on failure lets Lambda retry the asynchronous
 * invocation and ultimately send an invocation record to its configured failure destination.
 */
public class PartnerEventLambdaHandler implements RequestHandler<Map<String, Object>, String> {

    private final ObjectMapper mapper;
    private final EventNormalizer normalizer;
    private final EventBridgeClient events;
    private final String busName;

    public PartnerEventLambdaHandler() {
        this(new ObjectMapper(), EventBridgeClient.create(), System.getenv("EVENT_BUS_NAME"));
    }

    PartnerEventLambdaHandler(ObjectMapper mapper, EventBridgeClient events, String busName) {
        this.mapper = mapper;
        this.normalizer = new EventNormalizer(mapper);
        this.events = events;
        this.busName = busName;
    }

    @Override
    public String handleRequest(Map<String, Object> event, Context context) {
        try {
            // EventBridge wraps our envelope in the event's "detail" field.
            Object detail = event.get("detail");
            String envelopeJson = mapper.writeValueAsString(detail);

            String normalized = normalizer.normalize(envelopeJson);
            if (normalized == null) {
                throw new IllegalArgumentException("Invalid partner event");
            }
            if (busName == null || busName.isBlank()) {
                throw new IllegalStateException("EVENT_BUS_NAME is required");
            }
            PutEventsRequestEntry entry = PutEventsRequestEntry.builder()
                    .eventBusName(busName)
                    .source("habitloop.normalizer")
                    .detailType("partner.activity.normalized")
                    .detail(normalized)
                    .build();
            var result = events.putEvents(b -> b.entries(entry));
            if (result.failedEntryCount() > 0) {
                throw new IllegalStateException("EventBridge rejected normalized activity: " + result.entries());
            }
            return "OK";
        } catch (Exception e) {
            // Rethrow so Lambda retries and invokes its configured failure destination.
            throw new RuntimeException("Failed to normalize partner event", e);
        }
    }
}
