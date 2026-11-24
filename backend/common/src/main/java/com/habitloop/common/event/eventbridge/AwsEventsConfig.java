package com.habitloop.common.event.eventbridge;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.sqs.SqsClient;

/**
 * AWS clients for the EventBridge/SQS event transport. Created only when
 * {@code habitloop.events.transport=eventbridge} (i.e. on AWS), so local/test runs never touch AWS.
 * Region + credentials come from the default provider chain (AWS_REGION + the ECS task role).
 */
@Configuration
@ConditionalOnProperty(name = "habitloop.events.transport", havingValue = "eventbridge")
public class AwsEventsConfig {

    @Bean(destroyMethod = "close")
    public EventBridgeClient eventBridgeClient() {
        return EventBridgeClient.create();
    }

    @Bean(destroyMethod = "close")
    public SqsClient sqsClient() {
        return SqsClient.create();
    }
}
