import { beforeEach, describe, expect, it, vi } from 'vitest';
import { loadTodayActivity } from './today-activity';
import { api } from './api';

vi.mock('./api', () => ({ api: { activity: { log: vi.fn() } } }));
vi.mock('./date', () => ({ userDateString: vi.fn().mockResolvedValue('2026-11-26') }));

describe('loadTodayActivity', () => {
  beforeEach(() => vi.mocked(api.activity.log).mockReset());

  it('uses the IST day and includes manual completions from later pages', async () => {
    const first = Array.from({ length: 100 }, (_, index) => ({
      id: `${index}`, habitId: `h${index}`, activityType: 'HABIT_DONE',
      occurredAt: '2026-11-26T00:00:00+05:30', source: 'WEARABLE' as const
    }));
    vi.mocked(api.activity.log)
      .mockResolvedValueOnce({ content: first, page: 0, size: 100, totalElements: 101, totalPages: 2 })
      .mockResolvedValueOnce({
        content: [{ id: '100', habitId: 'done', activityType: 'HABIT_DONE', occurredAt: '2026-11-26T00:00:00+05:30', source: 'MANUAL' }],
        page: 1, size: 100, totalElements: 101, totalPages: 2
      });

    await expect(loadTodayActivity()).resolves.toEqual({ date: '2026-11-26', completedHabitIds: ['done'] });
    expect(api.activity.log).toHaveBeenLastCalledWith({
      type: 'HABIT_DONE', from: '2026-11-26T00:00:00+05:30',
      to: '2026-11-26T23:59:59.999999999+05:30', page: 1, size: 100, sort: 'id,asc'
    });
  });
});
