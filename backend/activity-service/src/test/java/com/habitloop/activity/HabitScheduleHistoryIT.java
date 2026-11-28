package com.habitloop.activity;

import com.habitloop.activity.dto.CompletionRequest;
import com.habitloop.activity.dto.CreateHabitRequest;
import com.habitloop.activity.dto.UpdateHabitRequest;
import com.habitloop.activity.entity.Frequency;
import com.habitloop.activity.service.HabitService;
import com.habitloop.common.security.AuthUser;
import com.habitloop.common.time.TimeZones;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class HabitScheduleHistoryIT {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("habitloop.events.transport", () -> "none");
    }

    @Autowired HabitService service;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void commitsScheduleAndSnapshotWithBackfilledCompletion() {
        String tenant = UUID.randomUUID().toString();
        String user = UUID.randomUUID().toString();
        var principal = new AuthUser(user, tenant, List.of("USER"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
        var created = service.create(new CreateHabitRequest("Walk", null, Frequency.DAILY, Map.of(), 1));
        UUID habitId = UUID.fromString(created.id());
        LocalDate today = LocalDate.now(TimeZones.INDIA);
        service.complete(habitId, new CompletionRequest(today, null));
        service.complete(habitId, new CompletionRequest(today.minusDays(1), null));
        var updated = service.update(habitId,
                new UpdateHabitRequest(null, null, Frequency.WEEKLY, Map.of("perWeek", 2), 2, null));

        assertThat(updated.streak().current()).isEqualTo(2);
        assertThat(updated.frequency()).isEqualTo(Frequency.DAILY);
        assertThat(updated.pendingSchedule().effectiveDate())
                .isEqualTo(today.with(TemporalAdjusters.next(DayOfWeek.MONDAY)));
        assertThat(service.get(habitId).pendingSchedule()).isEqualTo(updated.pendingSchedule());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM habit_schedule_versions WHERE habit_id = ?",
                Integer.class, habitId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT snapshot_date FROM habit_streaks WHERE habit_id = ?",
                LocalDate.class, habitId)).isEqualTo(today.with(DayOfWeek.MONDAY));
    }
}
