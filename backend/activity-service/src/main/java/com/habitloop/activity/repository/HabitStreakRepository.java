package com.habitloop.activity.repository;

import com.habitloop.activity.entity.HabitStreak;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface HabitStreakRepository extends JpaRepository<HabitStreak, UUID> {

    Optional<HabitStreak> findByHabitId(UUID habitId);
}
