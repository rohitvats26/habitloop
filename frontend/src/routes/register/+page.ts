import { redirect } from '@sveltejs/kit';
import { browser } from '$app/environment';
import { isAuthenticated } from '$lib/auth.svelte';

export const load = () => {
  if (browser && isAuthenticated()) {
    throw redirect(307, '/dashboard');
  }
};
