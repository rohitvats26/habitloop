import { INDIA_TIMEZONE, dateStringInTimezone } from './timezone';

export async function userDateString(date = new Date()): Promise<string> {
  return dateStringInTimezone(date, INDIA_TIMEZONE);
}
