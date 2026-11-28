package com.habitloop.challenge.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.habitloop.challenge.service.AchievementService;
import com.habitloop.challenge.service.ProgressService;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ActivityEventHandlerTest {
    @Test
    void failureAfterProgressIsPropagatedAndReplayPassesTheSameIds() {
        var progress = mock(ProgressService.class);
        var achievements = mock(AchievementService.class);
        var handler = new ActivityEventHandler(new ObjectMapper(), progress, achievements);
        var tenant = UUID.randomUUID();
        var user = UUID.randomUUID();
        var activity = UUID.randomUUID();
        var habit = UUID.randomUUID();
        var time = OffsetDateTime.parse("2026-01-01T00:00:00Z");
        String json = """
                {"payload":{"tenantId":"%s","userId":"%s","activityLogId":"%s","habitId":"%s",
                "activityType":"HABIT_DONE","occurredAt":"2026-01-01T00:00:00Z"}}
                """.formatted(tenant, user, activity, habit);
        doThrow(new IllegalStateException("temporary failure")).doNothing()
                .when(achievements).recordActivity(tenant, user, activity, habit, "HABIT_DONE", time);

        assertThatThrownBy(() -> handler.handle(json)).isInstanceOf(IllegalStateException.class);
        handler.handle(json);

        verify(progress, times(2)).applyActivity(tenant, user, activity, time);
        verify(achievements, times(2)).recordActivity(tenant, user, activity, habit, "HABIT_DONE", time);
    }

    @Test
    void malformedActivityIsRejectedBeforeEitherUpdate() {
        var progress = mock(ProgressService.class);
        var achievements = mock(AchievementService.class);
        var handler = new ActivityEventHandler(new ObjectMapper(), progress, achievements);
        assertThatThrownBy(() -> handler.handle("{\"payload\":{\"tenantId\":\"invalid\"}}"))
                .isInstanceOf(ActivityEventHandler.InvalidActivityEventException.class);
        verifyNoInteractions(progress, achievements);
    }
}
