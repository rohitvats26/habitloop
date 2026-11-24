import type { PageServerLoad } from './$types';
import { backend } from '$lib/server/backend';
import type { AnalyticsSummary } from '$lib/types';

async function safe<T>(p: Promise<unknown>, fb: T): Promise<T> {
  try {
    return (await p) as T;
  } catch {
    return fb;
  }
}

export const load: PageServerLoad = async ({ locals }) => {
  const t = locals.token!;
  const summary = await safe<AnalyticsSummary | null>(backend.analytics.summary(t), null);
  const daily = await safe<{ date: string; completions: number }[]>(backend.analytics.daily(t), []);
  return { summary, daily };
};
