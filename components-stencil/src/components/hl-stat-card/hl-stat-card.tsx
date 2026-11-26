import { Component, Prop, h } from '@stencil/core';

/** Activity summary / KPI card. Usage: <hl-stat-card label="This week" value="12"></hl-stat-card> */
@Component({
  tag: 'hl-stat-card',
  shadow: true,
  styles: `
    :host { display: block; }
    .card {
      position: relative; overflow: hidden; min-height: 112px; padding: 1rem 1.1rem;
      border: 1px solid var(--border,#dce8e7); border-radius: var(--radius,20px);
      background: linear-gradient(145deg,color-mix(in srgb,var(--primary,#087f75) 5%,var(--surface,#fff)),var(--surface,#fff) 68%);
      transition: border-color .18s ease, box-shadow .18s ease, transform .18s ease;
    }
    .card::after { position: absolute; top: -26px; right: -24px; width: 92px; height: 92px; border: 1px solid color-mix(in srgb,var(--primary,#087f75) 12%,transparent); border-radius: 50%; box-shadow: 0 0 0 12px color-mix(in srgb,var(--primary,#087f75) 3%,transparent),0 0 0 24px color-mix(in srgb,var(--primary,#087f75) 2%,transparent); content: ''; pointer-events: none; }
    .card:hover { border-color: color-mix(in srgb,var(--primary,#087f75) 32%,var(--border,#dce8e7)); box-shadow: 0 12px 28px color-mix(in srgb,var(--primary,#087f75) 9%,transparent); transform: translateY(-2px); }
    .k { position: relative; z-index: 1; color: var(--text-muted,#617574); font: 650 .77rem var(--font,system-ui); letter-spacing: .015em; }
    .v { position: relative; z-index: 1; margin-top: .45rem; color: var(--text,#172b2b); font: 750 clamp(1.8rem,3vw,2.2rem)/1.1 var(--font,system-ui); letter-spacing: -.045em; font-variant-numeric: tabular-nums; }
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
