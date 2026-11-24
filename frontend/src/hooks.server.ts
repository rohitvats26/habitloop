import type { Handle } from '@sveltejs/kit';
import { redirect } from '@sveltejs/kit';
import { decodeJwt } from '$lib/jwt';

const PUBLIC_PATHS = new Set(['/login', '/register']);

export const handle: Handle = async ({ event, resolve }) => {
  const token = event.cookies.get('hl_token');
  if (token) {
    const user = decodeJwt(token);
    if (user) {
      event.locals.token = token;
      event.locals.user = user;
    } else {
      event.cookies.delete('hl_token', { path: '/' });
    }
  }

  const path = event.url.pathname;
  const isPublic = PUBLIC_PATHS.has(path);

  // Guard protected routes; bounce authenticated users away from auth pages.
  if (!event.locals.user && !isPublic) {
    throw redirect(303, '/login');
  }
  if (event.locals.user && isPublic) {
    throw redirect(303, '/dashboard');
  }

  return resolve(event);
};
