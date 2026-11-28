package com.habitloop.normalizer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequest;
import software.amazon.awssdk.services.eventbridge.model.PutEventsResponse;

import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PartnerEventLambdaHandlerTest {
    @Test
    void rejectedEventBridgeEntryFailsInvocation() {
        EventBridgeClient events = mock(EventBridgeClient.class);
        when(events.putEvents(org.mockito.ArgumentMatchers.<Consumer<PutEventsRequest.Builder>>any()))
                .thenReturn(PutEventsResponse.builder().failedEntryCount(1).build());
        var handler = new PartnerEventLambdaHandler(new ObjectMapper(), events, "bus");
        var event = Map.<String, Object>of("detail", Map.of("payload", Map.of(
                "tenantId", "t", "userId", "u", "externalEventId", "e",
                "rawPayload", "{\"externalEventId\":\"e\"}")));
        assertThatThrownBy(() -> handler.handleRequest(event, null))
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    void missingBusFailsRatherThanReportingSuccess() {
        EventBridgeClient events = mock(EventBridgeClient.class);
        var handler = new PartnerEventLambdaHandler(new ObjectMapper(), events, "");
        var event = Map.<String, Object>of("detail", Map.of("payload", Map.of(
                "tenantId", "t", "userId", "u", "externalEventId", "e",
                "rawPayload", "{\"externalEventId\":\"e\"}")));
        assertThatThrownBy(() -> handler.handleRequest(event, null))
                .hasCauseInstanceOf(IllegalStateException.class);
        verifyNoInteractions(events);
    }
}
