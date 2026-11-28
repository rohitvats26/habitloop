import { describe, expect, it } from 'vitest';
import { canJoinChallenge, challengeDateError, memberLabel } from './challenges';
import { activityWhen } from './date';

describe('challenge rules', () => {
  it('offers joining only for active public challenges the user has not joined', () => {
    expect(canJoinChallenge({ visibility: 'PUBLIC', status: 'ACTIVE' }, false)).toBe(true);
    expect(canJoinChallenge({ visibility: 'PUBLIC', status: 'ACTIVE' }, true)).toBe(false);
    expect(canJoinChallenge({ visibility: 'PRIVATE', status: 'ACTIVE' }, false)).toBe(false);
    expect(canJoinChallenge({ visibility: 'PUBLIC', status: 'ENDED' }, false)).toBe(false);
  });

  it('labels members by name, marks the viewer and falls back to a short ID', () => {
    expect(memberLabel('aaaaaaaa-1', 'Ria', 'aaaaaaaa-1')).toBe('Ria (you)');
    expect(memberLabel('bbbbbbbb-2', ' ', 'aaaaaaaa-1')).toBe('Member bbbbbbbb');
    expect(memberLabel('cccccccc-3', 'Asha')).toBe('Asha');
  });

  it('rejects inverted or already-finished date ranges', () => {
    expect(challengeDateError('2026-11-26', '2026-11-25', '2026-11-26')).toMatch('on or after');
    expect(challengeDateError('2026-11-01', '2026-11-20', '2026-11-26')).toMatch('today or later');
    expect(challengeDateError('2026-11-26', '2026-11-26', '2026-11-26')).toBe('');
  });
});

describe('activityWhen', () => {
  it('shows only the IST day for manual completions but keeps the time for synced activity', () => {
    const at = '2026-11-25T18:30:00Z';
    expect(activityWhen({ activityType: 'HABIT_DONE', source: 'MANUAL', occurredAt: at })).toBe('26 Nov 2026');
    expect(activityWhen({ activityType: 'WALKING', source: 'WEARABLE', occurredAt: at })).toMatch(/26 Nov 2026.*12:00/i);
  });
});
