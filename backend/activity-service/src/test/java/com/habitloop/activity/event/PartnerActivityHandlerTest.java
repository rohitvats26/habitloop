package com.habitloop.activity.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.habitloop.activity.service.ActivityIngestService;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PartnerActivityHandlerTest {
    private final ActivityIngestService ingest = mock(ActivityIngestService.class);
    private final PartnerActivityHandler handler =
            new PartnerActivityHandler(new ObjectMapper().registerModule(new JavaTimeModule()), ingest);
    private final String valid = """
            {"eventType":"partner.activity.normalized","payload":{"tenantId":"%s","userId":"%s",
            "activityType":"STEPS","occurredAt":"2026-01-01T00:00:00Z","externalEventId":"evt",
            "source":"WEARABLE"}}
            """.formatted(UUID.randomUUID(), UUID.randomUUID());

    @Test
    void consumesNormalizedEnvelopeAndPropagatesTransientFailures() {
        doThrow(new IllegalStateException("database unavailable")).when(ingest).recordExternalActivity(any());
        assertThatThrownBy(() -> handler.handle(valid)).isInstanceOf(IllegalStateException.class);
        verify(ingest).recordExternalActivity(any(NormalizedPartnerActivity.class));
    }

    @Test
    void rejectsMalformedMessagesWithoutCallingIngest() {
        assertThatThrownBy(() -> handler.handle("{\"payload\":{}}"))
                .isInstanceOf(PartnerActivityHandler.InvalidPartnerActivityException.class);
        verifyNoInteractions(ingest);
    }
}
