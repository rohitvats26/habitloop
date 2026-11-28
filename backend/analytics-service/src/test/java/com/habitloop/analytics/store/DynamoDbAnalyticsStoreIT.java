package com.habitloop.analytics.store;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;

import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "DYNAMODB_LOCAL_ENDPOINT", matches = "http://127\\.0\\.0\\.1:[0-9]+")
class DynamoDbAnalyticsStoreIT {
    @Test
    void concurrentEventsAndDuplicatesKeepAllAggregatesConsistent() throws Exception {
        String tableName = "habitloop-test-" + UUID.randomUUID();
        try (var raw = DynamoDbClient.builder()
                .endpointOverride(URI.create(System.getenv("DYNAMODB_LOCAL_ENDPOINT")))
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("local", "local")))
                .build()) {
            raw.createTable(request -> request.tableName(tableName).billingMode(BillingMode.PAY_PER_REQUEST)
                    .attributeDefinitions(
                            AttributeDefinition.builder().attributeName("pk").attributeType(ScalarAttributeType.S).build(),
                            AttributeDefinition.builder().attributeName("sk").attributeType(ScalarAttributeType.S).build())
                    .keySchema(
                            KeySchemaElement.builder().attributeName("pk").keyType(KeyType.HASH).build(),
                            KeySchemaElement.builder().attributeName("sk").keyType(KeyType.RANGE).build()));
            try {
                raw.waiter().waitUntilTableExists(request -> request.tableName(tableName));
                var client = DynamoDbEnhancedClient.builder().dynamoDbClient(raw).build();
                var table = client.table(tableName, TableSchema.fromBean(AnalyticsItem.class));
                var store = new DynamoDbAnalyticsStore(table, client);
                var executor = Executors.newFixedThreadPool(5);
                var start = new CountDownLatch(1);
                String repeated = UUID.randomUUID().toString();
                try {
                    var tasks = new ArrayList<java.util.concurrent.Future<?>>();
                    for (int i = 0; i < 10; i++) {
                        String id = i < 5 ? repeated : UUID.randomUUID().toString();
                        tasks.add(executor.submit(() -> {
                            start.await();
                            var update = new AnalyticsUpdate("tenant", "user", id,
                                    LocalDate.of(2026, 11, 24), "2026-W48", "04", "TUE");
                            for (int attempt = 0; ; attempt++) {
                                try {
                                    store.apply(update);
                                    return null;
                                } catch (TransactionCanceledException conflict) {
                                    if (attempt >= 99) {
                                        throw conflict;
                                    }
                                    Thread.sleep(10);
                                }
                            }
                        }));
                    }
                    start.countDown();
                    for (var task : tasks) {
                        task.get(30, TimeUnit.SECONDS);
                    }
                } finally {
                    executor.shutdownNow();
                }
                String pk = AnalyticsKeys.pk("tenant", "user");
                var daily = table.getItem(request -> request.key(Key.builder().partitionValue(pk)
                        .sortValue(AnalyticsKeys.skDaily(LocalDate.of(2026, 11, 24))).build()).consistentRead(true));
                var weekly = table.getItem(request -> request.key(Key.builder().partitionValue(pk)
                        .sortValue(AnalyticsKeys.skWeekly("2026-W48")).build()).consistentRead(true));
                var pattern = table.getItem(request -> request.key(Key.builder().partitionValue(pk)
                        .sortValue(AnalyticsKeys.SK_PATTERN).build()).consistentRead(true));
                assertThat(daily.getCompletions()).isEqualTo(6);
                assertThat(weekly.getCompletions()).isEqualTo(6);
                assertThat(pattern.getByHour()).containsEntry("04", 6);
            } finally {
                raw.deleteTable(request -> request.tableName(tableName));
            }
        }
    }
}
