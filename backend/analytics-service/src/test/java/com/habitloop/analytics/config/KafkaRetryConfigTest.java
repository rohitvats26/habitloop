package com.habitloop.analytics.config;

import com.habitloop.analytics.event.AnalyticsActivityConsumer.InvalidActivityEventException;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.listener.ListenerExecutionFailedException;
import org.springframework.kafka.support.SendResult;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class KafkaRetryConfigTest {
    @Test
    void malformedEventIsDeadLetteredButFailedPublicationIsNotRecovered() {
        @SuppressWarnings("unchecked")
        KafkaTemplate<Object, Object> template = mock(KafkaTemplate.class);
        @SuppressWarnings("unchecked")
        ProducerFactory<Object, Object> producerFactory = mock(ProducerFactory.class);
        @SuppressWarnings("unchecked")
        Consumer<Object, Object> consumer = mock(Consumer.class);
        when(template.getProducerFactory()).thenReturn(producerFactory);
        when(producerFactory.getConfigurationProperties()).thenReturn(Map.of());
        var handler = new KafkaRetryConfig().analyticsErrorHandler(template);
        var message = new ConsumerRecord<>("activity.logged", 0, 7L, "key", "invalid");
        var invalid = new ListenerExecutionFailedException("listener failed",
                new InvalidActivityEventException("malformed"));
        when(template.send(any(ProducerRecord.class))).thenAnswer(invocation -> {
            ProducerRecord<Object, Object> record = invocation.getArgument(0);
            assertThat(record.topic()).isEqualTo("activity.logged.analytics.DLT");
            return CompletableFuture.completedFuture(new SendResult<>(record, null));
        });

        assertThat(handler.handleOne(invalid, message, consumer, mock(MessageListenerContainer.class))).isTrue();
        verify(template).send(any(ProducerRecord.class));

        doReturn(CompletableFuture.failedFuture(new IllegalStateException("broker unavailable")))
                .when(template).send(any(ProducerRecord.class));
        assertThat(handler.handleOne(invalid, message, consumer, mock(MessageListenerContainer.class))).isFalse();
    }
}
