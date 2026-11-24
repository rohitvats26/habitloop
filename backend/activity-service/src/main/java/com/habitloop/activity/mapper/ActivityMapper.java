package com.habitloop.activity.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.habitloop.activity.dto.ActivityLogResponse;
import com.habitloop.activity.dto.HabitResponse;
import com.habitloop.activity.dto.StreakDto;
import com.habitloop.activity.entity.ActivityLog;
import com.habitloop.activity.entity.Habit;
import com.habitloop.activity.entity.HabitStreak;
import com.habitloop.common.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ActivityMapper {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    public ActivityMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public HabitResponse toHabitResponse(Habit habit, HabitStreak streak) {
        StreakDto streakDto = streak == null
                ? StreakDto.zero()
                : new StreakDto(streak.getCurrentStreak(), streak.getLongestStreak());
        return new HabitResponse(
                habit.getId().toString(),
                habit.getName(),
                habit.getDescription(),
                habit.getFrequency(),
                readSchedule(habit.getSchedule()),
                habit.getTargetPerPeriod(),
                habit.isActive(),
                streakDto);
    }

    public ActivityLogResponse toActivityLogResponse(ActivityLog log) {
        return new ActivityLogResponse(
                log.getId().toString(),
                log.getHabitId() == null ? null : log.getHabitId().toString(),
                log.getActivityType(),
                log.getValue(),
                log.getUnit(),
                log.getOccurredAt(),
                log.getSource());
    }

    public String writeSchedule(Map<String, Object> schedule) {
        if (schedule == null || schedule.isEmpty()) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(schedule);
        } catch (Exception e) {
            throw new BusinessException("Invalid schedule payload");
        }
    }

    public Map<String, Object> readSchedule(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (Exception e) {
            return Map.of();
        }
    }
}
