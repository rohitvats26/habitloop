import type { Habit, Recommendation } from './types';

export function recommendationUpdate(recommendation: Recommendation, habit: Habit): Record<string, unknown> | null {
  const action = recommendation.action;
  if (isValidTime(action.suggestedTime)) {
    return {
      schedule: {
        ...supportedSchedule(habit),
        targetTime: action.suggestedTime
      }
    };
  }

  const suggestedPerWeek = action.suggestedPerWeek;
  if (isValidWeeklyTarget(suggestedPerWeek)) {
    const schedule = supportedSchedule(habit);
    delete schedule.daysOfWeek;
    return {
      frequency: 'WEEKLY',
      schedule: {
        ...schedule,
        perWeek: suggestedPerWeek
      },
      targetPerPeriod: suggestedPerWeek
    };
  }

  return null;
}

function supportedSchedule(habit: Habit): Record<string, unknown> {
  const schedule: Record<string, unknown> = {};
  for (const field of ['daysOfWeek', 'targetTime', 'perWeek']) {
    if (field in habit.schedule) schedule[field] = habit.schedule[field];
  }
  return schedule;
}

export function hasApplicableRecommendation(recommendation: Recommendation): boolean {
  return isValidTime(recommendation.action.suggestedTime)
    || isValidWeeklyTarget(recommendation.action.suggestedPerWeek);
}

function isValidTime(value: unknown): value is string {
  if (typeof value !== 'string' || !/^\d{2}:\d{2}$/.test(value)) return false;
  const [hours, minutes] = value.split(':').map(Number);
  return hours < 24 && minutes < 60;
}

function isValidWeeklyTarget(value: unknown): value is number {
  return typeof value === 'number' && Number.isInteger(value) && value >= 1 && value <= 7;
}
