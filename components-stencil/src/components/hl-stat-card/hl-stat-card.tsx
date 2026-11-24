import { Component, Prop, h } from '@stencil/core';

/** Activity summary / KPI card. Usage: <hl-stat-card label="This week" value="12"></hl-stat-card> */
@Component({
  tag: 'hl-stat-card',
  shadow: true,
  styles: `
    :host { display: block; }
    .card {
      background: var(--surface, #fff); border: 1px solid var(--border, #e3e7ef);
      border-radius: var(--radius, 12px); padding: 1rem 1.1rem;
    }
    .k { color: var(--text-muted, #5b6474); font: 500 0.85rem var(--font, system-ui); }
    .v { font: 700 2rem var(--font, system-ui); color: var(--text, #1a1d29); }
  `
})
export class HlStatCard {
  @Prop() label = '';
  @Prop() value: string | number = '';
  render() {
    return (
      <div class="card">
        <div class="k">{this.label}</div>
        <div class="v">{this.value}</div>
      </div>
    );
  }
}
