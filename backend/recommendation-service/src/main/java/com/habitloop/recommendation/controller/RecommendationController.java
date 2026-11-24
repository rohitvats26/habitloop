package com.habitloop.recommendation.controller;

import com.habitloop.common.security.SecurityUtils;
import com.habitloop.recommendation.dto.RecommendationResponse;
import com.habitloop.recommendation.service.RecommendationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/recommendations")
@Tag(name = "Recommendations")
public class RecommendationController {

    private final RecommendationService recommendationService;

    public RecommendationController(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @GetMapping
    @Operation(summary = "Actionable recommendations for the current user")
    public RecommendationResponse recommendations() {
        return RecommendationResponse.of(recommendationService.forUser(SecurityUtils.currentUserId()));
    }

    @GetMapping("/habits/{id}")
    @Operation(summary = "Per-habit frequency/timing suggestion")
    public RecommendationResponse forHabit(@PathVariable UUID id) {
        return RecommendationResponse.of(recommendationService.forHabit(id.toString()));
    }
}
