import { browser } from '$app/environment';
import { decodeJwt } from './jwt';
import type { AuthUser } from './types';

const TOKEN_KEY = 'hl_token';

function readToken(): string | null {
  if (!browser) return null;
  try {
    return localStorage.getItem(TOKEN_KEY);
  } catch {
    return null;
  }
}

const initial = readToken();

/**
 * Client-side auth state (runes). The JWT is kept in localStorage and sent as a Bearer header by the API
 * client. NOTE: a static S3 site cannot use httpOnly cookies, so the token lives in the browser — this is
 * the documented trade-off of the S3 static-hosting choice (PDF Session 3). See frontend/README.md.
 */
export const auth = $state<{ token: string | null; user: AuthUser | null }>({
  token: initial,
  user: initial ? decodeJwt(initial) : null
});

export function setToken(token: string) {
  auth.token = token;
  auth.user = decodeJwt(token);
  try {
    localStorage.setItem(TOKEN_KEY, token);
  } catch {
    /* storage unavailable (private mode) — session-only */
  }
}

export function logout() {
  auth.token = null;
  auth.user = null;
  try {
    localStorage.removeItem(TOKEN_KEY);
  } catch {
    /* ignore */
  }
}

export function getToken(): string | null {
  return auth.token;
}

export function isAuthenticated(): boolean {
  return !!auth.token && !!auth.user;
}
