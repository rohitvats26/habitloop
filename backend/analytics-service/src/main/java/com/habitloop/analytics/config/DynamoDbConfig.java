package com.habitloop.analytics.config;

import com.habitloop.analytics.store.AnalyticsItem;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.net.URI;

/** Builds the DynamoDB enhanced client and table binding. Active only when store=dynamodb. */
@Configuration
@ConditionalOnProperty(name = "habitloop.analytics.store", havingValue = "dynamodb")
public class DynamoDbConfig {

    @Bean
    public DynamoDbClient dynamoDbClient(AnalyticsProperties props) {
        var builder = DynamoDbClient.builder().region(Region.of(props.getRegion()));
        if (StringUtils.hasText(props.getDynamoEndpoint())) {
            // DynamoDB Local: endpoint override + dummy static credentials.
            builder.endpointOverride(URI.create(props.getDynamoEndpoint()))
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create("local", "local")));
        } else {
            builder.credentialsProvider(DefaultCredentialsProvider.create());
        }
        return builder.build();
    }

    @Bean
    public DynamoDbEnhancedClient dynamoDbEnhancedClient(DynamoDbClient client) {
        return DynamoDbEnhancedClient.builder().dynamoDbClient(client).build();
    }

    @Bean
    public DynamoDbTable<AnalyticsItem> analyticsTable(DynamoDbEnhancedClient enhancedClient,
                                                       AnalyticsProperties props) {
        return enhancedClient.table(props.getTableName(), TableSchema.fromBean(AnalyticsItem.class));
    }
}
