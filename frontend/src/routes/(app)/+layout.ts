import { redirect } from '@sveltejs/kit';
import { browser } from '$app/environment';
import { auth, isAuthenticated, logout } from '$lib/auth.svelte';
import { refreshSession } from '$lib/api';

// Client-side route guard for the authenticated app group; renews an expired access token when possible.
export const load = async () => {
  if (browser && !isAuthenticated() && !(await refreshSession())) {
    logout();
    throw redirect(307, '/login');
  }
  return { user: auth.user };
};
