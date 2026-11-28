import { Component, Prop, h } from '@stencil/core';
import { progressRatio } from '../../utils';

/** Circular progress indicator. Usage: <hl-progress-ring value="5" max="30" label="5d"></hl-progress-ring> */
@Component({
  tag: 'hl-progress-ring',
  shadow: true,
  styles: `
    :host { display: inline-flex; vertical-align: middle; }
    .wrap { position: relative; }
    svg { display: block; transform: rotate(-90deg); }
    .track { stroke: color-mix(in srgb,var(--primary,#0a8276) 12%,var(--border,#dde8e6)); }
    .bar { stroke: url(#progress-gradient); stroke-linecap: round; transition: stroke-dashoffset 0.7s cubic-bezier(.2,.8,.2,1); }
    .label {
      position: absolute; inset: 0; display: grid; place-items: center; isolation: isolate;
      color: var(--text,#0f2422); font: 800 var(--ring-font,13px) var(--font,system-ui); font-variant-numeric: tabular-nums; letter-spacing: -.03em;
    }
    .label::after { position: absolute; inset: 18%; z-index: -1; border-radius: 50%; background: color-mix(in srgb,var(--surface,#fff) 88%,var(--primary,#0a8276) 12%); content: ''; }
    @media (prefers-reduced-motion: reduce) { .bar { transition: none; } }
  `
})
export class HlProgressRing {
  @Prop() value = 0;
  @Prop() max = 100;
  @Prop() label?: string;
  /** Rendered diameter in pixels. */
  @Prop() size = 68;
  /** Accessible name for the progress bar. */
  @Prop() ringLabel = 'Habit progress';

  render() {
    const r = 24;
    const c = 2 * Math.PI * r;
    const pct = progressRatio(this.value, this.max);
    const offset = c * (1 - pct);
    const size = Math.max(40, this.size);
    return (
      <div class="wrap" role="progressbar" aria-label={this.ringLabel} aria-valuemin="0" aria-valuemax="100"
        aria-valuenow={Math.round(pct * 100)} aria-valuetext={this.label ?? `${Math.round(pct * 100)}%`}
        style={{ width: `${size}px`, height: `${size}px`, '--ring-font': `${Math.round(size * 0.2)}px` }}>
        <svg width={size} height={size} viewBox="0 0 56 56" aria-hidden="true">
          <defs>
            <linearGradient id="progress-gradient" x1="0" y1="0" x2="1" y2="1">
              <stop offset="0%" stop-color="var(--primary,#0a8276)"></stop>
              <stop offset="100%" stop-color="#5eead4"></stop>
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
