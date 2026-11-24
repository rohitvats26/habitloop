package com.habitloop.analytics.store;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.PutItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * DynamoDB-backed {@link AnalyticsStore} (AWS). Active when habitloop.analytics.store=dynamodb.
 * Idempotency uses a conditional put on a PROCESSED# marker item.
 */
@Component
@ConditionalOnProperty(name = "habitloop.analytics.store", havingValue = "dynamodb")
public class DynamoDbAnalyticsStore implements AnalyticsStore {

    private final DynamoDbTable<AnalyticsItem> table;

    public DynamoDbAnalyticsStore(DynamoDbTable<AnalyticsItem> table) {
        this.table = table;
    }

    @Override
    public boolean markProcessedIfAbsent(String pk, String activityLogId) {
        AnalyticsItem marker = new AnalyticsItem();
        marker.setPk(pk);
        marker.setSk(AnalyticsKeys.skProcessed(activityLogId));
        marker.setUpdatedAt(OffsetDateTime.now().toString());
        try {
            table.putItem(PutItemEnhancedRequest.builder(AnalyticsItem.class)
                    .item(marker)
                    .conditionExpression(Expression.builder()
                            .expression("attribute_not_exists(pk)")
                            .build())
                    .build());
            return true;
        } catch (ConditionalCheckFailedException alreadyProcessed) {
            return false;
        }
    }

    @Override
    public Optional<AnalyticsItem> get(String pk, String sk) {
        return Optional.ofNullable(table.getItem(Key.builder().partitionValue(pk).sortValue(sk).build()));
    }

    @Override
    public void put(AnalyticsItem item) {
        table.putItem(item);
    }

    @Override
    public List<AnalyticsItem> queryPrefix(String pk, String skPrefix) {
        return table.query(QueryConditional.sortBeginsWith(
                        Key.builder().partitionValue(pk).sortValue(skPrefix).build()))
                .items().stream().toList();
    }

    @Override
    public List<AnalyticsItem> queryCohort(String gsi1pk) {
        return table.index(AnalyticsItem.GSI1)
                .query(QueryConditional.keyEqualTo(Key.builder().partitionValue(gsi1pk).build()))
                .stream()
                .flatMap(page -> page.items().stream())
                .toList();
    }
}
