package com.habitloop.integration.event;

import com.habitloop.common.event.eventbridge.EventBridgePublisher;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PartnerOutboxRelayTest {
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void kafkaDeliveryIsMarkedPublishedOnlyAfterAcknowledgement(boolean succeeds) throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        TransactionTemplate transactions = mock(TransactionTemplate.class);
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<EventBridgePublisher> bridge = mock(ObjectProvider.class);
        ResultSet row = mock(ResultSet.class);
        when(row.getObject("id", UUID.class)).thenReturn(UUID.randomUUID());
        when(row.getString("partition_key")).thenReturn("key");
        when(row.getString("envelope")).thenReturn("payload");
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Object>>any()))
                .thenAnswer(i -> List.of(i.<RowMapper<Object>>getArgument(1).mapRow(row, 0)))
                .thenReturn(List.of());
        when(transactions.execute(any())).thenAnswer(i -> i.<TransactionCallback<?>>getArgument(0).doInTransaction(null));
        CompletableFuture<SendResult<String, String>> result = succeeds
                ? CompletableFuture.completedFuture(null)
                : CompletableFuture.failedFuture(new IllegalStateException("broker unavailable"));
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(result);
        var updates = new ArrayList<String>();
        when(jdbc.update(anyString(), any(Object[].class))).thenAnswer(i -> {
            updates.add(i.getArgument(0));
            return 1;
        });

        new PartnerOutboxRelay(jdbc, transactions, kafka, bridge, "kafka").relay();

        if (succeeds) {
            assertThat(updates).singleElement().asString().contains("published_at =").doesNotContain("next_attempt_at =");
        } else {
            assertThat(updates).singleElement().asString().contains("next_attempt_at =").doesNotContain("published_at =");
        }
    }
}
