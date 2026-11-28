package com.habitloop.activity.event;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(name = "habitloop.events.transport", havingValue = "kafka")
public class KafkaActivityPublisher implements ActivityEventTransport {
    private final KafkaTemplate<String, String> kafkaTemplate;

    public KafkaActivityPublisher(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void publish(String key, String envelope) {
        try {
            kafkaTemplate.send("activity.logged", key, envelope).get(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while publishing activity event", e);
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException e) {
            throw new IllegalStateException("Failed to publish activity event to Kafka", e);
        }
    }
}
