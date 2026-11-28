import { Component, Prop, h } from '@stencil/core';

const ICONS: Record<string, string> = {
  check: 'M5 12.5 10 17.5 19 7',
  calendar: 'M4 6h16v14H4zM8 3v4M16 3v4M4 10h16',
  flame: 'M12 21c4 0 7-2.7 7-6.6 0-3.1-2-5.4-3.6-7.2-.4 1.8-1.4 3-2.6 3.6.3-3.3-1.2-6-3.8-7.8.2 3.2-1.5 5-3 6.8C4.8 11.3 5 12.9 5 14.4 5 18.3 8 21 12 21Z',
  target: 'M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18Zm0-4a5 5 0 1 0 0-10 5 5 0 0 0 0 10Zm0-4a1 1 0 1 0 0-2 1 1 0 0 0 0 2Z',
  spark: 'm12 3 1.8 5.2L19 10l-5.2 1.8L12 17l-1.8-5.2L5 10l5.2-1.8L12 3Z'
};

const TONES: Record<string, string> = {
  primary: 'var(--primary,#0a8276)',
  accent: 'var(--accent,#e8890c)',
  violet: 'var(--violet,#6d5ae6)',
  success: 'var(--success,#138a5e)'
};

/** Activity summary / KPI card. Usage: <hl-stat-card label="This week" value="12" tone="accent" icon="flame"></hl-stat-card> */
@Component({
  tag: 'hl-stat-card',
  shadow: true,
  styles: `
    :host { display: block; }
    .card {
      position: relative; display: grid; gap: .55rem; overflow: hidden; min-height: 124px; padding: 1.1rem 1.2rem;
      border: 1px solid var(--border,#dde8e6); border-radius: var(--radius,22px);
      background: var(--surface,#fff); box-shadow: var(--shadow,0 12px 32px rgba(15,52,49,.08));
      transition: border-color .18s ease, box-shadow .18s ease, transform .18s ease;
    }
    .card::after {
      position: absolute; top: -40px; right: -40px; width: 130px; height: 130px; border-radius: 50%;
      background: radial-gradient(circle, color-mix(in srgb,var(--tone) 22%,transparent), transparent 70%); content: ''; pointer-events: none;
    }
    .card:hover { border-color: color-mix(in srgb,var(--tone) 35%,var(--border,#dde8e6)); transform: translateY(-2px); }
    .top { display: flex; align-items: center; justify-content: space-between; gap: .75rem; }
    .k { color: var(--text-muted,#5a6e6c); font: 650 .8rem var(--font,system-ui); }
    .icon { display: grid; width: 2.2rem; height: 2.2rem; place-items: center; border-radius: 12px; background: color-mix(in srgb,var(--tone) 15%,var(--surface,#fff)); color: var(--tone); }
    .icon svg { width: 1.15rem; height: 1.15rem; fill: none; stroke: currentColor; stroke-width: 1.9; stroke-linecap: round; stroke-linejoin: round; }
    .v { position: relative; z-index: 1; color: var(--text,#0f2422); font: 800 clamp(1.7rem,3vw,2.2rem)/1.05 var(--font,system-ui); letter-spacing: -.045em; font-variant-numeric: tabular-nums; }
    .hint { position: relative; z-index: 1; color: var(--text-muted,#5a6e6c); font: 500 .76rem var(--font,system-ui); }
    @media (prefers-reduced-motion: reduce) { .card { transition: none; } }
  `
})
export class HlStatCard {
  @Prop() label = '';
  @Prop() value: string | number = '';
  @Prop() hint?: string;
  @Prop() tone: 'primary' | 'accent' | 'violet' | 'success' = 'primary';
  @Prop() icon?: 'check' | 'calendar' | 'flame' | 'target' | 'spark';

  render() {
    const path = this.icon ? ICONS[this.icon] : undefined;
    return (
      <div class="card" style={{ '--tone': TONES[this.tone] ?? TONES.primary }}>
        <div class="top">
          <div class="k">{this.label}</div>
          {path && <span class="icon" aria-hidden="true"><svg viewBox="0 0 24 24"><path d={path}></path></svg></span>}
        </div>
        <div class="v">{this.value}</div>
        {this.hint && <div class="hint">{this.hint}</div>}
      </div>
    );
  }
}
