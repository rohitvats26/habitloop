package com.habitloop.activity.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.habitloop.activity.service.ActivityIngestService;
import com.habitloop.common.event.eventbridge.SqsEventPoller;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;

/**
 * EventBridge counterpart of {@link PartnerActivityConsumer}: polls this service's SQS queue (fed by the
 * {@code partner.activity.normalized} EventBridge rule) and records each as an idempotent activity log.
 * Active only when {@code habitloop.events.transport=eventbridge}.
 */
@Component
@ConditionalOnProperty(name = "habitloop.events.transport", havingValue = "eventbridge")
public class SqsPartnerActivityConsumer {

    private final ActivityIngestService ingestService;
    private final ObjectMapper objectMapper;
    private final SqsEventPoller poller;

    public SqsPartnerActivityConsumer(SqsClient sqs, ObjectMapper objectMapper,
                                      ActivityIngestService ingestService,
                                      @Value("${habitloop.events.queue-url:}") String queueUrl) {
        this.ingestService = ingestService;
        this.objectMapper = objectMapper;
        this.poller = new SqsEventPoller(sqs, objectMapper, queueUrl, "activity", this::handle);
    }

    private void handle(String detail) {
        try {
            NormalizedPartnerActivity activity = objectMapper.readValue(detail, NormalizedPartnerActivity.class);
            ingestService.recordExternalActivity(activity);
        } catch (Exception e) {
            throw new RuntimeException("Failed to process partner.activity.normalized", e);
        }
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
