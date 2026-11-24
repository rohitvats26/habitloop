package com.habitloop.analytics.store;

import java.util.List;
import java.util.Optional;

/** Abstraction over the analytics aggregate store (in-memory for dev, DynamoDB for AWS). */
public interface AnalyticsStore {

    /** Atomically marks an activity log as processed; returns true only the first time (idempotency). */
    boolean markProcessedIfAbsent(String pk, String activityLogId);

    Optional<AnalyticsItem> get(String pk, String sk);

    void put(AnalyticsItem item);

    /** Items under one partition whose sort key begins with {@code skPrefix}. */
    List<AnalyticsItem> queryPrefix(String pk, String skPrefix);

    /** Cohort query over GSI1: all users' items for a given {@code gsi1pk} (tenant+date). */
    List<AnalyticsItem> queryCohort(String gsi1pk);
}
