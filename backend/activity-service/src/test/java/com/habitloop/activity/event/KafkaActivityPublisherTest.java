package com.habitloop.activity.event;

import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KafkaActivityPublisherTest {
    @Test
    void asynchronousBrokerFailureIsPropagated() {
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new IllegalStateException("broker unavailable"));
        when(kafka.send("activity.logged", "tenant:user", "payload")).thenReturn(failed);
        assertThatThrownBy(() -> new KafkaActivityPublisher(kafka).publish("tenant:user", "payload"))
                .isInstanceOf(IllegalStateException.class);
    }
}
