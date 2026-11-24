package com.habitloop.recommendation.service;

import com.habitloop.recommendation.client.AnalyticsClient;
import com.habitloop.recommendation.client.PatternDto;
import com.habitloop.recommendation.client.SummaryDto;
import com.habitloop.recommendation.dto.RecommendationItem;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Produces recommendations by reading analytics aggregates and running the heuristic engine. Results are
 * cached per user with a short TTL (read-mostly, AS-10); an {@code analytics.updated} event evicts a
 * user's entry so new activity reflects promptly.
 */
@Service
public class RecommendationService {

    private static final long TTL_MILLIS = 300_000; // 5 minutes

    private record Cached(List<RecommendationItem> items, long expiresAt) {
    }

    private final ConcurrentHashMap<String, Cached> cache = new ConcurrentHashMap<>();
    private final AnalyticsClient analyticsClient;

    public RecommendationService(AnalyticsClient analyticsClient) {
        this.analyticsClient = analyticsClient;
    }

    public List<RecommendationItem> forUser(String userId) {
        Cached cached = cache.get(userId);
        if (cached != null && System.currentTimeMillis() < cached.expiresAt()) {
            return cached.items();
        }
        List<RecommendationItem> items = compute(null);
        cache.put(userId, new Cached(items, System.currentTimeMillis() + TTL_MILLIS));
        return items;
    }

    /** Per-habit suggestion (pattern is user-level; tagged with the habit id). Not cached. */
    public List<RecommendationItem> forHabit(String habitId) {
        return compute(habitId);
    }

    public void evict(String userId) {
        cache.remove(userId);
    }

    private List<RecommendationItem> compute(String habitId) {
        PatternDto pattern = analyticsClient.getPattern();
        SummaryDto summary = analyticsClient.getSummary();
        return RecommendationEngine.generate(pattern, summary, habitId);
    }
}
