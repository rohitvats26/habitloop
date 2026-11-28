package com.habitloop.challenge;

import com.habitloop.challenge.service.AchievementService;
import com.habitloop.challenge.service.ProgressService;
import com.habitloop.common.time.TimeZones;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ChallengeConcurrencyIT {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("habitloop.events.transport", () -> "none");
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired ProgressService progress;
    @Autowired AchievementService achievements;

    @Test
    void concurrentDistinctAndDuplicateEventsAreAppliedExactlyOnce() throws Exception {
        UUID tenant = UUID.randomUUID();
        UUID user = UUID.randomUUID();
        UUID challenge = UUID.randomUUID();
        UUID participant = UUID.randomUUID();
        UUID duplicate = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now(TimeZones.INDIA);
        LocalDate today = now.toLocalDate();
        jdbc.update("""
                INSERT INTO challenges (id, tenant_id, creator_id, title, visibility, metric, target,
                                        starts_on, ends_on, status, created_at, updated_at)
                VALUES (?, ?, ?, 'Test', 'PUBLIC', 'DAYS_COMPLETED', 100, ?, ?, 'ACTIVE', ?, ?)
                """, challenge, tenant, user, today.minusDays(1), today.plusDays(1), now, now);
        jdbc.update("""
                INSERT INTO challenge_participants
                    (id, tenant_id, challenge_id, user_id, joined_at, progress, completed)
                VALUES (?, ?, ?, ?, ?, 0, false)
                """, participant, tenant, challenge, user, now.minusDays(1));

        var executor = Executors.newFixedThreadPool(8);
        var start = new CountDownLatch(1);
        try {
            var tasks = new ArrayList<java.util.concurrent.Future<?>>();
            for (int i = 0; i < 20; i++) {
                UUID activity = i < 10 ? duplicate : UUID.randomUUID();
                tasks.add(executor.submit(() -> {
                    start.await();
                    progress.applyActivity(tenant, user, activity, now);
                    return null;
                }));
            }
            start.countDown();
            for (var task : tasks) {
                task.get(30, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }
        assertThat(jdbc.queryForObject("SELECT progress FROM challenge_participants WHERE id = ?",
                java.math.BigDecimal.class, participant)).isEqualByComparingTo("11");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM challenge_progress_events WHERE participant_id = ?",
                Integer.class, participant)).isEqualTo(11);
    }

    @Test
    void achievementEventsAreIsolatedByHabitAndReplayIsIdempotent() {
        UUID tenant = UUID.randomUUID();
        UUID user = UUID.randomUUID();
        UUID firstHabit = UUID.randomUUID();
        UUID secondHabit = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now(TimeZones.INDIA);
        UUID activity = UUID.randomUUID();
        for (int i = 0; i < 7; i++) {
            achievements.recordActivity(tenant, user, i == 0 ? activity : UUID.randomUUID(),
                    i % 2 == 0 ? firstHabit : secondHabit, "HABIT_DONE", now.minusDays(i));
        }
        achievements.recordActivity(tenant, user, activity, firstHabit, "HABIT_DONE", now);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM achievement_activity_events WHERE tenant_id = ? AND user_id = ?",
                Integer.class, tenant, user)).isEqualTo(7);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM achievement_awards
                WHERE tenant_id = ? AND user_id = ? AND code = 'SEVEN_DAY_STREAK'
                """, Integer.class, tenant, user)).isZero();
    }
}
