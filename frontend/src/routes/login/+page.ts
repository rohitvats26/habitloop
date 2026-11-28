import { redirect } from '@sveltejs/kit';
import { browser } from '$app/environment';
import { isAuthenticated } from '$lib/auth.svelte';

export const load = ({ url }) => {
  if (browser && isAuthenticated()) {
    throw redirect(307, '/dashboard');
  }
  return {
    expired: url.searchParams.get('expired') === '1',
    registered: url.searchParams.get('registered') === '1'
  };
};
