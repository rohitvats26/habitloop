import { redirect } from '@sveltejs/kit';
import { browser } from '$app/environment';
import { auth } from '$lib/auth.svelte';

// Client-side route guard for the authenticated app group.
export const load = () => {
  if (browser && !auth.token) {
    throw redirect(307, '/login');
  }
  return { user: auth.user };
};
