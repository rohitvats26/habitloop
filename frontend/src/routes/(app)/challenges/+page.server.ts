import type { PageServerLoad, Actions } from './$types';
import { fail } from '@sveltejs/kit';
import { backend, ApiError } from '$lib/server/backend';
import type { ChallengeSummary, Page } from '$lib/types';

export const load: PageServerLoad = async ({ locals }) => {
  const feed = (await backend.challenges.feed(locals.token!)) as Page<ChallengeSummary>;
  return { feed };
};

export const actions: Actions = {
  join: async ({ request, locals }) => {
    const f = await request.formData();
    try {
      await backend.challenges.join(locals.token!, String(f.get('challengeId') ?? ''));
    } catch (e) {
      const msg = e instanceof ApiError ? e.message : 'Unexpected error';
      return fail(e instanceof ApiError ? e.status : 500, { error: msg });
    }
    return { joined: true };
  }
};
