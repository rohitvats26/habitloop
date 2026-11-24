package com.habitloop.common.event.eventbridge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

import java.util.function.Consumer;

/**
 * Lightweight long-polling SQS consumer for the EventBridge transport. Each consuming service creates one
 * poller for its queue (EventBridge rule → SQS). It unwraps the EventBridge envelope, hands the inner
 * {@code detail} (our event envelope JSON) to the handler, and deletes the message on success — failures are
 * left on the queue for redelivery / DLQ. Not a Spring bean itself; services instantiate and start it.
 */
public class SqsEventPoller {

    private static final Logger log = LoggerFactory.getLogger(SqsEventPoller.class);

    private final SqsClient sqs;
    private final ObjectMapper mapper;
    private final String queueUrl;
    private final String name;
    private final Consumer<String> handler;

    private volatile boolean running;
    private Thread thread;

    public SqsEventPoller(SqsClient sqs, ObjectMapper mapper, String queueUrl, String name, Consumer<String> handler) {
        this.sqs = sqs;
        this.mapper = mapper;
        this.queueUrl = queueUrl;
        this.name = name;
        this.handler = handler;
    }

    public void start() {
        if (queueUrl == null || queueUrl.isBlank()) {
            log.warn("SQS poller '{}' has no queue URL — not starting", name);
            return;
        }
        running = true;
        thread = new Thread(this::loop, "sqs-poller-" + name);
        thread.setDaemon(true);
        thread.start();
        log.info("Started SQS poller '{}' on {}", name, queueUrl);
    }

    public void stop() {
        running = false;
        if (thread != null) {
            thread.interrupt();
        }
    }

    private void loop() {
        while (running) {
            try {
                ReceiveMessageResponse resp = sqs.receiveMessage(b -> b
                        .queueUrl(queueUrl)
                        .maxNumberOfMessages(10)
                        .waitTimeSeconds(20));
                for (Message m : resp.messages()) {
                    try {
                        handler.accept(extractDetail(m.body()));
                        sqs.deleteMessage(b -> b.queueUrl(queueUrl).receiptHandle(m.receiptHandle()));
                    } catch (Exception ex) {
                        // Leave the message on the queue; SQS redrive / DLQ handles repeated failures.
                        log.error("Poller '{}' failed to process a message", name, ex);
                    }
                }
            } catch (Exception e) {
                if (running) {
                    log.error("Poller '{}' receive error; backing off", name, e);
                    sleep(2000);
                }
            }
        }
    }

    /** EventBridge→SQS wraps our event; return the inner {@code detail} (the envelope) as JSON. */
    private String extractDetail(String body) throws Exception {
        JsonNode node = mapper.readTree(body);
        JsonNode detail = node.get("detail");
        return detail != null ? mapper.writeValueAsString(detail) : body;
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
