import type { PageServerLoad } from './$types';
import { backend } from '$lib/server/backend';
import type { Recommendation } from '$lib/types';

export const load: PageServerLoad = async ({ locals }) => {
  let items: Recommendation[] = [];
  try {
    const res = (await backend.recommendations.list(locals.token!)) as { items: Recommendation[] };
    items = res.items ?? [];
  } catch {
    items = [];
  }
  return { items };
};
