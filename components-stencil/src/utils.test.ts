import { describe, expect, it } from 'vitest';
import { parseChartPoints, progressRatio } from './utils';

describe('progressRatio', () => {
  it('clamps progress to the inclusive zero-to-one range', () => {
    expect(progressRatio(5, 10)).toBe(0.5);
    expect(progressRatio(-1, 10)).toBe(0);
    expect(progressRatio(12, 10)).toBe(1);
  });

  it('returns zero for invalid ranges and non-finite values', () => {
    expect(progressRatio(5, 0)).toBe(0);
    expect(progressRatio(Number.NaN, 10)).toBe(0);
    expect(progressRatio(5, Number.POSITIVE_INFINITY)).toBe(0);
  });
});

describe('parseChartPoints', () => {
  it('parses valid chart data', () => {
    expect(parseChartPoints('[{"label":"W46","value":5}]')).toEqual([{ label: 'W46', value: 5 }]);
  });

  it('rejects invalid JSON and malformed or negative points', () => {
    expect(() => parseChartPoints('not json')).toThrow();
    expect(() => parseChartPoints('{"label":"W46","value":5}')).toThrow(TypeError);
    expect(() => parseChartPoints('[{"label":"W46","value":-1}]')).toThrow(TypeError);
  });
});
