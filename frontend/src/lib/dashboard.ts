import type { ActivityLog, Habit } from './types';

export function completedHabitIdsForDay(activity: ActivityLog[]): string[] {
  return [
    ...new Set(
      activity
        .filter(
          (item): item is ActivityLog & { habitId: string } =>
            item.activityType === 'HABIT_DONE' && item.source === 'MANUAL' && typeof item.habitId === 'string'
        )
        .map((item) => item.habitId)
    )
  ];
}

export function habitDayKind(habit: Habit, date: string): 'paused' | 'scheduled' | 'rest' | 'flexible' {
  if (!habit.active) return 'paused';
  const days = habit.schedule.daysOfWeek;
  if (Array.isArray(days) && days.length > 0) {
    const weekday = new Date(`${date}T00:00:00Z`).getUTCDay() || 7;
    return days.includes(weekday) ? 'scheduled' : 'rest';
  }
  return habit.frequency === 'DAILY' ? 'scheduled' : 'flexible';
}
