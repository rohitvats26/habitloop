package com.habitloop.analytics.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "habitloop.analytics")
public class AnalyticsProperties {

    /** Backing store: "memory" (dev default) or "dynamodb" (AWS). */
    private String store = "memory";

    /** DynamoDB table name (when store=dynamodb). */
    private String tableName = "habitloop-analytics";

    /** Optional DynamoDB endpoint override (e.g. http://localhost:8000 for DynamoDB Local). */
    private String dynamoEndpoint = "";

    /** AWS region for the DynamoDB client. */
    private String region = "ap-south-1";

    public String getStore() {
        return store;
    }

    public void setStore(String store) {
        this.store = store;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public String getDynamoEndpoint() {
        return dynamoEndpoint;
    }

    public void setDynamoEndpoint(String dynamoEndpoint) {
        this.dynamoEndpoint = dynamoEndpoint;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }
}
