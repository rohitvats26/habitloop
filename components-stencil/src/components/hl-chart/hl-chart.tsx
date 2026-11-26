import { Component, Prop, h } from '@stencil/core';
import { parseChartPoints } from '../../utils';

/**
 * Minimal bar chart. Usage:
 * <hl-chart points='[{"label":"W46","value":5},{"label":"W47","value":8}]'></hl-chart>
 */
@Component({
  tag: 'hl-chart',
  shadow: true,
  styles: `
    :host { display: block; }
    .scroll { max-width: 100%; padding: 0 .1rem .45rem; overflow-x: auto; overscroll-behavior-x: contain; scrollbar-color: var(--border,#dce8e7) transparent; }
    .chart {
      display: flex; align-items: stretch; gap: 8px; height: 188px; padding: 10px 4px 2px;
      background: repeating-linear-gradient(to top,transparent 0,transparent calc(25% - 1px),color-mix(in srgb,var(--border,#dce8e7) 70%,transparent) calc(25% - 1px),color-mix(in srgb,var(--border,#dce8e7) 70%,transparent) 25%);
    }
    .col { display: grid; flex: 0 0 44px; grid-template-rows: 16px minmax(0,1fr) 16px; justify-items: center; gap: 4px; }
    .rail { display: flex; width: 100%; height: 100%; align-items: flex-end; justify-content: center; overflow: hidden; border-radius: 10px 10px 4px 4px; background: color-mix(in srgb,var(--primary,#087f75) 6%,var(--surface-2,#eaf4f3)); }
    .bar { width: 68%; min-height: 3px; border-radius: 9px 9px 3px 3px; background: linear-gradient(180deg,#61dfd0,var(--primary,#087f75)); box-shadow: 0 4px 12px color-mix(in srgb,var(--primary,#087f75) 20%,transparent); transition: height .35s cubic-bezier(.2,.8,.2,1); }
    .v { align-self: center; color: var(--text,#172b2b); font: 650 10px var(--font,system-ui); font-variant-numeric: tabular-nums; }
    .l { align-self: center; color: var(--text-muted,#617574); font: 550 10px var(--font,system-ui); white-space: nowrap; }
    @media (max-width: 520px) { .chart { height: 164px; } .col { flex-basis: 40px; } }
    @media (prefers-reduced-motion: reduce) { .bar { transition: none; } }
  `
})
export class HlChart {
  @Prop() points = '[]';

  private parse() {
    try {
      return parseChartPoints(this.points);
    } catch {
      return null;
    }
  }

  render() {
    const data = this.parse();
    if (data === null) {
      return <div role="alert">Chart data is invalid.</div>;
    }
    const max = Math.max(1, ...data.map((d) => d.value));
    const chartWidth = Math.max(100, data.length * 52);
    return (
      <div class="scroll">
        <div class="chart" style={{ width: chartWidth + 'px' }} role="img"
          aria-label={`Bar chart: ${data.map((point) => `${point.label}, ${point.value}`).join('; ') || 'no data points'}.`}>
          {data.map((d) => (
          <div class="col" title={`${d.label}: ${d.value}`}>
            <span class="v">{d.value}</span>
            <div class="rail">
              <div class="bar" style={{ height: Math.round((d.value / max) * 100) + '%' }}></div>
            </div>
            <span class="l">{d.label}</span>
          </div>
        ))}
        </div>
      </div>
    );
  }
}
