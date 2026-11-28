/**
 * Client-side API client. In production the static site and /api/* requests share the CloudFront origin;
 * CloudFront forwards API paths to the ALB, which routes to each service. The JWT is attached as a Bearer
 * header from the auth store. Base URLs come from build-time env (VITE_*); local dev defaults to service ports.
 */
import { browser } from '$app/environment';
import { getRefreshToken, getToken, logout, setToken } from './auth.svelte';
import type {
  ActivityQuery,
  ActivityLog,
  Achievement,
  AnalyticsSummary,
  ChallengeDetail,
  ChallengeSummary,
  CohortSummary,
  Connection,
  Habit,
  Invitation,
  PatternSummary,
  RecommendationResponse,
  Page,
  TenantInfo,
  UserProfile,
  WeeklyStat
} from './types';

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

interface ErrorPayload {
  code?: string;
  message?: string;
  details?: string[];
}

async function send(svc: Svc, path: string, opts: Opts, token: string | null): Promise<Response> {
  const headers: Record<string, string> = { Accept: 'application/json' };
  if (token) headers.Authorization = `Bearer ${token}`;
  if (opts.body !== undefined) headers['Content-Type'] = 'application/json';
  try {
    return await fetch(BASES[svc] + path, {
      method: opts.method ?? 'GET',
      headers,
      body: opts.body !== undefined ? JSON.stringify(opts.body) : undefined
    });
  } catch {
    throw new ApiError(
      503,
      'SERVICE_UNAVAILABLE',
      "The service is temporarily unavailable. Check your connection and try again."
    );
  }
}

let refreshInFlight: Promise<boolean> | null = null;

/** Exchanges the stored refresh token once, shared by concurrent callers. Resolves false if the session ended. */
export function refreshSession(): Promise<boolean> {
  refreshInFlight ??= (async () => {
    const refreshToken = getRefreshToken();
    if (!refreshToken) return false;
    try {
      const res = await send('user', '/api/auth/refresh', { method: 'POST', body: { refreshToken } }, null);
      if (!res.ok) return false;
      const data = (await res.json()) as { accessToken?: unknown; refreshToken?: unknown };
      if (typeof data.accessToken !== 'string') return false;
      setToken(data.accessToken, typeof data.refreshToken === 'string' ? data.refreshToken : undefined);
      return true;
    } catch {
      return false;
    }
  })().finally(() => {
    refreshInFlight = null;
  });
  return refreshInFlight;
}

function endSession() {
  logout();
  if (browser && window.location.pathname !== '/login') {
    window.location.assign('/login?expired=1');
  }
}

async function call<T>(svc: Svc, path: string, opts: Opts = {}): Promise<T> {
  const isAuthCall = path.startsWith('/api/auth/');
  const token = isAuthCall ? null : getToken();
  let res = await send(svc, path, opts, token);
  if (res.status === 401 && token) {
    if (await refreshSession()) {
      res = await send(svc, path, opts, getToken());
    }
    if (res.status === 401) endSession();
  }

  const text = await res.text();
  let data: unknown = null;
  if (text) {
    try {
      data = JSON.parse(text) as unknown;
    } catch {
      throw new ApiError(
        res.ok ? 502 : res.status,
        'INVALID_RESPONSE',
        'The service returned an unreadable response. Please try again.'
      );
    }
  }
  if (!res.ok) {
    const payload = errorPayload(data);
    throw new ApiError(
      res.status,
      payload.code ?? 'ERROR',
      payload.message ?? (res.statusText || `Request failed with status ${res.status}.`),
      payload.details ?? []
    );
  }
  return data as T;
}

