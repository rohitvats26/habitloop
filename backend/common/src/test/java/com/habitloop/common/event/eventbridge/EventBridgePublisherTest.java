package com.habitloop.common.event.eventbridge;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequest;
import software.amazon.awssdk.services.eventbridge.model.PutEventsResponse;
import software.amazon.awssdk.services.eventbridge.model.PutEventsResultEntry;

import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EventBridgePublisherTest {
    @Test
    void partialFailureIsReportedToCaller() {
        EventBridgeClient client = mock(EventBridgeClient.class);
        when(client.putEvents(org.mockito.ArgumentMatchers.<Consumer<PutEventsRequest.Builder>>any()))
                .thenReturn(PutEventsResponse.builder().failedEntryCount(1)
                .entries(PutEventsResultEntry.builder().errorCode("InternalFailure").build()).build());
        EventBridgePublisher publisher = new EventBridgePublisher(client, "bus", "habitloop");
        assertThatThrownBy(() -> publisher.publish("activity.logged", "{}"))
                .isInstanceOf(IllegalStateException.class);
    }
}
