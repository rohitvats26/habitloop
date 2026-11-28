package com.habitloop.common.event.eventbridge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequestEntry;

/**
 * Publishes a domain event to the shared EventBridge bus. The {@code detail} is the same envelope JSON the
 * Kafka transport uses, so consumers parse it identically after EventBridge delivers it (via SQS). Active
 * only when {@code habitloop.events.transport=eventbridge}.
 */
@Component
@ConditionalOnProperty(name = "habitloop.events.transport", havingValue = "eventbridge")
public class EventBridgePublisher {

    private static final Logger log = LoggerFactory.getLogger(EventBridgePublisher.class);

    private final EventBridgeClient client;
    private final String busName;
    private final String source;

    public EventBridgePublisher(EventBridgeClient client,
                                @Value("${habitloop.events.bus-name:}") String busName,
                                @Value("${habitloop.events.source:habitloop}") String source) {
        this.client = client;
        this.busName = busName;
        this.source = source;
    }

    /** @param detailType the event type, e.g. {@code activity.logged}. @param detailJson the envelope JSON. */
    public void publish(String detailType, String detailJson) {
        try {
            PutEventsRequestEntry entry = PutEventsRequestEntry.builder()
                    .eventBusName(busName)
                    .source(source)
                    .detailType(detailType)
                    .detail(detailJson)
                    .build();
            var result = client.putEvents(b -> b.entries(entry));
            if (result.failedEntryCount() > 0) {
                throw new IllegalStateException("EventBridge rejected " + detailType + ": " + result.entries());
            }
            log.debug("Published {} to EventBridge bus {}", detailType, busName);
        } catch (Exception e) {
            log.error("Failed to publish {} to EventBridge", detailType, e);
            throw new IllegalStateException("Failed to publish " + detailType + " to EventBridge", e);
        }
    }
}
