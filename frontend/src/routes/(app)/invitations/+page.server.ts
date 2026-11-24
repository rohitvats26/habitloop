import type { PageServerLoad, Actions } from './$types';
import { fail } from '@sveltejs/kit';
import { backend, ApiError } from '$lib/server/backend';
import type { Invitation, Page } from '$lib/types';

export const load: PageServerLoad = async ({ locals }) => {
  const invitations = (await backend.invitations.pending(locals.token!)) as Page<Invitation>;
  return { invitations };
};

function respond(fn: (token: string, id: string) => Promise<unknown>) {
  return async ({ request, locals }: { request: Request; locals: App.Locals }) => {
    const f = await request.formData();
    try {
      await fn(locals.token!, String(f.get('id') ?? ''));
    } catch (e) {
      const msg = e instanceof ApiError ? e.message : 'Unexpected error';
      return fail(e instanceof ApiError ? e.status : 500, { error: msg });
    }
    return { ok: true };
  };
}

export const actions: Actions = {
  accept: respond(backend.invitations.accept),
  reject: respond(backend.invitations.reject)
};
