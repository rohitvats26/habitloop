package com.habitloop.challenge.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.habitloop.common.event.eventbridge.SqsEventPoller;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;

/**
 * EventBridge counterpart of {@link ActivityLoggedConsumer}: polls this service's SQS queue (fed by the
 * {@code activity.logged} EventBridge rule) and updates challenge progress. Active only when
 * {@code habitloop.events.transport=eventbridge}.
 */
@Component
@ConditionalOnProperty(name = "habitloop.events.transport", havingValue = "eventbridge")
public class SqsActivityLoggedConsumer {

    private final SqsEventPoller poller;

    public SqsActivityLoggedConsumer(SqsClient sqs, ObjectMapper objectMapper,
                                     ActivityEventHandler handler,
                                     @Value("${habitloop.events.queue-url:}") String queueUrl) {
        this.poller = new SqsEventPoller(sqs, objectMapper, queueUrl, "challenge", handler::handle);
    }

    @PostConstruct
    void start() {
        poller.start();
    }

    @PreDestroy
    void stop() {
        poller.stop();
    }
}
