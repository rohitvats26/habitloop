import type { PageServerLoad, Actions } from './$types';
import { fail } from '@sveltejs/kit';
import { backend, ApiError } from '$lib/server/backend';

interface UserResponse {
  id: string;
  tenantId: string;
  email: string;
  displayName?: string;
  timezone?: string;
  avatarUrl?: string;
  roles: string[];
}

export const load: PageServerLoad = async ({ locals }) => {
  const profile = (await backend.users.me(locals.token!)) as UserResponse;
  return { profile };
};

export const actions: Actions = {
  default: async ({ request, locals }) => {
    const f = await request.formData();
    const body = {
      displayName: String(f.get('displayName') ?? ''),
      timezone: String(f.get('timezone') ?? ''),
      avatarUrl: String(f.get('avatarUrl') ?? '')
    };
    try {
      await backend.users.updateMe(locals.token!, body);
    } catch (e) {
      const msg = e instanceof ApiError ? e.message : 'Unexpected error';
      return fail(e instanceof ApiError ? e.status : 500, { error: msg });
    }
    return { saved: true };
  }
};
