/**
 * Server-side backend client. All calls run on the SvelteKit server (never the browser), so there are no
 * CORS concerns and the JWT stays in an httpOnly cookie. In AWS the ALB routes /api/* to each service; in
 * dev these base URLs point at the local service ports.
 */
import { env } from '$env/dynamic/private';

const BASES = {
  user: env.USER_SERVICE_URL ?? 'http://localhost:8081',
  activity: env.ACTIVITY_SERVICE_URL ?? 'http://localhost:8082',
  challenge: env.CHALLENGE_SERVICE_URL ?? 'http://localhost:8083',
  integration: env.INTEGRATION_SERVICE_URL ?? 'http://localhost:8084',
  analytics: env.ANALYTICS_SERVICE_URL ?? 'http://localhost:8085',
  recommendation: env.RECOMMENDATION_SERVICE_URL ?? 'http://localhost:8086'
} as const;

export class ApiError extends Error {
  constructor(public status: number, public code: string, message: string, public details: string[] = []) {
    super(message);
  }
}

type Svc = keyof typeof BASES;

interface Opts {
  method?: string;
  token?: string;
  body?: unknown;
}

async function call<T>(svc: Svc, path: string, opts: Opts = {}): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' };
  if (opts.token) headers.Authorization = `Bearer ${opts.token}`;
  if (opts.body !== undefined) headers['Content-Type'] = 'application/json';

  let res: Response;
  try {
    res = await fetch(BASES[svc] + path, {
      method: opts.method ?? 'GET',
      headers,
      body: opts.body !== undefined ? JSON.stringify(opts.body) : undefined
    });
  } catch (e) {
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

export const backend = {
  auth: {
    register: (body: unknown) => call('user', '/api/auth/register', { method: 'POST', body }),
    login: (body: unknown) => call<{ accessToken: string; refreshToken: string; expiresIn: number }>(
      'user', '/api/auth/login', { method: 'POST', body })
  },
  users: {
    me: (token: string) => call('user', '/api/users/me', { token }),
    updateMe: (token: string, body: unknown) => call('user', '/api/users/me', { method: 'PUT', token, body })
  },
  habits: {
    list: (token: string) => call('activity', '/api/habits?size=100', { token }),
    get: (token: string, id: string) => call('activity', `/api/habits/${id}`, { token }),
    create: (token: string, body: unknown) => call('activity', '/api/habits', { method: 'POST', token, body }),
    update: (token: string, id: string, body: unknown) =>
      call('activity', `/api/habits/${id}`, { method: 'PUT', token, body }),
    remove: (token: string, id: string) => call('activity', `/api/habits/${id}`, { method: 'DELETE', token }),
    complete: (token: string, id: string, body: unknown) =>
      call('activity', `/api/habits/${id}/completions`, { method: 'POST', token, body })
  },
  activity: {
    log: (token: string) => call('activity', '/api/activity?size=50', { token })
  },
  challenges: {
    feed: (token: string) => call('challenge', '/api/challenges?size=100', { token }),
    get: (token: string, id: string) => call('challenge', `/api/challenges/${id}`, { token }),
    create: (token: string, body: unknown) => call('challenge', '/api/challenges', { method: 'POST', token, body }),
    join: (token: string, id: string) => call('challenge', `/api/challenges/${id}/join`, { method: 'POST', token }),
    invite: (token: string, id: string, body: unknown) =>
      call('challenge', `/api/challenges/${id}/invites`, { method: 'POST', token, body })
  },
  invitations: {
    pending: (token: string) => call('challenge', '/api/invitations?size=100', { token }),
    accept: (token: string, id: string) => call('challenge', `/api/invitations/${id}/accept`, { method: 'POST', token }),
    reject: (token: string, id: string) => call('challenge', `/api/invitations/${id}/reject`, { method: 'POST', token })
  },
  integrations: {
    list: (token: string) => call('integration', '/api/integrations/connections', { token }),
    create: (token: string, body: unknown) =>
      call('integration', '/api/integrations/connections', { method: 'POST', token, body }),
    revoke: (token: string, id: string) =>
      call('integration', `/api/integrations/connections/${id}`, { method: 'DELETE', token })
  },
  analytics: {
    summary: (token: string) => call('analytics', '/api/analytics/summary', { token }),
    daily: (token: string) => call('analytics', '/api/analytics/daily', { token })
  },
  recommendations: {
    list: (token: string) => call<{ items: unknown[] }>('recommendation', '/api/recommendations', { token })
  }
};
