import type { Habit } from './types';

export function streakUnit(habit: Pick<Habit, 'frequency' | 'schedule'>): 'day' | 'week' {
  const days = habit.schedule?.daysOfWeek;
  return habit.frequency !== 'DAILY' && !(Array.isArray(days) && days.length > 0) ? 'week' : 'day';
}
