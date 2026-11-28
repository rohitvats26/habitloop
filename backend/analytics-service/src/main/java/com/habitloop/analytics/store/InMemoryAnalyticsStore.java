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
    public synchronized boolean apply(AnalyticsUpdate update) {
        String pk = update.pk();
        String marker = key(pk, update.activityLogId());
        if (processed.containsKey(marker)) {
            return false;
        }
        String dailyKey = key(pk, AnalyticsKeys.skDaily(update.date()));
        String weeklyKey = key(pk, AnalyticsKeys.skWeekly(update.isoWeek()));
        String patternKey = key(pk, AnalyticsKeys.SK_PATTERN);
        AnalyticsItem daily = update.daily(items.get(dailyKey));
        AnalyticsItem weekly = update.weekly(items.get(weeklyKey));
        AnalyticsItem pattern = update.pattern(items.get(patternKey));
        items.put(dailyKey, daily);
        items.put(weeklyKey, weekly);
        items.put(patternKey, pattern);
        processed.put(marker, Boolean.TRUE);
        return true;
    }

    @Override
    public synchronized Optional<AnalyticsItem> get(String pk, String sk) {
        return Optional.ofNullable(items.get(key(pk, sk)));
    }

    @Override
    public synchronized List<AnalyticsItem> queryPrefix(String pk, String skPrefix) {
        List<AnalyticsItem> result = new ArrayList<>();
        for (AnalyticsItem item : items.values()) {
            if (item.getPk().equals(pk) && item.getSk().startsWith(skPrefix)) {
                result.add(item);
            }
        }
        return result;
    }

    @Override
    public synchronized List<AnalyticsItem> queryCohort(String gsi1pk) {
        List<AnalyticsItem> result = new ArrayList<>();
        for (AnalyticsItem item : items.values()) {
            if (gsi1pk.equals(item.getGsi1pk())) {
                result.add(item);
            }
        }
        return result;
    }
}
