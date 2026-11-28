import { browser } from '$app/environment';
import { decodeJwt } from './jwt';
import type { AuthUser } from './types';

const TOKEN_KEY = 'hl_token';
const REFRESH_KEY = 'hl_refresh';

function readStored(key: string): string | null {
  if (!browser) return null;
  try {
    const token = sessionStorage.getItem(key);
    if (token && decodeJwt(token)) return token;
    sessionStorage.removeItem(key);
    localStorage.removeItem(key);
    return null;
  } catch {
    return null;
  }
}

const initial = readStored(TOKEN_KEY);

/**
 * Client-side auth state (runes). The JWT is kept in sessionStorage and sent as a Bearer header by the API
 * client. Static S3 hosting has no same-origin server for httpOnly cookies, so this remains exposed to
 * same-origin script; sessionStorage limits persistence to the current tab.
 */
export const auth = $state<{ token: string | null; user: AuthUser | null }>({
  token: initial,
  user: initial ? decodeJwt(initial) : null
});

export function setToken(token: string, refreshToken?: string) {
  auth.token = token;
  auth.user = decodeJwt(token);
  try {
    sessionStorage.setItem(TOKEN_KEY, token);
    if (refreshToken) sessionStorage.setItem(REFRESH_KEY, refreshToken);
  } catch {
    /* storage unavailable (private mode) — session-only */
  }
}

export function logout() {
  auth.token = null;
  auth.user = null;
  try {
    sessionStorage.removeItem(TOKEN_KEY);
    sessionStorage.removeItem(REFRESH_KEY);
    localStorage.removeItem(TOKEN_KEY);
  } catch {
    /* ignore */
  }
}

export function getToken(): string | null {
  return auth.token;
}

export function getRefreshToken(): string | null {
  return readStored(REFRESH_KEY);
}

export function isAuthenticated(): boolean {
  return !!auth.token && !!decodeJwt(auth.token);
}
