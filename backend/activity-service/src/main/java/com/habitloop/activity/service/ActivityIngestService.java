package com.habitloop.activity.service;

import com.habitloop.activity.entity.ActivityLog;
import com.habitloop.activity.entity.ActivitySource;
import com.habitloop.activity.event.ActivityLoggedEvent;
import com.habitloop.activity.event.NormalizedPartnerActivity;
import com.habitloop.activity.repository.ActivityLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Turns a normalized partner activity into exactly one activity log. Idempotent by
 * (tenant, user, source, externalEventId): a replayed event is detected and skipped (NFR-2).
 */
@Service
public class ActivityIngestService {

    private static final Logger log = LoggerFactory.getLogger(ActivityIngestService.class);

    private final ActivityLogRepository activityLogs;
    private final ApplicationEventPublisher events;

    public ActivityIngestService(ActivityLogRepository activityLogs, ApplicationEventPublisher events) {
        this.activityLogs = activityLogs;
        this.events = events;
    }

    @Transactional
    public void recordExternalActivity(NormalizedPartnerActivity n) {
        ActivitySource source = n.source() != null ? n.source() : ActivitySource.WEARABLE;
        UUID tenantId = UUID.fromString(n.tenantId());
        UUID userId = UUID.fromString(n.userId());

        if (activityLogs.existsByTenantIdAndUserIdAndSourceAndExternalEventId(
                tenantId, userId, source, n.externalEventId())) {
            log.info("Duplicate partner event {} for user {} — skipping", n.externalEventId(), userId);
            return;
        }

        ActivityLog entry = new ActivityLog();
        entry.setTenantId(tenantId);
        entry.setUserId(userId);
        entry.setHabitId(n.habitId() == null ? null : UUID.fromString(n.habitId()));
        entry.setActivityType(n.activityType());
        entry.setValue(n.value());
        entry.setUnit(n.unit());
        entry.setOccurredAt(n.occurredAt());
        entry.setSource(source);
        entry.setExternalEventId(n.externalEventId());

        try {
            entry = activityLogs.save(entry);
        } catch (DataIntegrityViolationException dup) {
            // Lost the race against a concurrent delivery of the same event — safe to skip.
            log.info("Concurrent duplicate partner event {} — skipping", n.externalEventId());
            return;
        }

        events.publishEvent(new ActivityLoggedEvent(
                n.tenantId(), n.userId(), n.habitId(), entry.getId().toString(),
                n.activityType(), n.occurredAt(), source, n.externalEventId()));
        log.info("Recorded external activity {} for user {}", n.externalEventId(), userId);
    }
}
