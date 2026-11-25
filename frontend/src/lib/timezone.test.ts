import { describe, expect, it } from 'vitest';
import { dateStringInTimezone, isValidIanaTimezone } from './timezone';

describe('IANA timezone handling', () => {
  it('accepts a known timezone and rejects invalid values', () => {
    expect(isValidIanaTimezone('Asia/Kolkata')).toBe(true);
    expect(isValidIanaTimezone('')).toBe(false);
    expect(isValidIanaTimezone('Mars/Olympus')).toBe(false);
    expect(isValidIanaTimezone('+05:00')).toBe(false);
  });

  it('formats the calendar day in the requested timezone', () => {
    expect(dateStringInTimezone(new Date('2024-01-01T22:30:00Z'), 'Asia/Kolkata')).toBe('2024-01-02');
  });

  it('does not silently format an invalid timezone', () => {
    expect(() => dateStringInTimezone(new Date('2024-01-01T00:00:00Z'), 'Mars/Olympus')).toThrow(RangeError);
  });
});
