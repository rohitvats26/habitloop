/**
 * Client-side API client. Runs in the browser (static S3 site), calling the backend cross-origin via the
 * ALB. The JWT is attached as a Bearer header from the auth store. Base URLs come from build-time env
 * (VITE_*). In production set every VITE_*_SERVICE_URL to the ALB URL (path routing sends each /api/* to the
 * right service); in local dev they default to the per-service ports.
 */
import { getToken } from './auth.svelte';

const BASES = {
  user: import.meta.env.VITE_USER_SERVICE_URL ?? 'http://localhost:8081',
  activity: import.meta.env.VITE_ACTIVITY_SERVICE_URL ?? 'http://localhost:8082',
  challenge: import.meta.env.VITE_CHALLENGE_SERVICE_URL ?? 'http://localhost:8083',
  integration: import.meta.env.VITE_INTEGRATION_SERVICE_URL ?? 'http://localhost:8084',
  analytics: import.meta.env.VITE_ANALYTICS_SERVICE_URL ?? 'http://localhost:8085',
  recommendation: import.meta.env.VITE_RECOMMENDATION_SERVICE_URL ?? 'http://localhost:8086'
} as const;

export class ApiError extends Error {
  constructor(
    public status: number,
    public code: string,
    message: string,
    public details: string[] = []
  ) {
    super(message);
  }
}

type Svc = keyof typeof BASES;

interface Opts {
  method?: string;
  body?: unknown;
}

async function call<T>(svc: Svc, path: string, opts: Opts = {}): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' };
  const token = getToken();
  if (token) headers.Authorization = `Bearer ${token}`;
  if (opts.body !== undefined) headers['Content-Type'] = 'application/json';

  let res: Response;
  try {
    res = await fetch(BASES[svc] + path, {
      method: opts.method ?? 'GET',
      headers,
      body: opts.body !== undefined ? JSON.stringify(opts.body) : undefined
    });
  } catch {
    throw new ApiError(503, 'SERVICE_UNAVAILABLE', `${svc}-service unreachable`);
  }

  const text = await res.text();
  const data = text ? JSON.parse(text) : null;
  if (!res.ok) {
    const code = data?.code ?? 'ERROR';
    const message = data?.message ?? res.statusText;
    throw new ApiError(res.status, code, message, data?.details ?? []);
  }
  return data as T;
}

export const api = {
  auth: {
    register: (body: unknown) => call('user', '/api/auth/register', { method: 'POST', body }),
    login: (body: unknown) =>
      call<{ accessToken: string; refreshToken: string; expiresIn: number }>('user', '/api/auth/login', {
        method: 'POST',
        body
      })
  },
  users: {
    me: () => call('user', '/api/users/me'),
    updateMe: (body: unknown) => call('user', '/api/users/me', { method: 'PUT', body })
  },
  habits: {
    list: () => call('activity', '/api/habits?size=100'),
    get: (id: string) => call('activity', `/api/habits/${id}`),
    create: (body: unknown) => call('activity', '/api/habits', { method: 'POST', body }),
    update: (id: string, body: unknown) => call('activity', `/api/habits/${id}`, { method: 'PUT', body }),
    remove: (id: string) => call('activity', `/api/habits/${id}`, { method: 'DELETE' }),
    complete: (id: string, body: unknown) =>
      call('activity', `/api/habits/${id}/completions`, { method: 'POST', body })
  },
  activity: {
    log: () => call('activity', '/api/activity?size=50')
  },
  challenges: {
    feed: () => call('challenge', '/api/challenges?size=100'),
    get: (id: string) => call('challenge', `/api/challenges/${id}`),
    create: (body: unknown) => call('challenge', '/api/challenges', { method: 'POST', body }),
    join: (id: string) => call('challenge', `/api/challenges/${id}/join`, { method: 'POST' }),
    invite: (id: string, body: unknown) =>
      call('challenge', `/api/challenges/${id}/invites`, { method: 'POST', body })
  },
  invitations: {
    pending: () => call('challenge', '/api/invitations?size=100'),
    accept: (id: string) => call('challenge', `/api/invitations/${id}/accept`, { method: 'POST' }),
    reject: (id: string) => call('challenge', `/api/invitations/${id}/reject`, { method: 'POST' })
  },
  integrations: {
    list: () => call('integration', '/api/integrations/connections'),
    create: (body: unknown) => call('integration', '/api/integrations/connections', { method: 'POST', body }),
    revoke: (id: string) => call('integration', `/api/integrations/connections/${id}`, { method: 'DELETE' })
  },
  analytics: {
    summary: () => call('analytics', '/api/analytics/summary'),
    daily: () => call('analytics', '/api/analytics/daily')
  },
  recommendations: {
    list: () => call<{ items: unknown[] }>('recommendation', '/api/recommendations')
  }
};
