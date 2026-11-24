import { redirect } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { clearAuthCookie } from '$lib/cookies';

export const POST: RequestHandler = ({ cookies }) => {
  clearAuthCookie(cookies);
  throw redirect(303, '/login');
};
