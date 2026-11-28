package com.habitloop.integration.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.habitloop.integration.entity.Partner;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class PartnerEventPublisherTest {
    @Test
    void databaseFailureIsNotReportedAsAcceptedDelivery() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        var publisher = new PartnerEventPublisher(jdbc, new ObjectMapper().registerModule(new JavaTimeModule()));
        var event = new PartnerEventReceived(UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                UUID.randomUUID().toString(), Partner.FITBIT, "evt", "{}");
        when(jdbc.update(anyString(), any(Object[].class))).thenThrow(new IllegalStateException("database unavailable"));
        assertThatThrownBy(() -> publisher.publish(event)).hasMessage("database unavailable");
    }
}
