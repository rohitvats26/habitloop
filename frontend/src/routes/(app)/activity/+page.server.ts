import type { PageServerLoad } from './$types';
import { backend } from '$lib/server/backend';
import type { ActivityLog, Page } from '$lib/types';

export const load: PageServerLoad = async ({ locals }) => {
  const activity = (await backend.activity.log(locals.token!)) as Page<ActivityLog>;
  return { activity };
};
