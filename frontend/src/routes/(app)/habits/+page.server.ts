import type { PageServerLoad, Actions } from './$types';
import { fail } from '@sveltejs/kit';
import { backend, ApiError } from '$lib/server/backend';
import type { Habit, Page } from '$lib/types';

export const load: PageServerLoad = async ({ locals }) => {
  const habits = (await backend.habits.list(locals.token!)) as Page<Habit>;
  return { habits };
};

function err(e: unknown) {
  const msg = e instanceof ApiError ? e.message : 'Unexpected error';
  const status = e instanceof ApiError && e.status >= 400 ? e.status : 500;
  return fail(status, { error: msg });
}

export const actions: Actions = {
  create: async ({ request, locals }) => {
    const f = await request.formData();
    const targetTime = String(f.get('targetTime') ?? '');
    const body = {
      name: String(f.get('name') ?? ''),
      description: String(f.get('description') ?? ''),
      frequency: String(f.get('frequency') ?? 'DAILY'),
      targetPerPeriod: Number(f.get('targetPerPeriod') ?? 1),
      schedule: targetTime ? { targetTime } : {}
    };
    try {
      await backend.habits.create(locals.token!, body);
    } catch (e) {
      return err(e);
    }
    return { created: true };
  },

  complete: async ({ request, locals }) => {
    const f = await request.formData();
    const id = String(f.get('habitId') ?? '');
    try {
      await backend.habits.complete(locals.token!, id, { date: new Date().toISOString().slice(0, 10) });
    } catch (e) {
      return err(e);
    }
    return { completed: true };
  },

  remove: async ({ request, locals }) => {
    const f = await request.formData();
    const id = String(f.get('habitId') ?? '');
    try {
      await backend.habits.remove(locals.token!, id);
    } catch (e) {
      return err(e);
    }
    return { removed: true };
  }
};
