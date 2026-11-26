import { Component, Prop, h } from '@stencil/core';
import { progressRatio } from '../../utils';

/** Circular progress indicator. Usage: <hl-progress-ring value="5" max="30" label="5d"></hl-progress-ring> */
@Component({
  tag: 'hl-progress-ring',
  shadow: true,
  styles: `
    :host { display: inline-flex; vertical-align: middle; }
    .wrap { position: relative; width: 68px; height: 68px; }
    svg { display: block; transform: rotate(-90deg); }
    .track { stroke: color-mix(in srgb,var(--primary,#087f75) 9%,var(--border,#dce8e7)); }
    .bar { stroke: url(#progress-gradient); stroke-linecap: round; transition: stroke-dashoffset 0.55s cubic-bezier(.2,.8,.2,1); }
    .label {
      position: absolute; inset: 0; display: grid; place-items: center; isolation: isolate;
      color: var(--text,#172b2b); font: 750 13px var(--font,system-ui); font-variant-numeric: tabular-nums;
    }
    .label::after { position: absolute; inset: 11px; z-index: -1; border-radius: 50%; background: color-mix(in srgb,var(--surface,#fff) 90%,var(--primary,#087f75) 10%); content: ''; }
    @media (prefers-reduced-motion: reduce) { .bar { transition: none; } }
  `
})
export class HlProgressRing {
  @Prop() value = 0;
  @Prop() max = 100;
  @Prop() label?: string;

  render() {
    const r = 24;
    const c = 2 * Math.PI * r;
    const pct = progressRatio(this.value, this.max);
    const offset = c * (1 - pct);
    return (
      <div class="wrap" role="progressbar" aria-label="Habit progress" aria-valuemin="0" aria-valuemax="100"
        aria-valuenow={Math.round(pct * 100)} aria-valuetext={this.label ?? `${Math.round(pct * 100)}%`}>
        <svg width="68" height="68" viewBox="0 0 56 56" aria-hidden="true">
          <defs>
            <linearGradient id="progress-gradient" x1="0" y1="0" x2="1" y2="1">
              <stop offset="0%" stop-color="var(--primary,#087f75)"></stop>
              <stop offset="100%" stop-color="#43d5c3"></stop>
            </linearGradient>
          </defs>
          <circle class="track" cx="28" cy="28" r={r} fill="none" stroke-width="5" />
          <circle class="bar" cx="28" cy="28" r={r} fill="none" stroke-width="5"
            stroke-dasharray={c} stroke-dashoffset={offset} />
        </svg>
        <div class="label">{this.label ?? Math.round(pct * 100) + '%'}</div>
      </div>
    );
  }
}
