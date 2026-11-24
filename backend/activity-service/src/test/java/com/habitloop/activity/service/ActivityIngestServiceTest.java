package com.habitloop.activity.service;

import com.habitloop.activity.entity.ActivityLog;
import com.habitloop.activity.entity.ActivitySource;
import com.habitloop.activity.event.ActivityLoggedEvent;
import com.habitloop.activity.event.NormalizedPartnerActivity;
import com.habitloop.activity.repository.ActivityLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActivityIngestServiceTest {

    @Mock ActivityLogRepository activityLogs;
    @Mock ApplicationEventPublisher events;

    ActivityIngestService service;

    final String tenantId = UUID.randomUUID().toString();
    final String userId = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        service = new ActivityIngestService(activityLogs, events);
    }

    @Test
    void duplicateExternalEvent_isSkipped_noLogNoEvent() {
        when(activityLogs.existsByTenantIdAndUserIdAndSourceAndExternalEventId(
                any(), any(), eq(ActivitySource.WEARABLE), eq("evt_123"))).thenReturn(true);

        service.recordExternalActivity(event("evt_123"));

        verify(activityLogs, never()).save(any());
        verify(events, never()).publishEvent(any());
    }

    @Test
    void newExternalEvent_createsLog_andPublishes() {
        when(activityLogs.existsByTenantIdAndUserIdAndSourceAndExternalEventId(
                any(), any(), eq(ActivitySource.WEARABLE), eq("evt_999"))).thenReturn(false);
        when(activityLogs.save(any(ActivityLog.class))).thenAnswer(i -> {
            ActivityLog l = i.getArgument(0);
            l.setId(UUID.randomUUID());
            return l;
        });

        service.recordExternalActivity(event("evt_999"));

        verify(activityLogs).save(any(ActivityLog.class));
        verify(events).publishEvent(any(ActivityLoggedEvent.class));
    }

    private NormalizedPartnerActivity event(String externalId) {
        return new NormalizedPartnerActivity(tenantId, userId, null, "STEPS",
                new BigDecimal("8450"), "steps", OffsetDateTime.now(), externalId, ActivitySource.WEARABLE);
    }
}
