package com.habitloop.analytics.store;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsResponse;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DynamoDbAnalyticsStoreTest {
    private final DynamoDbClient raw = mock(DynamoDbClient.class);
    private final DynamoDbEnhancedClient client = DynamoDbEnhancedClient.builder().dynamoDbClient(raw).build();
    private final DynamoDbAnalyticsStore store = new DynamoDbAnalyticsStore(
            client.table("analytics", TableSchema.fromBean(AnalyticsItem.class)), client);
    private final AnalyticsUpdate update = new AnalyticsUpdate(
            "tenant", "user", "activity", LocalDate.of(2026, 11, 24), "2026-W48", "04", "TUE");

    @Test
    void markerAndAllAggregatesAreConditionallyWrittenInOneTransaction() {
        when(raw.getItem(any(GetItemRequest.class))).thenReturn(GetItemResponse.builder().build());
        when(raw.transactWriteItems(any(TransactWriteItemsRequest.class)))
                .thenReturn(TransactWriteItemsResponse.builder().build());

        assertThat(store.apply(update)).isTrue();

        var captor = ArgumentCaptor.forClass(TransactWriteItemsRequest.class);
        verify(raw).transactWriteItems(captor.capture());
        var writes = captor.getValue().transactItems();
        assertThat(writes).hasSize(4);
        assertThat(writes).allSatisfy(write -> assertThat(write.put().conditionExpression()).isNotBlank());
        assertThat(writes.get(0).put().item().get("sk").s()).isEqualTo("PROCESSED#activity");
        assertThat(writes.get(1).put().item().get("completions").n()).isEqualTo("1");
    }

    @Test
    void transactionConflictWithoutMarkerIsReportedForRetry() {
        when(raw.getItem(any(GetItemRequest.class))).thenReturn(GetItemResponse.builder().build());
        when(raw.transactWriteItems(any(TransactWriteItemsRequest.class)))
                .thenThrow(TransactionCanceledException.builder().message("conflict").build())
                .thenReturn(TransactWriteItemsResponse.builder().build());

        assertThatThrownBy(() -> store.apply(update)).isInstanceOf(TransactionCanceledException.class);
        assertThat(store.apply(update)).isTrue();
    }

    @Test
    void existingAggregateRequiresMatchingRevision() {
        when(raw.getItem(any(GetItemRequest.class))).thenAnswer(invocation -> {
            GetItemRequest request = invocation.getArgument(0);
            if (!request.key().get("sk").s().startsWith("STAT#DAILY#")) {
                return GetItemResponse.builder().build();
            }
            return GetItemResponse.builder().item(Map.of(
                    "pk", request.key().get("pk"), "sk", request.key().get("sk"),
                    "revision", AttributeValue.builder().s("old-revision").build(),
                    "completions", AttributeValue.builder().n("4").build())).build();
        });
        when(raw.transactWriteItems(any(TransactWriteItemsRequest.class)))
                .thenReturn(TransactWriteItemsResponse.builder().build());
        assertThat(store.apply(update)).isTrue();
        var captor = ArgumentCaptor.forClass(TransactWriteItemsRequest.class);
        verify(raw).transactWriteItems(captor.capture());
        var daily = captor.getValue().transactItems().get(1).put();
        assertThat(daily.conditionExpression()).contains("= :revision");
        assertThat(daily.expressionAttributeValues().get(":revision").s()).isEqualTo("old-revision");
        assertThat(daily.item().get("completions").n()).isEqualTo("5");
    }

    @Test
    void previouslyProcessedEventDoesNotWriteAgain() {
        when(raw.getItem(any(GetItemRequest.class))).thenAnswer(invocation -> {
            GetItemRequest request = invocation.getArgument(0);
            return GetItemResponse.builder().item(request.key()).build();
        });
        assertThat(store.apply(update)).isFalse();
        verify(raw, never()).transactWriteItems(any(TransactWriteItemsRequest.class));
    }
}
