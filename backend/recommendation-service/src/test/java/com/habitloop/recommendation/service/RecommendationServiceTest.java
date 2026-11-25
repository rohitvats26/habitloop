package com.habitloop.recommendation.service;

import com.habitloop.recommendation.client.AnalyticsClient;
import com.habitloop.recommendation.client.PatternDto;
import com.habitloop.recommendation.client.SummaryDto;
import com.habitloop.recommendation.dto.RecommendationItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

    @Mock AnalyticsClient analyticsClient;

    RecommendationService service;

    @BeforeEach
    void setUp() {
        service = new RecommendationService(analyticsClient);
        lenient().when(analyticsClient.getPattern()).thenReturn(new PatternDto(Map.of("09", 4), Map.of("TUE", 3)));
        lenient().when(analyticsClient.getSummary()).thenReturn(new SummaryDto(1, 4, List.of(
                new SummaryDto.WeekPoint("2026-W46", 4))));
    }

    @Test
    void forUser_cachesResult_untilEvicted() {
        List<RecommendationItem> first = service.forUser("u1");
        List<RecommendationItem> second = service.forUser("u1");

        assertThat(first).isNotEmpty();
        assertThat(second).isEqualTo(first);
        verify(analyticsClient, times(1)).getPattern(); // served from cache the 2nd time

        service.evict("u1");
        service.forUser("u1");
        verify(analyticsClient, times(2)).getPattern(); // recomputed after eviction
    }

    @Test
    void forHabit_tagsRecommendationsWithHabitId() {
        List<RecommendationItem> items = service.forHabit("habit-9");
        assertThat(items).anySatisfy(i -> assertThat(i.habitId()).isEqualTo("habit-9"));
    }
}
