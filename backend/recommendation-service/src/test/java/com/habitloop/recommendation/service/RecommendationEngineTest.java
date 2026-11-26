package com.habitloop.recommendation.service;

import com.habitloop.recommendation.client.PatternDto;
import com.habitloop.recommendation.client.SummaryDto;
import com.habitloop.recommendation.dto.RecommendationItem;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RecommendationEngineTest {

    @Test
    void generates_timingFrequencyAndConsistency() {
        PatternDto pattern = new PatternDto(
                Map.of("07", 5, "18", 9),
                Map.of("MON", 4, "SAT", 1));
        SummaryDto summary = new SummaryDto(2, 5, List.of(
                new SummaryDto.WeekPoint("2026-W45", 3),
                new SummaryDto.WeekPoint("2026-W46", 5)));

        List<RecommendationItem> items = RecommendationEngine.generate(pattern, summary, null);

        assertThat(items).extracting(RecommendationItem::type)
                .containsExactlyInAnyOrder("TIMING", "FREQUENCY", "CONSISTENCY");

        RecommendationItem timing = items.stream().filter(i -> i.type().equals("TIMING")).findFirst().orElseThrow();
        assertThat(timing.action()).containsEntry("suggestedTime", "18:00"); // peak hour
        assertThat(timing.action()).containsEntry("label", "Set target time 18:00");

        RecommendationItem freq = items.stream().filter(i -> i.type().equals("FREQUENCY")).findFirst().orElseThrow();
        assertThat(freq.action()).containsEntry("suggestedPerWeek", 4); // round(avg of 3,5)
    }

    @Test
    void timingItem_carriesHabitIdWhenProvided() {
        PatternDto pattern = new PatternDto(Map.of("06", 3), Map.of());
        List<RecommendationItem> items = RecommendationEngine.generate(pattern, SummaryDto.empty(), "habit-1");
        assertThat(items).anySatisfy(i -> {
            assertThat(i.type()).isEqualTo("TIMING");
            assertThat(i.habitId()).isEqualTo("habit-1");
        });
    }

    @Test
    void emptyInputs_produceNoRecommendations() {
        assertThat(RecommendationEngine.generate(PatternDto.empty(), SummaryDto.empty(), null)).isEmpty();
    }
}
