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
 * store an activity log (PDF Session 3 step 5). Throwing on failure lets EventBridge retry and finally
 * route to the DLQ. No SNS is used (it is not in the brief).
 */
public class PartnerEventLambdaHandler implements RequestHandler<Map<String, Object>, String> {

    private final ObjectMapper mapper = new ObjectMapper();
    private final EventNormalizer normalizer = new EventNormalizer(mapper);
    private final EventBridgeClient events = EventBridgeClient.create();
    private final String busName = System.getenv("EVENT_BUS_NAME");

    @Override
    public String handleRequest(Map<String, Object> event, Context context) {
        try {
            // EventBridge wraps our envelope in the event's "detail" field.
            Object detail = event.get("detail");
            String envelopeJson = mapper.writeValueAsString(detail);

            String normalized = normalizer.normalize(envelopeJson);
            if (normalized == null) {
                context.getLogger().log("Dropping invalid partner event");
                return "INVALID";
            }
            if (busName != null && !busName.isBlank()) {
                PutEventsRequestEntry entry = PutEventsRequestEntry.builder()
                        .eventBusName(busName)
                        .source("habitloop.normalizer")
                        .detailType("partner.activity.normalized")
                        .detail(normalized)
                        .build();
                events.putEvents(b -> b.entries(entry));
            }
            return "OK";
        } catch (Exception e) {
            // Rethrow so EventBridge retries and ultimately sends to the DLQ.
            throw new RuntimeException("Failed to normalize partner event", e);
        }
    }
}
