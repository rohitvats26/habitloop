import { describe, expect, it } from 'vitest';
import { completedHabitIdsForDay, habitDayKind } from './dashboard';
import type { ActivityLog, Habit } from './types';

describe('completedHabitIdsForDay', () => {
  it('counts unique habit completions and ignores other activity types', () => {
    const activity: ActivityLog[] = [
      { id: '1', habitId: 'habit-1', activityType: 'HABIT_DONE', occurredAt: '2026-11-26T08:00:00+05:30', source: 'MANUAL' },
      { id: '2', habitId: 'habit-1', activityType: 'HABIT_DONE', occurredAt: '2026-11-26T08:00:00+05:30', source: 'MANUAL' },
      { id: '3', habitId: 'habit-2', activityType: 'WALKING', occurredAt: '2026-11-26T09:00:00+05:30', source: 'WEARABLE' },
      { id: '4', activityType: 'HABIT_DONE', occurredAt: '2026-11-26T10:00:00+05:30', source: 'MANUAL' },
      { id: '5', habitId: 'habit-3', activityType: 'HABIT_DONE', occurredAt: '2026-11-26T10:00:00+05:30', source: 'WEARABLE' }
    ];

    expect(completedHabitIdsForDay(activity)).toEqual(['habit-1']);
  });

  it('distinguishes scheduled days, rest days, flexible targets and pauses', () => {
    const habit: Habit = {
      id: '1', name: 'Walk', active: true, frequency: 'CUSTOM',
      schedule: { daysOfWeek: [1, 3, 5] }, targetPerPeriod: 3,
      weeklyProgress: { completed: 0, expected: 0, consistencyPercent: 0 },
      streak: { current: 0, longest: 0 }
    };
    expect(habitDayKind(habit, '2026-11-23')).toBe('scheduled');
    expect(habitDayKind(habit, '2026-11-24')).toBe('rest');
    expect(habitDayKind({ ...habit, frequency: 'WEEKLY', schedule: {} }, '2026-11-24')).toBe('flexible');
    expect(habitDayKind({ ...habit, active: false }, '2026-11-23')).toBe('paused');
  });
});
