package com.habitloop.normalizer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PartnerEventListenerTest {
    @Test
    void brokerFailureIsPropagatedAndInvalidInputIsClassified() {
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        var listener = new PartnerEventListener(kafka, new ObjectMapper());
        String message = """
                {"payload":{"tenantId":"t","userId":"u","externalEventId":"e",
                "rawPayload":"{\\"externalEventId\\":\\"e\\",\\"type\\":\\"STEPS\\"}"}}
                """;
        CompletableFuture<SendResult<String, String>> failed =
                CompletableFuture.failedFuture(new IllegalStateException("broker unavailable"));
        when(kafka.send(eq("partner.activity.normalized"), eq("t:u"), anyString())).thenReturn(failed);
        assertThatThrownBy(() -> listener.onMessage(message)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> listener.onMessage("{\"payload\":{}}"))
                .isInstanceOf(PartnerEventListener.InvalidPartnerEventException.class);
    }
}
