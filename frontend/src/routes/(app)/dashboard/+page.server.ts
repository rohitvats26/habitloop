import type { PageServerLoad } from './$types';
import { backend } from '$lib/server/backend';
import type { AnalyticsSummary, Habit, Page, Recommendation } from '$lib/types';

async function safe<T>(p: Promise<unknown>, fallback: T): Promise<T> {
  try {
    return (await p) as T;
  } catch {
    return fallback;
  }
}

export const load: PageServerLoad = async ({ locals }) => {
  const t = locals.token!;
  const [summary, habits, recs] = await Promise.all([
    safe<AnalyticsSummary | null>(backend.analytics.summary(t), null),
    safe<Page<Habit>>(backend.habits.list(t), { content: [], page: 0, size: 0, totalElements: 0, totalPages: 0 }),
    safe<{ items: Recommendation[] }>(backend.recommendations.list(t), { items: [] })
  ]);
  return { summary, habits, recs };
};
