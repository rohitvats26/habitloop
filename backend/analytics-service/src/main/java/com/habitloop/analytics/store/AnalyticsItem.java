package com.habitloop.analytics.store;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

import java.util.HashMap;
import java.util.Map;

/**
 * Single-table analytics item (see docs/04 §5). PK scopes to tenant+user; SK distinguishes daily/weekly
 * stats, per-challenge rollups and the pattern summary. GSI1 supports tenant-wide cohort queries by date.
 */
@DynamoDbBean
public class AnalyticsItem {

    public static final String GSI1 = "GSI1";

    private String pk;
    private String sk;
    private int completions;
    private boolean targetMet;
    private int currentStreak;
    private Map<String, Integer> byHour = new HashMap<>();
    private Map<String, Integer> byWeekday = new HashMap<>();
    private String updatedAt;
    private String revision;
    private String gsi1pk;
    private String gsi1sk;

    @DynamoDbPartitionKey
    public String getPk() {
        return pk;
    }

    public void setPk(String pk) {
        this.pk = pk;
    }

    @DynamoDbSortKey
    public String getSk() {
        return sk;
    }

    public void setSk(String sk) {
        this.sk = sk;
    }

    public int getCompletions() {
        return completions;
    }

    public void setCompletions(int completions) {
        this.completions = completions;
    }

    public boolean isTargetMet() {
        return targetMet;
    }

    public void setTargetMet(boolean targetMet) {
        this.targetMet = targetMet;
    }

    public int getCurrentStreak() {
        return currentStreak;
    }

    public void setCurrentStreak(int currentStreak) {
        this.currentStreak = currentStreak;
    }

    public Map<String, Integer> getByHour() {
        return byHour;
    }

    public void setByHour(Map<String, Integer> byHour) {
        this.byHour = byHour;
    }

    public Map<String, Integer> getByWeekday() {
        return byWeekday;
    }

    public void setByWeekday(Map<String, Integer> byWeekday) {
        this.byWeekday = byWeekday;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getRevision() {
        return revision;
    }

    public void setRevision(String revision) {
        this.revision = revision;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = GSI1)
    public String getGsi1pk() {
        return gsi1pk;
    }

    public void setGsi1pk(String gsi1pk) {
        this.gsi1pk = gsi1pk;
    }

    @DynamoDbSecondarySortKey(indexNames = GSI1)
    public String getGsi1sk() {
        return gsi1sk;
    }

    public void setGsi1sk(String gsi1sk) {
        this.gsi1sk = gsi1sk;
    }

    /** Increments a key in a counter map. */
    public static void bump(Map<String, Integer> map, String key) {
        map.merge(key, 1, Integer::sum);
    }

    public AnalyticsItem copy() {
        AnalyticsItem copy = new AnalyticsItem();
        copy.pk = pk;
        copy.sk = sk;
        copy.completions = completions;
        copy.targetMet = targetMet;
        copy.currentStreak = currentStreak;
        copy.byHour = new HashMap<>(byHour);
        copy.byWeekday = new HashMap<>(byWeekday);
        copy.updatedAt = updatedAt;
        copy.revision = revision;
        copy.gsi1pk = gsi1pk;
        copy.gsi1sk = gsi1sk;
        return copy;
    }
}
