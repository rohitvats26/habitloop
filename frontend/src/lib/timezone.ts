export const INDIA_TIMEZONE = 'Asia/Kolkata';
export const INDIA_TIMEZONE_OFFSET = '+05:30';

export function isValidIanaTimezone(timezone: string): boolean {
  const value = timezone.trim();
  if (!value || /^[+-]\d{2}:\d{2}$/.test(value)) return false;

  try {
    new Intl.DateTimeFormat('en-US', { timeZone: value });
    return true;
  } catch (error) {
    if (error instanceof RangeError) return false;
    throw error;
  }
}

export function dateStringInTimezone(date: Date, timezone: string): string {
  const value = timezone.trim();
  if (!isValidIanaTimezone(value)) {
    throw new RangeError('The configured timezone is invalid.');
  }

  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: value,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit'
  }).formatToParts(date);
  const part = (type: Intl.DateTimeFormatPartTypes) => parts.find((item) => item.type === type)?.value;
  const year = part('year');
  const month = part('month');
  const day = part('day');
  if (!year || !month || !day) throw new Error('Unable to format the date in the configured timezone.');
  return `${year}-${month}-${day}`;
}
