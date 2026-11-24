import { redirect } from '@sveltejs/kit';

export const load = () => {
  // The (app) guard bounces to /login if not authenticated.
  throw redirect(307, '/dashboard');
};
