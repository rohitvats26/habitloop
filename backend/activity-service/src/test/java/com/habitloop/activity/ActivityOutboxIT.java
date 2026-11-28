package com.habitloop.activity;

import com.habitloop.activity.entity.ActivitySource;
import com.habitloop.activity.event.ActivityLoggedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ActivityOutboxIT {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("habitloop.events.transport", () -> "none");
    }

    @Autowired ApplicationEventPublisher events;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;

    @Test
    void outboxFollowsTransactionCommitAndRollback() {
        String user = UUID.randomUUID().toString();
        var event = new ActivityLoggedEvent(UUID.randomUUID().toString(), user, null,
                UUID.randomUUID().toString(), "HABIT_DONE", OffsetDateTime.now(), ActivitySource.MANUAL, null);
        TransactionTemplate transaction = new TransactionTemplate(manager);
        transaction.executeWithoutResult(status -> {
            events.publishEvent(event);
            status.setRollbackOnly();
        });
        assertThat(count(user)).isZero();
        transaction.executeWithoutResult(status -> events.publishEvent(event));
        assertThat(count(user)).isEqualTo(1);
    }

    private int count(String user) {
        return jdbc.queryForObject("SELECT count(*) FROM activity_outbox WHERE partition_key LIKE ?",
                Integer.class, "%:" + user);
    }
}
