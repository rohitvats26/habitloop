import { fail, redirect } from '@sveltejs/kit';
import type { Actions } from './$types';
import { backend, ApiError } from '$lib/server/backend';
import { setAuthCookie } from '$lib/cookies';

export const actions: Actions = {
  default: async ({ request, cookies }) => {
    const form = await request.formData();
    const email = String(form.get('email') ?? '');
    const password = String(form.get('password') ?? '');
    const displayName = String(form.get('displayName') ?? '');
    const timezone = String(form.get('timezone') ?? 'UTC');
    const mode = String(form.get('mode') ?? 'create');
    const tenantName = mode === 'create' ? String(form.get('tenantName') ?? '') : undefined;
    const joinCode = mode === 'join' ? String(form.get('joinCode') ?? '') : undefined;

    const payload = { email, password, displayName, timezone, tenantName, joinCode };
    try {
      await backend.auth.register(payload);
      const res = await backend.auth.login({ email, password });
      setAuthCookie(cookies, res.accessToken, res.expiresIn);
    } catch (e) {
      const msg = e instanceof ApiError ? e.message : 'Unexpected error';
      const status = e instanceof ApiError && e.status >= 400 ? e.status : 500;
      return fail(status, { error: msg, email, displayName });
    }
    throw redirect(303, '/onboarding');
  }
};
