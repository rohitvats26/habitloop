package com.habitloop.activity.event;

import com.habitloop.common.event.eventbridge.EventBridgePublisher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "habitloop.events.transport", havingValue = "eventbridge")
public class EventBridgeActivityPublisher implements ActivityEventTransport {
    private final EventBridgePublisher publisher;

    public EventBridgeActivityPublisher(EventBridgePublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void publish(String key, String envelope) {
        publisher.publish("activity.logged", envelope);
    }
}
