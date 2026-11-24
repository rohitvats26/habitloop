import { fail, redirect } from '@sveltejs/kit';
import type { Actions } from './$types';
import { backend, ApiError } from '$lib/server/backend';
import { setAuthCookie } from '$lib/cookies';

export const actions: Actions = {
  default: async ({ request, cookies }) => {
    const form = await request.formData();
    const email = String(form.get('email') ?? '');
    const password = String(form.get('password') ?? '');
    const tenantCode = String(form.get('tenantCode') ?? '') || undefined;

    try {
      const res = await backend.auth.login({ email, password, tenantCode });
      setAuthCookie(cookies, res.accessToken, res.expiresIn);
    } catch (e) {
      const msg = e instanceof ApiError ? e.message : 'Unexpected error';
      const status = e instanceof ApiError && e.status >= 400 ? e.status : 500;
      return fail(status, { error: msg, email });
    }
    throw redirect(303, '/dashboard');
  }
};
