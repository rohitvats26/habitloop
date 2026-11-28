import type { Habit, Recommendation } from './types';

export type RecommendationCategory = 'ALL' | 'TIMING' | 'FREQUENCY' | 'CONSISTENCY';

export function recommendationsForCategory(
  recommendations: Recommendation[],
  category: RecommendationCategory
): Recommendation[] {
  if (category === 'ALL') return recommendations;
  return recommendations.filter((recommendation) => recommendation.type.toUpperCase() === category);
}

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
  const source = habit.pendingSchedule?.schedule ?? habit.schedule;
  const schedule: Record<string, unknown> = {};
  for (const field of ['daysOfWeek', 'targetTime', 'perWeek']) {
    if (field in source) schedule[field] = source[field];
  }
  return schedule;
}

export function hasApplicableRecommendation(recommendation: Recommendation): boolean {
  return isValidTime(recommendation.action.suggestedTime)
    || isValidWeeklyTarget(recommendation.action.suggestedPerWeek);
}

export function isRecommendationApplied(recommendation: Recommendation, habit: Habit): boolean {
  const action = recommendation.action;
  const planned = habit.pendingSchedule ?? habit;
  if (isValidTime(action.suggestedTime)) {
    return planned.schedule.targetTime === action.suggestedTime;
  }
  if (isValidWeeklyTarget(action.suggestedPerWeek)) {
    return planned.frequency === 'WEEKLY'
      && planned.schedule.perWeek === action.suggestedPerWeek
      && planned.targetPerPeriod === action.suggestedPerWeek
      && !('daysOfWeek' in planned.schedule);
  }
  return false;
}

export function eligibleRecommendationHabits(recommendation: Recommendation, habits: Habit[]): Habit[] {
  return habits.filter((habit) => habit.active && (!recommendation.habitId || habit.id === recommendation.habitId));
}

function isValidTime(value: unknown): value is string {
  if (typeof value !== 'string' || !/^\d{2}:\d{2}$/.test(value)) return false;
  const [hours, minutes] = value.split(':').map(Number);
  return hours < 24 && minutes < 60;
}

function isValidWeeklyTarget(value: unknown): value is number {
  return typeof value === 'number' && Number.isInteger(value) && value >= 1 && value <= 7;
}
