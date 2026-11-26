package com.habitloop.challenge.mapper;

import com.habitloop.challenge.dto.AchievementResponse;
import com.habitloop.challenge.entity.AchievementAward;
import org.springframework.stereotype.Component;

@Component
public class AchievementMapper {

    public AchievementResponse toResponse(AchievementAward award) {
        return new AchievementResponse(award.getId().toString(), award.getCode(), award.getTitle(),
                award.getDescription(), award.getSourceKey(), award.getAwardedAt());
    }
}
