import { Component, Prop, h } from '@stencil/core';
import { progressRatio } from '../../utils';

/** Recommendation card. Usage: <hl-recommendation-card type="TIMING" headline="Run earlier" message="..."></hl-recommendation-card> */
@Component({
  tag: 'hl-recommendation-card',
  shadow: true,
  styles: `
    :host { display: block; }
    .card { background: var(--surface,#fff); border: 1px solid var(--border,#e3e7ef); border-radius: 14px; padding: 1rem; transition: border-color .18s ease, box-shadow .18s ease; }
    .card:hover { border-color: color-mix(in srgb, var(--primary,#5b5bd6) 28%, var(--border,#e3e7ef)); box-shadow: 0 8px 22px rgba(27,35,67,.07); }
    .top { display: flex; justify-content: space-between; align-items: center; margin-bottom: 0.4rem; }
    .badge { font-size: 0.68rem; padding: 0.18rem 0.55rem; border-radius: 999px; background: color-mix(in srgb, var(--primary,#5b5bd6) 14%, transparent); color: var(--primary,#5b5bd6); font-weight: 700; letter-spacing: .04em; }
    .conf { font-size: 0.75rem; color: var(--text-muted,#5b6474); font-variant-numeric: tabular-nums; }
    .confidence-track { height: 4px; overflow: hidden; margin: .55rem 0 .75rem; border-radius: 999px; background: var(--border,#e3e7ef); }
    .confidence-fill { height: 100%; border-radius: inherit; background: var(--primary,#5b5bd6); transition: width .3s ease; }
    .headline { font: 650 1.05rem var(--font,system-ui); color: var(--text,#1a1d29); }
    .msg { color: var(--text-muted,#5b6474); font-size: 0.9rem; margin-top: 0.25rem; }
    @media (prefers-reduced-motion: reduce) { .card, .confidence-fill { transition: none; } }
  `
})
export class HlRecommendationCard {
  @Prop() type = '';
  @Prop() headline = '';
  @Prop() message = '';
  @Prop() confidence = 0;

  render() {
    const confidence = Math.round(progressRatio(this.confidence, 1) * 100);
    return (
      <div class="card">
        <div class="top">
          <span class="badge">{this.type}</span>
          <span class="conf">{confidence}% confidence</span>
        </div>
        <div class="confidence-track" role="progressbar" aria-label="Recommendation confidence"
          aria-valuemin="0" aria-valuemax="100" aria-valuenow={confidence}>
          <div class="confidence-fill" style={{ width: confidence + '%' }}></div>
        </div>
        <div class="headline">{this.headline}</div>
        <div class="msg">{this.message}</div>
      </div>
    );
  }
}
