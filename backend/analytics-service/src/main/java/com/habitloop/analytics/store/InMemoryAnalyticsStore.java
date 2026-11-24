package com.habitloop.analytics.store;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dev/local {@link AnalyticsStore} backed by maps, so the service runs end-to-end without AWS. Default
 * (habitloop.analytics.store=memory). Not durable or shared across instances — DynamoDB is used in AWS.
 */
@Component
@ConditionalOnProperty(name = "habitloop.analytics.store", havingValue = "memory", matchIfMissing = true)
public class InMemoryAnalyticsStore implements AnalyticsStore {

    private final Map<String, AnalyticsItem> items = new ConcurrentHashMap<>();
    private final Map<String, Boolean> processed = new ConcurrentHashMap<>();

    private static String key(String pk, String sk) {
        return pk + "||" + sk;
    }

    @Override
    public boolean markProcessedIfAbsent(String pk, String activityLogId) {
        return processed.putIfAbsent(key(pk, activityLogId), Boolean.TRUE) == null;
    }

    @Override
    public Optional<AnalyticsItem> get(String pk, String sk) {
        return Optional.ofNullable(items.get(key(pk, sk)));
    }

    @Override
    public void put(AnalyticsItem item) {
        items.put(key(item.getPk(), item.getSk()), item);
    }

    @Override
    public List<AnalyticsItem> queryPrefix(String pk, String skPrefix) {
        List<AnalyticsItem> result = new ArrayList<>();
        for (AnalyticsItem item : items.values()) {
            if (item.getPk().equals(pk) && item.getSk().startsWith(skPrefix)) {
                result.add(item);
            }
        }
        return result;
    }

    @Override
    public List<AnalyticsItem> queryCohort(String gsi1pk) {
        List<AnalyticsItem> result = new ArrayList<>();
        for (AnalyticsItem item : items.values()) {
            if (gsi1pk.equals(item.getGsi1pk())) {
                result.add(item);
            }
        }
        return result;
    }
}
