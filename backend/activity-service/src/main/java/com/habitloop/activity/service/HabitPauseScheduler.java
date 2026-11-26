package com.habitloop.activity.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class HabitPauseScheduler {

    private final HabitService habits;

    public HabitPauseScheduler(HabitService habits) {
        this.habits = habits;
    }

    @Scheduled(cron = "0 5 0 * * *", zone = "Asia/Kolkata")
    public void resumeExpiredPauses() {
        habits.resumeExpiredPauses();
    }
}
