import { Component, Prop, h } from '@stencil/core';

/** Circular progress indicator. Usage: <hl-progress-ring value="5" max="30" label="5d"></hl-progress-ring> */
@Component({
  tag: 'hl-progress-ring',
  shadow: true,
  styles: `
    :host { display: inline-block; }
    .wrap { position: relative; width: 56px; height: 56px; }
    svg { transform: rotate(-90deg); }
    .track { stroke: var(--border, #e3e7ef); }
    .bar { stroke: var(--primary, #5b5bd6); stroke-linecap: round; transition: stroke-dashoffset 0.4s ease; }
    .label {
      position: absolute; inset: 0; display: grid; place-items: center;
      font: 600 12px var(--font, system-ui); color: var(--text, #1a1d29);
    }
  `
})
export class HlProgressRing {
  @Prop() value = 0;
  @Prop() max = 100;
  @Prop() label?: string;

  render() {
    const r = 24;
    const c = 2 * Math.PI * r;
    const pct = this.max > 0 ? Math.min(1, this.value / this.max) : 0;
    const offset = c * (1 - pct);
    return (
      <div class="wrap">
        <svg width="56" height="56" viewBox="0 0 56 56">
          <circle class="track" cx="28" cy="28" r={r} fill="none" stroke-width="6" />
          <circle class="bar" cx="28" cy="28" r={r} fill="none" stroke-width="6"
            stroke-dasharray={c} stroke-dashoffset={offset} />
        </svg>
        <div class="label">{this.label ?? Math.round(pct * 100) + '%'}</div>
      </div>
    );
  }
}
