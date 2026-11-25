import { describe, expect, it } from 'vitest';
import { hasApplicableRecommendation, recommendationUpdate } from './recommendations';
import type { Habit, Recommendation } from './types';

const habit: Habit = {
  id: 'habit-1',
  name: 'Morning walk',
  frequency: 'CUSTOM',
  schedule: { daysOfWeek: [1, 3, 5], targetTime: '07:00', perWeek: 3 },
  targetPerPeriod: 3,
  active: true,
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
});
