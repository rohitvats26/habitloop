import type { Cookies } from '@sveltejs/kit';
import { dev } from '$app/environment';

const NAME = 'hl_token';

export function setAuthCookie(cookies: Cookies, token: string, maxAgeSeconds: number) {
  cookies.set(NAME, token, {
    path: '/',
    httpOnly: true,
    sameSite: 'lax',
    secure: !dev,
    maxAge: maxAgeSeconds
  });
}

export function clearAuthCookie(cookies: Cookies) {
  cookies.delete(NAME, { path: '/' });
}
