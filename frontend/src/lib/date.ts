import { INDIA_TIMEZONE, dateStringInTimezone } from './timezone';
import type { ActivityLog } from './types';

export async function userDateString(date = new Date()): Promise<string> {
  return dateStringInTimezone(date, INDIA_TIMEZONE);
}

export function formatIstDate(iso: string): string {
  return new Date(iso).toLocaleDateString('en-IN', { timeZone: INDIA_TIMEZONE, dateStyle: 'medium' });
}

export function formatIstDateTime(iso: string): string {
  return new Date(iso).toLocaleString('en-IN', { timeZone: INDIA_TIMEZONE, dateStyle: 'medium', timeStyle: 'short' });
}

/** Manual habit completions are recorded per IST day, so their midnight timestamp is not a real time of day. */
export function activityWhen(activity: Pick<ActivityLog, 'activityType' | 'source' | 'occurredAt'>): string {
  return activity.activityType === 'HABIT_DONE' && activity.source === 'MANUAL'
    ? formatIstDate(activity.occurredAt)
    : formatIstDateTime(activity.occurredAt);
}
