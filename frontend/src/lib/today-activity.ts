import { api } from './api';
import { completedHabitIdsForDay } from './dashboard';
import { userDateString } from './date';
import { fetchAllPages } from './pagination';
import { INDIA_TIMEZONE_OFFSET } from './timezone';

export async function loadTodayActivity(): Promise<{ date: string; completedHabitIds: string[] }> {
  const date = await userDateString();
  const activity = await fetchAllPages((page) => api.activity.log({
    type: 'HABIT_DONE',
    from: `${date}T00:00:00${INDIA_TIMEZONE_OFFSET}`,
    to: `${date}T23:59:59.999999999${INDIA_TIMEZONE_OFFSET}`,
    page,
    size: 100,
    sort: 'id,asc'
  }));
  return { date, completedHabitIds: completedHabitIdsForDay(activity) };
}
