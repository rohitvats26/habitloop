package com.habitloop.activity.event;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ActivityOutboxRelayTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final TransactionTemplate transactions = mock(TransactionTemplate.class);
    private final ActivityEventTransport transport = mock(ActivityEventTransport.class);
    private final UUID id = UUID.randomUUID();
    private final List<String> updates = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        ResultSet row = mock(ResultSet.class);
        when(row.getObject("id", UUID.class)).thenReturn(id);
        when(row.getString("partition_key")).thenReturn("tenant:user");
        when(row.getString("envelope")).thenReturn("payload");
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Object>>any()))
                .thenAnswer(invocation -> List.of(invocation.<RowMapper<Object>>getArgument(1).mapRow(row, 0)))
                .thenReturn(List.of());
        when(transactions.execute(any())).thenAnswer(invocation ->
                invocation.<TransactionCallback<?>>getArgument(0).doInTransaction(null));
        doAnswer(invocation -> {
            updates.add(invocation.getArgument(0));
            return 1;
        }).when(jdbc).update(anyString(), any(Object[].class));
    }

    @Test
    void marksPublishedOnlyAfterSuccessfulDelivery() {
        new ActivityOutboxRelay(jdbc, transactions, transport).relay();
        verify(transport).publish("tenant:user", "payload");
        assertThat(updates).singleElement().asString().contains("published_at = CURRENT_TIMESTAMP");
    }

    @Test
    void failedDeliveryIsRecordedForRetry() {
        doThrow(new IllegalStateException("broker unavailable")).when(transport).publish("tenant:user", "payload");
        new ActivityOutboxRelay(jdbc, transactions, transport).relay();
        assertThat(updates).singleElement().asString().contains("next_attempt_at").doesNotContain("published_at =");
    }
}
