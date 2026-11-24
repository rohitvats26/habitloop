package com.habitloop.normalizer;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.sns.SnsClient;

import java.util.Map;

/**
 * AWS Lambda handler for partner-event normalization (docs/06 §6). Triggered by EventBridge on
 * {@code partner.event.received}; reuses {@link EventNormalizer} (the same logic the local Kafka
 * consumer uses) and publishes the normalized event to the SNS topic in {@code NORMALIZED_TOPIC_ARN}.
 * Throwing on failure lets EventBridge retry and finally route to the DLQ.
 */
public class PartnerEventLambdaHandler implements RequestHandler<Map<String, Object>, String> {

    private final ObjectMapper mapper = new ObjectMapper();
    private final EventNormalizer normalizer = new EventNormalizer(mapper);
    private final SnsClient sns = SnsClient.create();
    private final String topicArn = System.getenv("NORMALIZED_TOPIC_ARN");

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
            if (topicArn != null && !topicArn.isBlank()) {
                sns.publish(b -> b.topicArn(topicArn).message(normalized));
            }
            return "OK";
        } catch (Exception e) {
            // Rethrow so EventBridge retries and ultimately sends to the DLQ.
            throw new RuntimeException("Failed to normalize partner event", e);
        }
    }
}
