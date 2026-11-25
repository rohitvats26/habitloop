import { describe, expect, it } from 'vitest';
import { completedHabitIdsForDay } from './dashboard';
import type { ActivityLog } from './types';

describe('completedHabitIdsForDay', () => {
  it('counts unique habit completions and ignores other activity types', () => {
    const activity: ActivityLog[] = [
      { id: '1', habitId: 'habit-1', activityType: 'HABIT_DONE', occurredAt: '2026-11-26T08:00:00+05:30', source: 'MANUAL' },
      { id: '2', habitId: 'habit-1', activityType: 'HABIT_DONE', occurredAt: '2026-11-26T08:00:00+05:30', source: 'MANUAL' },
      { id: '3', habitId: 'habit-2', activityType: 'WALKING', occurredAt: '2026-11-26T09:00:00+05:30', source: 'WEARABLE' },
      { id: '4', activityType: 'HABIT_DONE', occurredAt: '2026-11-26T10:00:00+05:30', source: 'MANUAL' }
    ];

    expect(completedHabitIdsForDay(activity)).toEqual(['habit-1']);
  });
});
