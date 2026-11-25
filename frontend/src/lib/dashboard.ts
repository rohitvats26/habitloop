import type { ActivityLog } from './types';

export function completedHabitIdsForDay(activity: ActivityLog[]): string[] {
  return [
    ...new Set(
      activity
        .filter(
          (item): item is ActivityLog & { habitId: string } =>
            item.activityType === 'HABIT_DONE' && typeof item.habitId === 'string'
        )
        .map((item) => item.habitId)
    )
  ];
}
