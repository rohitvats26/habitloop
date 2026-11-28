package com.habitloop.common.event.eventbridge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
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
    private ScheduledExecutorService visibilityExecutor;

    public SqsEventPoller(SqsClient sqs, ObjectMapper mapper, String queueUrl, String name, Consumer<String> handler) {
        this.sqs = sqs;
        this.mapper = mapper;
        this.queueUrl = queueUrl;
        this.name = name;
        this.handler = handler;
    }

    public void start() {
        if (queueUrl == null || queueUrl.isBlank()) {
            throw new IllegalStateException("SQS poller '" + name + "' requires a queue URL");
        }
        visibilityExecutor = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread worker = new Thread(task, "sqs-visibility-" + name);
            worker.setDaemon(true);
            return worker;
        });
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
        try {
            while (running) {
                try {
                    ReceiveMessageResponse resp = sqs.receiveMessage(b -> b
                            .queueUrl(queueUrl)
                            .maxNumberOfMessages(1)
                            .visibilityTimeout(60)
                            .waitTimeSeconds(20));
                    for (Message m : resp.messages()) {
                        if (!running) {
                            break;
                        }
                        var renewal = visibilityExecutor.scheduleWithFixedDelay(() -> {
                            try {
                                sqs.changeMessageVisibility(b -> b.queueUrl(queueUrl)
                                        .receiptHandle(m.receiptHandle()).visibilityTimeout(60));
                            } catch (Exception ex) {
                                log.error("Poller '{}' failed to renew message visibility", name, ex);
                            }
                        }, 20, 20, TimeUnit.SECONDS);
                        try {
                            handler.accept(extractDetail(m.body()));
                            renewal.cancel(false);
                            sqs.deleteMessage(b -> b.queueUrl(queueUrl).receiptHandle(m.receiptHandle()));
                        } catch (Exception ex) {
                            // Leave the message on the queue; SQS redrive / DLQ handles repeated failures.
                            log.error("Poller '{}' failed to process a message", name, ex);
                        } finally {
                            renewal.cancel(false);
                        }
                    }
                } catch (Exception e) {
                    if (running) {
                        log.error("Poller '{}' receive error; backing off", name, e);
                        sleep(2000);
                    }
                }
            }
        } finally {
            visibilityExecutor.shutdownNow();
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
