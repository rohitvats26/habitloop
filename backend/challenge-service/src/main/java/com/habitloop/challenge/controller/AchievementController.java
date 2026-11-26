package com.habitloop.challenge.controller;

import com.habitloop.challenge.dto.AchievementResponse;
import com.habitloop.challenge.service.AchievementService;
import com.habitloop.common.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/achievements")
@Tag(name = "Achievements")
public class AchievementController {

    private final AchievementService achievements;

    public AchievementController(AchievementService achievements) {
        this.achievements = achievements;
    }

    @GetMapping
    @Operation(summary = "List achievements earned by the current user")
    public PageResponse<AchievementResponse> mine(@PageableDefault(size = 50) Pageable pageable) {
        return achievements.mine(pageable);
    }
}
