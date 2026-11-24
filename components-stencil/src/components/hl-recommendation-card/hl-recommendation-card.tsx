import { Component, Prop, h } from '@stencil/core';

/** Recommendation card. Usage: <hl-recommendation-card type="TIMING" headline="Run earlier" message="..."></hl-recommendation-card> */
@Component({
  tag: 'hl-recommendation-card',
  shadow: true,
  styles: `
    :host { display: block; }
    .card { background: var(--surface,#fff); border: 1px solid var(--border,#e3e7ef); border-radius: 12px; padding: 1rem; }
    .top { display: flex; justify-content: space-between; align-items: center; margin-bottom: 0.4rem; }
    .badge { font-size: 0.7rem; padding: 0.1rem 0.5rem; border-radius: 999px; background: color-mix(in srgb, var(--primary,#5b5bd6) 14%, transparent); color: var(--primary,#5b5bd6); font-weight: 600; }
    .conf { font-size: 0.75rem; color: var(--text-muted,#5b6474); }
    .headline { font: 650 1.05rem var(--font,system-ui); color: var(--text,#1a1d29); }
    .msg { color: var(--text-muted,#5b6474); font-size: 0.9rem; margin-top: 0.25rem; }
  `
})
export class HlRecommendationCard {
  @Prop() type = '';
  @Prop() headline = '';
  @Prop() message = '';
  @Prop() confidence = 0;

  render() {
    return (
      <div class="card">
        <div class="top">
          <span class="badge">{this.type}</span>
          <span class="conf">{Math.round(this.confidence * 100)}%</span>
        </div>
        <div class="headline">{this.headline}</div>
        <div class="msg">{this.message}</div>
      </div>
    );
  }
}
