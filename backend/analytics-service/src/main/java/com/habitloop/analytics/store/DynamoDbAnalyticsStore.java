package com.habitloop.analytics.store;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactPutItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactWriteItemsEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * DynamoDB-backed {@link AnalyticsStore} (AWS). Active when habitloop.analytics.store=dynamodb.
 * Idempotency uses a conditional put on a PROCESSED# marker item.
 */
@Component
@ConditionalOnProperty(name = "habitloop.analytics.store", havingValue = "dynamodb")
public class DynamoDbAnalyticsStore implements AnalyticsStore {

    private final DynamoDbTable<AnalyticsItem> table;
    private final DynamoDbEnhancedClient client;

    public DynamoDbAnalyticsStore(DynamoDbTable<AnalyticsItem> table, DynamoDbEnhancedClient client) {
        this.table = table;
        this.client = client;
    }

    @Override
    public boolean apply(AnalyticsUpdate update) {
        String pk = update.pk();
        String markerKey = AnalyticsKeys.skProcessed(update.activityLogId());
        if (readStrong(pk, markerKey) != null) {
            return false;
        }
        AnalyticsItem daily = readStrong(pk, AnalyticsKeys.skDaily(update.date()));
        AnalyticsItem weekly = readStrong(pk, AnalyticsKeys.skWeekly(update.isoWeek()));
        AnalyticsItem pattern = readStrong(pk, AnalyticsKeys.SK_PATTERN);
        AnalyticsItem marker = new AnalyticsItem();
        marker.setPk(pk);
        marker.setSk(markerKey);
        marker.setUpdatedAt(OffsetDateTime.now().toString());
        var transaction = TransactWriteItemsEnhancedRequest.builder()
                .addPutItem(table, TransactPutItemEnhancedRequest.builder(AnalyticsItem.class)
                        .item(marker).conditionExpression(Expression.builder()
                                .expression("attribute_not_exists(pk)").build()).build())
                .addPutItem(table, conditionalPut(update.daily(daily), daily))
                .addPutItem(table, conditionalPut(update.weekly(weekly), weekly))
                .addPutItem(table, conditionalPut(update.pattern(pattern), pattern))
                .build();
        try {
            client.transactWriteItems(transaction);
            return true;
        } catch (TransactionCanceledException e) {
            if (readStrong(pk, markerKey) != null) {
                return false;
            }
            throw e;
        }
    }

    private AnalyticsItem readStrong(String pk, String sk) {
        Key key = Key.builder().partitionValue(pk).sortValue(sk).build();
        return table.getItem(request -> request.key(key).consistentRead(true));
    }

    private TransactPutItemEnhancedRequest<AnalyticsItem> conditionalPut(AnalyticsItem item, AnalyticsItem previous) {
        var expression = Expression.builder().expressionNames(Map.of("#revision", "revision"));
        if (previous == null || previous.getRevision() == null) {
            expression.expression("attribute_not_exists(#revision)");
        } else {
            expression.expression("#revision = :revision")
                    .expressionValues(Map.of(":revision", AttributeValue.builder().s(previous.getRevision()).build()));
        }
        return TransactPutItemEnhancedRequest.builder(AnalyticsItem.class)
                .item(item).conditionExpression(expression.build()).build();
    }

    @Override
    public Optional<AnalyticsItem> get(String pk, String sk) {
        return Optional.ofNullable(table.getItem(Key.builder().partitionValue(pk).sortValue(sk).build()));
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
