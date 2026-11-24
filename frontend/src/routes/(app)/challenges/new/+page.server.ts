import type { Actions } from './$types';
import { fail, redirect } from '@sveltejs/kit';
import { backend, ApiError } from '$lib/server/backend';
import type { ChallengeSummary } from '$lib/types';

export const actions: Actions = {
  default: async ({ request, locals }) => {
    const f = await request.formData();
    const body = {
      title: String(f.get('title') ?? ''),
      description: String(f.get('description') ?? ''),
      visibility: String(f.get('visibility') ?? 'PUBLIC'),
      metric: String(f.get('metric') ?? 'DAYS_COMPLETED'),
      target: Number(f.get('target') ?? 1),
      startsOn: String(f.get('startsOn') ?? ''),
      endsOn: String(f.get('endsOn') ?? '')
    };
    let created: ChallengeSummary;
    try {
      created = (await backend.challenges.create(locals.token!, body)) as ChallengeSummary;
    } catch (e) {
      const msg = e instanceof ApiError ? e.message : 'Unexpected error';
      return fail(e instanceof ApiError ? e.status : 500, { error: msg, ...body });
    }
    throw redirect(303, `/challenges/${created.id}`);
  }
};
