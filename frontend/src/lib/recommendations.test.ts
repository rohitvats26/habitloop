import { describe, expect, it } from 'vitest';
import { eligibleRecommendationHabits, hasApplicableRecommendation, isRecommendationApplied, recommendationUpdate, recommendationsForCategory } from './recommendations';
import type { Habit, Recommendation } from './types';

const habit: Habit = {
  id: 'habit-1',
  name: 'Morning walk',
  frequency: 'CUSTOM',
  schedule: { daysOfWeek: [1, 3, 5], targetTime: '07:00', perWeek: 3 },
  targetPerPeriod: 3,
  active: true,
  weeklyProgress: { completed: 1, expected: 2, consistencyPercent: 50 },
  streak: { current: 2, longest: 5 }
};

function recommendation(action: Record<string, unknown>): Recommendation {
  return {
    type: 'TIMING',
    title: 'Try a new time',
    message: 'A new time may help.',
    action,
    confidence: 0.8
  };
}

describe('recommendation actions', () => {
  it('updates timing without dropping the existing custom schedule', () => {
    expect(recommendationUpdate(recommendation({ suggestedTime: '08:00' }), habit)).toEqual({
      schedule: { daysOfWeek: [1, 3, 5], targetTime: '08:00', perWeek: 3 }
    });
  });

  describe('recommendation categories', () => {
    const recommendations = [
      recommendation({ suggestedTime: '08:00' }),
      { ...recommendation({ suggestedPerWeek: 4 }), type: 'FREQUENCY' },
      { ...recommendation({ suggestedWeekday: 'MON' }), type: 'CONSISTENCY' }
    ];

    it('returns all suggestions or only the selected focus', () => {
      expect(recommendationsForCategory(recommendations, 'ALL')).toHaveLength(3);
      expect(recommendationsForCategory(recommendations, 'FREQUENCY').map((item) => item.title))
        .toEqual(['Try a new time']);
    });

    it('matches categories case-insensitively and returns no items for an empty category', () => {
      const mixedCase = [{ ...recommendations[0], type: 'timing' }];
      expect(recommendationsForCategory(mixedCase, 'TIMING')).toEqual(mixedCase);
      expect(recommendationsForCategory(recommendations, 'CONSISTENCY')).toHaveLength(1);
      expect(recommendationsForCategory(recommendations, 'TIMING')).toHaveLength(1);
      expect(recommendationsForCategory(recommendations.slice(1), 'TIMING')).toEqual([]);
    });
  });

  it('applies a weekly target to a selected habit', () => {
    expect(recommendationUpdate(recommendation({ suggestedPerWeek: 4 }), habit)).toEqual({
      frequency: 'WEEKLY',
      schedule: { targetTime: '07:00', perWeek: 4 },
      targetPerPeriod: 4
    });
  });

  it('does not offer automatic apply for an insight with no supported change', () => {
    const insight = recommendation({ suggestedWeekday: 'MON' });
    expect(hasApplicableRecommendation(insight)).toBe(false);
    expect(recommendationUpdate(insight, habit)).toBeNull();
  });

  it('does not offer invalid time or weekly target values', () => {
    expect(hasApplicableRecommendation(recommendation({ suggestedTime: '25:00' }))).toBe(false);
    expect(hasApplicableRecommendation(recommendation({ suggestedPerWeek: 8 }))).toBe(false);
  });

  it('recognizes applied timing and allows a different suggested time', () => {
    expect(isRecommendationApplied(recommendation({ suggestedTime: '07:00' }), habit)).toBe(true);
    expect(isRecommendationApplied(recommendation({ suggestedTime: '08:00' }), habit)).toBe(false);
  });

  it('recognizes weekly targets only when the full recommended schedule is applied', () => {
    const weekly = { ...habit, frequency: 'WEEKLY' as const, schedule: { perWeek: 3 }, targetPerPeriod: 3 };
    const r = recommendation({ suggestedPerWeek: 3 });
    expect(isRecommendationApplied(r, weekly)).toBe(true);
    expect(isRecommendationApplied(r, habit)).toBe(false);
    expect(isRecommendationApplied(r, { ...weekly, targetPerPeriod: 2 })).toBe(false);
  });

  it('recognizes scheduled recommendations and builds later updates from the pending schedule', () => {
    const planned: Habit = {
      ...habit,
      pendingSchedule: {
        effectiveDate: '2026-11-30',
        frequency: 'WEEKLY',
        schedule: { perWeek: 4, targetTime: '08:00' },
        targetPerPeriod: 4
      }
    };
    expect(isRecommendationApplied(recommendation({ suggestedPerWeek: 4 }), planned)).toBe(true);
    expect(isRecommendationApplied(recommendation({ suggestedTime: '07:00' }), planned)).toBe(false);
    expect(recommendationUpdate(recommendation({ suggestedTime: '09:00' }), planned))
      .toEqual({ schedule: { perWeek: 4, targetTime: '09:00' } });
  });

  it('limits habit-specific recommendations to their active habit', () => {
    const other = { ...habit, id: 'habit-2' };
    const r = { ...recommendation({ suggestedTime: '08:00' }), habitId: habit.id };
    expect(eligibleRecommendationHabits(r, [habit, other])).toEqual([habit]);
    expect(eligibleRecommendationHabits(r, [{ ...habit, active: false }, other])).toEqual([]);
    expect(eligibleRecommendationHabits(recommendation({ suggestedTime: '08:00' }), [habit, other])).toHaveLength(2);
  });
});
