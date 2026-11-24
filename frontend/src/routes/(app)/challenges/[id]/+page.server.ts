import type { PageServerLoad, Actions } from './$types';
import { fail } from '@sveltejs/kit';
import { backend, ApiError } from '$lib/server/backend';
import type { ChallengeDetail } from '$lib/types';

export const load: PageServerLoad = async ({ locals, params }) => {
  const challenge = (await backend.challenges.get(locals.token!, params.id)) as ChallengeDetail;
  return { challenge };
};

export const actions: Actions = {
  invite: async ({ request, locals, params }) => {
    const f = await request.formData();
    const userId = String(f.get('userId') ?? '');
    try {
      await backend.challenges.invite(locals.token!, params.id, { userId });
    } catch (e) {
      const msg = e instanceof ApiError ? e.message : 'Unexpected error';
      return fail(e instanceof ApiError ? e.status : 500, { error: msg });
    }
    return { invited: true };
  }
};
