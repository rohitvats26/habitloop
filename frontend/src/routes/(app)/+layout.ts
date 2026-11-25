import { redirect } from '@sveltejs/kit';
import { browser } from '$app/environment';
import { auth, isAuthenticated, logout } from '$lib/auth.svelte';

// Client-side route guard for the authenticated app group.
export const load = () => {
  if (browser && !isAuthenticated()) {
    logout();
    throw redirect(307, '/login');
  }
  return { user: auth.user };
};
