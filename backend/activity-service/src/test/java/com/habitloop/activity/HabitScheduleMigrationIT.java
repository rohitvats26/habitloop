package com.habitloop.activity;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class HabitScheduleMigrationIT {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void migrationUsesExistingScheduleAsHistoricalBaseline() {
        var source = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        Flyway.configure().dataSource(source).target(MigrationVersion.fromVersion("4")).load().migrate();
        var jdbc = new JdbcTemplate(source);
        UUID habitId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO habits(id, tenant_id, user_id, name, frequency, schedule, target_per_period, created_at, updated_at)
                VALUES (?, ?, ?, 'Walk', 'WEEKLY', CAST(? AS jsonb), 3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, habitId, UUID.randomUUID(), UUID.randomUUID(), "{\"perWeek\":3}");

        Flyway.configure().dataSource(source).load().migrate();

        var row = jdbc.queryForMap("SELECT effective_date, frequency, schedule::text AS schedule FROM habit_schedule_versions WHERE habit_id = ?", habitId);
        assertThat(row.get("effective_date")).isEqualTo(java.sql.Date.valueOf(LocalDate.of(1, 1, 1)));
        assertThat(row.get("frequency")).isEqualTo("WEEKLY");
        assertThat(row.get("schedule").toString()).contains("\"perWeek\": 3");
    }
}
