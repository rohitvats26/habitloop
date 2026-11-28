package com.habitloop.common.event.eventbridge;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityRequest;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityResponse;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class SqsEventPollerTest {
    @Test
    void missingQueueUrlFailsStartup() {
        var poller = new SqsEventPoller(mock(SqsClient.class), new ObjectMapper(), "", "test", message -> {});
        assertThatThrownBy(poller::start).isInstanceOf(IllegalStateException.class).hasMessageContaining("queue URL");
    }

    @Test
    void renewsVisibilityDuringProcessingAndDeletesAfterSuccess() throws Exception {
        SqsClient sqs = mock(SqsClient.class);
        CountDownLatch renewed = new CountDownLatch(1);
        receiveOne(sqs);
        when(sqs.changeMessageVisibility(org.mockito.ArgumentMatchers.<Consumer<ChangeMessageVisibilityRequest.Builder>>any()))
                .thenAnswer(invocation -> {
                    renewed.countDown();
                    return ChangeMessageVisibilityResponse.builder().build();
                });
        var poller = new SqsEventPoller(sqs, new ObjectMapper(), "queue", "test", message -> {
            try {
                if (!renewed.await(30, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("visibility was not renewed");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        });
        poller.start();
        try {
            assertThat(renewed.await(30, TimeUnit.SECONDS)).isTrue();
            verify(sqs, timeout(3000)).deleteMessage(org.mockito.ArgumentMatchers.<Consumer<DeleteMessageRequest.Builder>>any());
        } finally {
            poller.stop();
        }
    }

    @Test
    void failedProcessingDoesNotDeleteMessage() throws Exception {
        SqsClient sqs = mock(SqsClient.class);
        CountDownLatch handled = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        receiveOne(sqs, finished);
        var poller = new SqsEventPoller(sqs, new ObjectMapper(), "queue", "test", message -> {
            handled.countDown();
            throw new IllegalStateException("handler failed");
        });
        poller.start();
        try {
            assertThat(handled.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(finished.await(5, TimeUnit.SECONDS)).isTrue();
            verify(sqs, never()).deleteMessage(org.mockito.ArgumentMatchers.<Consumer<DeleteMessageRequest.Builder>>any());
        } finally {
            poller.stop();
        }
    }

    private void receiveOne(SqsClient sqs) {
        receiveOne(sqs, new CountDownLatch(1));
    }

    private void receiveOne(SqsClient sqs, CountDownLatch finished) {
        when(sqs.receiveMessage(org.mockito.ArgumentMatchers.<Consumer<ReceiveMessageRequest.Builder>>any()))
                .thenReturn(ReceiveMessageResponse.builder().messages(
                        Message.builder().body("{\"detail\":{\"eventId\":\"id\"}}").receiptHandle("receipt").build()).build())
                .thenAnswer(invocation -> {
                    finished.countDown();
                    new CountDownLatch(1).await();
                    return ReceiveMessageResponse.builder().build();
                });
    }
}
