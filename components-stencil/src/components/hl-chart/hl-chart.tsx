import { Component, Prop, h } from '@stencil/core';

/**
 * Minimal bar chart. Usage:
 * <hl-chart points='[{"label":"W46","value":5},{"label":"W47","value":8}]'></hl-chart>
 */
@Component({
  tag: 'hl-chart',
  shadow: true,
  styles: `
    :host { display: block; }
    .chart { display: flex; align-items: flex-end; gap: 8px; height: 140px; padding-top: 8px; }
    .col { display: flex; flex-direction: column; align-items: center; justify-content: flex-end; flex: 1; gap: 4px; }
    .bar { width: 100%; max-width: 34px; background: var(--primary, #5b5bd6); border-radius: 6px 6px 0 0; min-height: 2px; }
    .v { font: 600 11px var(--font, system-ui); color: var(--text, #1a1d29); }
    .l { font: 500 10px var(--font, system-ui); color: var(--text-muted, #5b6474); }
  `
})
export class HlChart {
  @Prop() points = '[]';

  private parse(): { label: string; value: number }[] {
    try {
      return JSON.parse(this.points) || [];
    } catch {
      return [];
    }
  }

  render() {
    const data = this.parse();
    const max = Math.max(1, ...data.map((d) => d.value));
    return (
      <div class="chart">
        {data.map((d) => (
          <div class="col">
            <span class="v">{d.value}</span>
            <div class="bar" style={{ height: Math.round((d.value / max) * 110) + 'px' }}></div>
            <span class="l">{d.label}</span>
          </div>
        ))}
      </div>
    );
  }
}
