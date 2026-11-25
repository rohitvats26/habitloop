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
      transition: border-color .18s ease, box-shadow .18s ease, transform .18s ease;
    }
    .card:hover { border-color: color-mix(in srgb, var(--primary,#5b5bd6) 32%, var(--border,#e3e7ef)); box-shadow: 0 10px 24px rgba(27,35,67,.08); transform: translateY(-1px); }
    .k { color: var(--text-muted, #5b6474); font: 600 0.78rem var(--font, system-ui); letter-spacing: .015em; }
    .v { margin-top: .2rem; font: 750 clamp(1.6rem, 3vw, 2rem) var(--font, system-ui); color: var(--text, #1a1d29); letter-spacing: -.035em; }
    @media (prefers-reduced-motion: reduce) { .card { transition: none; } }
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
