import { describe, expect, it } from 'vitest';
import { streakUnit } from './streak';

describe('streakUnit', () => {
  it('distinguishes scheduled days from flexible weekly targets', () => {
    expect(streakUnit({ frequency: 'DAILY', schedule: {} })).toBe('day');
    expect(streakUnit({ frequency: 'CUSTOM', schedule: { daysOfWeek: [1, 3] } })).toBe('day');
    expect(streakUnit({ frequency: 'CUSTOM', schedule: { perWeek: 3 } })).toBe('week');
    expect(streakUnit({ frequency: 'WEEKLY', schedule: {} })).toBe('week');
  });
});