function errorPayload(value: unknown): ErrorPayload {
  if (typeof value !== 'object' || value === null) return {};
  const record = value as Record<string, unknown>;
  return {
    ...(typeof record.code === 'string' ? { code: record.code } : {}),
    ...(typeof record.message === 'string' ? { message: record.message } : {}),
    ...(Array.isArray(record.details)
      ? { details: record.details.filter((detail): detail is string => typeof detail === 'string') }
      : {})
  };
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
    me: () => call<UserProfile>('user', '/api/users/me'),
    updateMe: (body: unknown) => call<UserProfile>('user', '/api/users/me', { method: 'PUT', body })
  },
  tenants: {
    me: () => call<TenantInfo>('user', '/api/tenants/me')
  },
  habits: {
    list: (page = 0) => call<Page<Habit>>('activity', `/api/habits?size=100&sort=id,asc${page ? `&page=${page}` : ''}`),
    get: (id: string) => call('activity', `/api/habits/${id}`),
    create: (body: unknown) => call('activity', '/api/habits', { method: 'POST', body }),
    update: (id: string, body: unknown) => call('activity', `/api/habits/${id}`, { method: 'PUT', body }),
    pause: (id: string, days = 7) =>
      call('activity', `/api/habits/${id}/pause`, { method: 'POST', body: { days } }),
    resume: (id: string) => call('activity', `/api/habits/${id}/resume`, { method: 'POST' }),
    remove: (id: string) => call('activity', `/api/habits/${id}`, { method: 'DELETE' }),
    complete: (id: string, body: unknown) =>
      call('activity', `/api/habits/${id}/completions`, { method: 'POST', body }),
    completions: (id: string, page = 0, size = 20) =>
      call<Page<ActivityLog>>('activity', `/api/habits/${id}/completions?page=${page}&size=${size}&sort=completionDate,desc&sort=id,asc`)
  },
  activity: {
    log: (query: ActivityQuery = {}) => {
      const params = new URLSearchParams();
      params.set('page', String(query.page ?? 0));
      params.set('size', String(query.size ?? 50));
      if (query.sort) params.set('sort', query.sort);
      if (query.type) params.set('type', query.type);
      if (query.from) params.set('from', query.from);
      if (query.to) params.set('to', query.to);
      return call<Page<ActivityLog>>('activity', `/api/activity?${params.toString()}`);
    }
  },
  challenges: {
    feed: (page = 0) => call<Page<ChallengeSummary>>('challenge', `/api/challenges?size=100&page=${page}&sort=id,asc`),
    get: (id: string) => call<ChallengeDetail>('challenge', `/api/challenges/${id}`),
    create: (body: unknown) => call<ChallengeSummary>('challenge', '/api/challenges', { method: 'POST', body }),
    join: (id: string) => call<ChallengeDetail>('challenge', `/api/challenges/${id}/join`, { method: 'POST' }),
    invite: (id: string, body: unknown) =>
      call('challenge', `/api/challenges/${id}/invites`, { method: 'POST', body })
  },
  invitations: {
    pending: (page = 0) => call<Page<Invitation>>('challenge', `/api/invitations?size=100&page=${page}&sort=id,asc`),
    count: async () => (await call<Page<Invitation>>('challenge', '/api/invitations?size=1')).totalElements,
    accept: (id: string) => call('challenge', `/api/invitations/${id}/accept`, { method: 'POST' }),
    reject: (id: string) => call('challenge', `/api/invitations/${id}/reject`, { method: 'POST' })
  },
  achievements: {
    list: (page = 0) => call<Page<Achievement>>('challenge', `/api/achievements?size=100&page=${page}&sort=id,asc`)
  },
  integrations: {
    list: () => call<Connection[]>('integration', '/api/integrations/connections'),
    create: (body: unknown) => call<Connection>('integration', '/api/integrations/connections', { method: 'POST', body }),
    revoke: (id: string) => call('integration', `/api/integrations/connections/${id}`, { method: 'DELETE' })
  },
  analytics: {
    summary: () => call<AnalyticsSummary>('analytics', '/api/analytics/summary'),
    daily: (from?: string, to?: string) => {
      const params = new URLSearchParams();
      if (from) params.set('from', from);
      if (to) params.set('to', to);
      const query = params.toString();
      return call<{ date: string; completions: number }[]>('analytics', `/api/analytics/daily${query ? `?${query}` : ''}`);
    },
    weekly: (weeks = 8) =>
      call<WeeklyStat[]>('analytics', `/api/analytics/weekly?weeks=${weeks}`),
    pattern: () => call<PatternSummary>('analytics', '/api/analytics/pattern'),
    cohort: (date: string) =>
      call<CohortSummary>('analytics', `/api/analytics/cohort?date=${encodeURIComponent(date)}`)
  },
  recommendations: {
    list: () => call<RecommendationResponse>('recommendation', '/api/recommendations'),
    forHabit: (id: string) =>
      call<RecommendationResponse>('recommendation', `/api/recommendations/habits/${id}`)
  }
};
