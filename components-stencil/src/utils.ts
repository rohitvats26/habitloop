export interface ChartPoint {
  label: string;
  value: number;
}

export function progressRatio(value: number, max: number): number {
  if (!Number.isFinite(value) || !Number.isFinite(max) || max <= 0) return 0;
  return Math.min(1, Math.max(0, value / max));
}

function isChartPoint(item: unknown): item is ChartPoint {
  if (typeof item !== 'object' || item === null || Array.isArray(item)) return false;
  const point = item as Record<string, unknown>;
  return (
    typeof point.label === 'string' &&
    typeof point.value === 'number' &&
    Number.isFinite(point.value) &&
    point.value >= 0
  );
}

export function parseChartPoints(serialized: string): ChartPoint[] {
  const parsed: unknown = JSON.parse(serialized);
  if (!Array.isArray(parsed)) throw new TypeError('Chart points must be an array.');

  return parsed.map((item) => {
    if (!isChartPoint(item)) {
      throw new TypeError('Chart points must contain a label and a non-negative finite value.');
    }
    return item;
  });
}
