import { afterEach, describe, expect, it, vi } from 'vitest';

vi.mock('./auth.svelte', () => ({
  getToken: () => null,
  getRefreshToken: () => null,
  setToken: vi.fn(),
  logout: vi.fn()
}));

import { api, ApiError } from './api';

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('API errors', () => {
  it('surfaces server validation details as a user-readable error', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify({
            status: 400,
            code: 'VALIDATION_ERROR',
            message: 'Please correct the invalid fields: target: must be positive',
            details: ['target: must be positive']
          }),
          { status: 400, headers: { 'Content-Type': 'application/json' } }
        )
      )
    );

    await expect(api.habits.create({ targetPerPeriod: 0 })).rejects.toMatchObject({
      status: 400,
      code: 'VALIDATION_ERROR',
      message: 'Please correct the invalid fields: target: must be positive',
      details: ['target: must be positive']
    });
  });

  describe('analytics and activity query APIs', () => {
    it('can request later habit pages without changing the default URL', async () => {
      const fetchMock = vi.fn().mockImplementation(() =>
        Promise.resolve(new Response(JSON.stringify({ content: [], totalPages: 0 }), { status: 200 }))
      );
      vi.stubGlobal('fetch', fetchMock);
      await api.habits.list();
      await api.habits.list(1);
      expect(fetchMock.mock.calls.map(([url]) => url)).toEqual([
        'http://localhost:8082/api/habits?size=100&sort=id,asc',
        'http://localhost:8082/api/habits?size=100&sort=id,asc&page=1'
      ]);
    });

    it('routes schedule and achievement APIs to their owning services', async () => {
      const fetchMock = vi.fn().mockImplementation(() =>
        Promise.resolve(new Response(JSON.stringify({}), {
          status: 200,
          headers: { 'Content-Type': 'application/json' }
        }))
      );
      vi.stubGlobal('fetch', fetchMock);

      await api.habits.pause('habit-id', 7);
      await api.achievements.list();

      expect(fetchMock.mock.calls.map(([url, init]) => [url, init?.method ?? 'GET'])).toEqual([
        ['http://localhost:8082/api/habits/habit-id/pause', 'POST'],
        ['http://localhost:8083/api/achievements?size=100&page=0&sort=id,asc', 'GET']
      ]);
    });

    it('encodes activity filters and pagination as query parameters', async () => {
      const fetchMock = vi.fn().mockImplementation(() =>
        Promise.resolve(new Response(JSON.stringify({ content: [] }), {
          status: 200,
          headers: { 'Content-Type': 'application/json' }
        }))
      );
      vi.stubGlobal('fetch', fetchMock);

      await api.activity.log({
        type: 'WALKING',
        from: '2025-01-01T00:00:00+05:30',
        to: '2025-01-31T23:59:59+05:30',
        page: 1,
        size: 25
      });

      expect(fetchMock.mock.calls[0][0]).toBe(
        'http://localhost:8082/api/activity?page=1&size=25&type=WALKING&from=2025-01-01T00%3A00%3A00%2B05%3A30&to=2025-01-31T23%3A59%3A59%2B05%3A30'
      );
    });

    it('requests the existing weekly analytics and per-habit recommendation endpoints', async () => {
      const fetchMock = vi.fn().mockImplementation(() =>
        Promise.resolve(new Response(JSON.stringify({ items: [] }), {
          status: 200,
          headers: { 'Content-Type': 'application/json' }
        }))
      );
      vi.stubGlobal('fetch', fetchMock);

      await api.analytics.weekly(12);
      await api.analytics.pattern();
      await api.recommendations.forHabit('habit-id');

      expect(fetchMock.mock.calls.map(([url]) => url)).toEqual([
        'http://localhost:8085/api/analytics/weekly?weeks=12',
        'http://localhost:8085/api/analytics/pattern',
        'http://localhost:8086/api/recommendations/habits/habit-id'
      ]);
    });
  });

  it('returns a safe message when a service responds with non-JSON error content', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('<html>bad gateway</html>', { status: 502 })));

    await expect(api.habits.list()).rejects.toMatchObject({
      status: 502,
      code: 'INVALID_RESPONSE',
      message: 'The service returned an unreadable response. Please try again.'
    });
  });
});
