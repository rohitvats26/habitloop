import { Component, Prop, h } from '@stencil/core';
import { progressRatio } from '../../utils';

/** Recommendation card. Usage: <hl-recommendation-card type="TIMING" headline="Run earlier" message="..."></hl-recommendation-card> */
@Component({
  tag: 'hl-recommendation-card',
  shadow: true,
  styles: `
    :host { display: block; }
    .card {
      position: relative; overflow: hidden; padding: 1.15rem;
      border: 1px solid color-mix(in srgb, #23c8b3 22%, var(--border,#e3e7ef));
      border-radius: 18px;
      background: linear-gradient(145deg, color-mix(in srgb, #23c8b3 7%, var(--surface,#fff)), var(--surface,#fff) 58%);
      transition: border-color .18s ease, box-shadow .18s ease, transform .18s ease;
    }
    .card::before { position: absolute; inset: 0 auto 0 0; width: 3px; background: linear-gradient(180deg,#42e3d0,#6988f2); content: ''; }
    .card:hover { border-color: color-mix(in srgb, #23c8b3 48%, var(--border,#e3e7ef)); box-shadow: 0 12px 30px rgba(13,148,136,.12); transform: translateY(-2px); }
    .top { display: flex; align-items: center; gap: .7rem; margin-bottom: 1rem; }
    .icon {
      display: grid; width: 2.45rem; height: 2.45rem; flex: none; place-items: center;
      border: 1px solid color-mix(in srgb,#23c8b3 22%,transparent); border-radius: 13px;
      background: color-mix(in srgb,#23c8b3 13%,var(--surface,#fff)); color: #0f9f92;
    }
    .icon svg { width: 1.2rem; height: 1.2rem; fill: none; stroke: currentColor; stroke-linecap: round; stroke-linejoin: round; stroke-width: 1.8; }
    .category { display: grid; gap: .12rem; }
    .eyebrow { color: var(--text-muted,#5b6474); font: 700 .63rem var(--font,system-ui); letter-spacing: .11em; }
    .badge { width: fit-content; padding: .12rem .48rem; border: 1px solid color-mix(in srgb,#23c8b3 26%,transparent); border-radius: 999px; background: color-mix(in srgb,#23c8b3 10%,var(--surface,#fff)); color: #087f75; font: 700 .7rem var(--font,system-ui); }
    .headline { margin: 0; color: var(--text,#1a1d29); font: 700 1.1rem/1.3 var(--font,system-ui); letter-spacing: -.02em; }
    .msg { max-width: 58ch; margin: .35rem 0 0; color: var(--text-muted,#5b6474); font: 400 .88rem/1.55 var(--font,system-ui); }
    .signal-heading { display: flex; justify-content: space-between; gap: .75rem; margin-top: 1rem; color: var(--text-muted,#5b6474); font: 600 .72rem var(--font,system-ui); }
    .signal-heading strong { color: var(--text,#1a1d29); font-variant-numeric: tabular-nums; }
    .confidence-track { height: 5px; overflow: hidden; margin-top: .4rem; border-radius: 999px; background: color-mix(in srgb,#23c8b3 12%,var(--border,#e3e7ef)); }
    .confidence-fill { height: 100%; border-radius: inherit; background: linear-gradient(90deg,#23c8b3,#6b91f6); transition: width .3s ease; }
    .note { margin: .45rem 0 0; color: var(--text-muted,#5b6474); font-size: .7rem; }
    @media (prefers-color-scheme: dark) {
      .icon { color: #51ddcc; }
      .badge { color: #72e3d6; }
      .card:hover { box-shadow: 0 12px 30px rgba(35,200,179,.1); }
    }
    @media (max-width: 360px) {
      .card { padding: .95rem; }
    }
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
    const category = this.type.trim().toUpperCase();
    const categoryLabel = category === 'TIMING'
      ? 'Best time'
      : category === 'FREQUENCY'
        ? 'Weekly goal'
        : category === 'CONSISTENCY'
          ? 'Consistency'
          : 'Personal insight';

    return (
      <div class="card">
        <div class="top">
          <span class="icon" aria-hidden="true">
            <svg viewBox="0 0 24 24">
              {category === 'TIMING'
                ? <g><circle cx="12" cy="12" r="8.5"></circle><path d="M12 7v5l3 2"></path></g>
                : category === 'FREQUENCY'
                  ? <g><rect x="3.5" y="5" width="17" height="15" rx="2"></rect><path d="M8 3.5v3M16 3.5v3M3.5 9.5h17M8 13h2M14 13h2M8 16.5h2"></path></g>
                  : <g><path d="m12 3 1.8 5.2L19 10l-5.2 1.8L12 17l-1.8-5.2L5 10l5.2-1.8L12 3Z"></path><path d="m19 15 .8 2.2L22 18l-2.2.8L19 21l-.8-2.2L16 18l2.2-.8L19 15Z"></path></g>}
            </svg>
          </span>
          <div class="category">
            <span class="eyebrow">PERSONALIZED INSIGHT</span>
            <span class="badge">{categoryLabel}</span>
          </div>
        </div>
        <h3 class="headline">{this.headline}</h3>
        <p class="msg">{this.message}</p>
        <div class="signal-heading">
          <span>Pattern strength</span>
          <strong>{confidence}%</strong>
        </div>
        <div class="confidence-track" role="progressbar" aria-label="Recommendation pattern strength"
          aria-valuemin="0" aria-valuemax="100" aria-valuenow={confidence} aria-valuetext={`${confidence}%`}>
          <div class="confidence-fill" style={{ width: confidence + '%' }}></div>
        </div>
        <p class="note">A guide based on your recent activity—not a rule.</p>
      </div>
    );
  }
}
