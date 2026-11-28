package com.habitloop.integration;

import com.habitloop.integration.entity.Partner;
import com.habitloop.integration.event.PartnerEventPublisher;
import com.habitloop.integration.event.PartnerEventReceived;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class PartnerOutboxIT {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("habitloop.events.transport", () -> "none");
    }

    @Autowired PartnerEventPublisher publisher;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;

    @Test
    void outboxFollowsTransactionCommitAndRollback() {
        String connection = UUID.randomUUID().toString();
        var event = new PartnerEventReceived(UUID.randomUUID().toString(), connection,
                UUID.randomUUID().toString(), Partner.FITBIT, "event", "{}");
        var transaction = new TransactionTemplate(manager);
        transaction.executeWithoutResult(status -> {
            publisher.publish(event);
            status.setRollbackOnly();
        });
        assertThat(count(connection)).isZero();
        transaction.executeWithoutResult(status -> publisher.publish(event));
        assertThat(count(connection)).isEqualTo(1);
    }

    private int count(String connection) {
        return jdbc.queryForObject("SELECT count(*) FROM partner_event_outbox WHERE partition_key = ?",
                Integer.class, connection);
    }
}
