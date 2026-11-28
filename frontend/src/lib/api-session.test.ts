import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const session = vi.hoisted(() => ({ token: 'old' as string | null, refresh: 'refresh-1' as string | null }));

vi.mock('./auth.svelte', () => ({
  getToken: () => session.token,
  getRefreshToken: () => session.refresh,
  setToken: (token: string, refresh?: string) => {
    session.token = token;
    if (refresh) session.refresh = refresh;
  },
  logout: vi.fn(() => {
    session.token = null;
    session.refresh = null;
  })
}));

import { api } from './api';
import { logout } from './auth.svelte';

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });

beforeEach(() => {
  session.token = 'old';
  session.refresh = 'refresh-1';
  vi.mocked(logout).mockClear();
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('session refresh', () => {
  it('refreshes once for concurrent expired requests and retries them with the new token', async () => {
    const fetchMock = vi.fn((url: string, init: RequestInit) => {
      if (url.endsWith('/api/auth/refresh')) {
        return Promise.resolve(json(200, { accessToken: 'new', refreshToken: 'refresh-2' }));
      }
      const auth = (init.headers as Record<string, string>).Authorization;
      return Promise.resolve(auth === 'Bearer new' ? json(200, { ok: true }) : json(401, { message: 'expired' }));
    });
    vi.stubGlobal('fetch', fetchMock);

    await expect(Promise.all([api.users.me(), api.tenants.me()])).resolves.toEqual([{ ok: true }, { ok: true }]);
    expect(fetchMock.mock.calls.filter(([url]) => url.endsWith('/api/auth/refresh'))).toHaveLength(1);
    expect(session).toEqual({ token: 'new', refresh: 'refresh-2' });
    expect(logout).not.toHaveBeenCalled();
  });

  it('ends the session when the refresh token is rejected', async () => {
    vi.stubGlobal('fetch', vi.fn((url: string) =>
      Promise.resolve(url.endsWith('/api/auth/refresh') ? json(401, { message: 'invalid' }) : json(401, { message: 'expired' }))));

    await expect(api.users.me()).rejects.toMatchObject({ status: 401 });
    expect(logout).toHaveBeenCalled();
  });
});
