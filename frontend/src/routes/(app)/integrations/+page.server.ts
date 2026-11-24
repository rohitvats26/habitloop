import type { PageServerLoad, Actions } from './$types';
import { fail } from '@sveltejs/kit';
import { backend, ApiError } from '$lib/server/backend';
import type { Connection } from '$lib/types';

export const load: PageServerLoad = async ({ locals }) => {
  const connections = (await backend.integrations.list(locals.token!)) as Connection[];
  return { connections };
};

export const actions: Actions = {
  connect: async ({ request, locals }) => {
    const f = await request.formData();
    try {
      const created = (await backend.integrations.create(locals.token!, {
        partner: String(f.get('partner') ?? 'GENERIC')
      })) as Connection;
      return { created };
    } catch (e) {
      const msg = e instanceof ApiError ? e.message : 'Unexpected error';
      return fail(e instanceof ApiError ? e.status : 500, { error: msg });
    }
  },
  revoke: async ({ request, locals }) => {
    const f = await request.formData();
    try {
      await backend.integrations.revoke(locals.token!, String(f.get('id') ?? ''));
    } catch (e) {
      const msg = e instanceof ApiError ? e.message : 'Unexpected error';
      return fail(e instanceof ApiError ? e.status : 500, { error: msg });
    }
    return { revoked: true };
  }
};
