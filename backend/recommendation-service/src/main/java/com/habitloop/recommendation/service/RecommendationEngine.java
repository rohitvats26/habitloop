package com.habitloop.recommendation.service;

import com.habitloop.recommendation.client.PatternDto;
import com.habitloop.recommendation.client.SummaryDto;
import com.habitloop.recommendation.dto.RecommendationItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Pure, rule-based recommendation logic (AS-10: heuristic, not ML). Derives timing, frequency and
 * consistency suggestions from the user's activity pattern and weekly trend. Isolated for unit testing.
 */
public final class RecommendationEngine {

    private static final Map<String, String> WEEKDAY_FULL = Map.of(
            "MON", "Monday", "TUE", "Tuesday", "WED", "Wednesday", "THU", "Thursday",
            "FRI", "Friday", "SAT", "Saturday", "SUN", "Sunday");

    private RecommendationEngine() {
    }

    public static List<RecommendationItem> generate(PatternDto pattern, SummaryDto summary, String habitId) {
        List<RecommendationItem> items = new ArrayList<>();
        timingRecommendation(pattern, habitId).ifPresent(items::add);
        frequencyRecommendation(summary, habitId).ifPresent(items::add);
        consistencyRecommendation(pattern).ifPresent(items::add);
        return items;
    }

    private static java.util.Optional<RecommendationItem> timingRecommendation(PatternDto pattern, String habitId) {
        if (pattern == null || pattern.byHour() == null || pattern.byHour().isEmpty()) {
            return java.util.Optional.empty();
        }
        Map.Entry<String, Integer> peak = maxEntry(pattern.byHour());
        int total = pattern.byHour().values().stream().mapToInt(Integer::intValue).sum();
        double confidence = total == 0 ? 0 : (double) peak.getValue() / total;
        String time = peak.getKey() + ":00";
        return java.util.Optional.of(new RecommendationItem(
                "TIMING", habitId, "Lock in your best time",
                "You complete most activities around " + time + ". Consider setting this as your target time.",
                Map.of("label", "Set target time " + time, "suggestedTime", time),
                round2(confidence)));
    }

    private static java.util.Optional<RecommendationItem> frequencyRecommendation(SummaryDto summary, String habitId) {
        if (summary == null || summary.trend() == null || summary.trend().isEmpty()) {
            return java.util.Optional.empty();
        }
        double avg = summary.trend().stream().mapToInt(SummaryDto.WeekPoint::completions).average().orElse(0);
        int perWeek = clamp((int) Math.round(avg), 1, 7);
        return java.util.Optional.of(new RecommendationItem(
                "FREQUENCY", habitId, "Aim for " + perWeek + "×/week",
                "Over recent weeks you average " + Math.round(avg)
                        + " completions/week. Targeting " + perWeek + "×/week fits your rhythm.",
                Map.of("label", "Set target " + perWeek, "suggestedPerWeek", perWeek),
                0.7));
    }

    private static java.util.Optional<RecommendationItem> consistencyRecommendation(PatternDto pattern) {
        if (pattern == null || pattern.byWeekday() == null || pattern.byWeekday().isEmpty()) {
            return java.util.Optional.empty();
        }
        Map.Entry<String, Integer> peak = maxEntry(pattern.byWeekday());
        String day = WEEKDAY_FULL.getOrDefault(peak.getKey(), peak.getKey());
        return java.util.Optional.of(new RecommendationItem(
                "CONSISTENCY", null, "Your strongest day is " + day,
                "You're most consistent on " + day + ". Consider anchoring new habits there.",
                Map.of("label", "Plan around " + day, "suggestedWeekday", peak.getKey()),
                0.6));
    }

    private static Map.Entry<String, Integer> maxEntry(Map<String, Integer> map) {
        return map.entrySet().stream().max(Map.Entry.comparingByValue()).orElseThrow();
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
