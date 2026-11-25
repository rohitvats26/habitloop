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
    .scroll { max-width: 100%; overflow-x: auto; overscroll-behavior-x: contain; }
    .chart { display: flex; align-items: flex-end; gap: 8px; height: 140px; padding: 8px 2px 2px; }
    .col { display: flex; flex: 0 0 38px; flex-direction: column; align-items: center; justify-content: flex-end; gap: 4px; }
    .bar { width: 100%; max-width: 30px; background: linear-gradient(180deg, color-mix(in srgb, var(--primary,#5b5bd6) 76%, white), var(--primary,#5b5bd6)); border-radius: 6px 6px 0 0; min-height: 2px; transition: height .3s ease; }
    .v { font: 600 11px var(--font, system-ui); color: var(--text, #1a1d29); }
    .l { font: 500 10px var(--font, system-ui); color: var(--text-muted, #5b6474); }
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
    const chartWidth = Math.max(100, data.length * 46);
    return (
      <div class="scroll">
        <div class="chart" style={{ width: chartWidth + 'px' }} role="img"
          aria-label={`Bar chart with ${data.length} data points. Values are also available in the table.`}>
          {data.map((d) => (
          <div class="col" title={`${d.label}: ${d.value}`}>
            <span class="v">{d.value}</span>
            <div class="bar" style={{ height: Math.round((d.value / max) * 110) + 'px' }}></div>
            <span class="l">{d.label}</span>
          </div>
        ))}
        </div>
      </div>
    );
  }
}
