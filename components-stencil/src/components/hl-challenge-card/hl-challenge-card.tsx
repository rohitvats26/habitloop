import { Component, Prop, h } from '@stencil/core';
import { progressRatio } from '../../utils';

/** Challenge summary card with a progress bar. */
@Component({
  tag: 'hl-challenge-card',
  shadow: true,
  styles: `
    :host { display: block; }
    .card { padding: 1rem 1.1rem; border: 1px solid var(--border,#dce8e7); border-radius: 16px; background: linear-gradient(145deg,color-mix(in srgb,var(--primary,#087f75) 4%,var(--surface,#fff)),var(--surface,#fff) 70%); }
    .title { display: flex; align-items: flex-start; justify-content: space-between; gap: .75rem; color: var(--text,#172b2b); font: 700 1rem/1.35 var(--font,system-ui); }
    .badge { flex: none; padding: .2rem .55rem; border: 1px solid color-mix(in srgb,var(--primary,#087f75) 16%,transparent); border-radius: 999px; background: color-mix(in srgb,var(--primary,#087f75) 8%,var(--surface,#fff)); color: var(--primary,#087f75); font: 700 .66rem var(--font,system-ui); letter-spacing: .04em; }
    .track { height: 9px; margin-top: .9rem; overflow: hidden; border-radius: 999px; background: color-mix(in srgb,var(--primary,#087f75) 8%,var(--border,#dce8e7)); }
    .fill { height: 100%; border-radius: inherit; background: linear-gradient(90deg,var(--primary,#087f75),#54d8c8); box-shadow: 0 2px 8px color-mix(in srgb,var(--primary,#087f75) 22%,transparent); transition: width .4s cubic-bezier(.2,.8,.2,1); }
    .meta { display: flex; justify-content: space-between; gap: .5rem; margin-top: .5rem; color: var(--text-muted,#617574); font: 600 .76rem var(--font,system-ui); font-variant-numeric: tabular-nums; }
    @media (prefers-reduced-motion: reduce) { .fill { transition: none; } }
  `
})
export class HlChallengeCard {
  @Prop() challengeTitle = '';
  @Prop() visibility = 'PUBLIC';
  @Prop() progress = 0;
  @Prop() target = 1;

  render() {
    const pct = Math.round(progressRatio(this.progress, this.target) * 100);
    const maxProgress = Math.max(this.target, 0);
    const currentProgress = Math.min(Math.max(this.progress, 0), maxProgress);
    return (
      <div class="card">
        <div class="title">{this.challengeTitle} <span class="badge">{this.visibility}</span></div>
        <div class="track" role="progressbar" aria-label={`${this.challengeTitle} progress`}
          aria-valuemin="0" aria-valuemax={maxProgress} aria-valuenow={currentProgress}
          aria-valuetext={`${this.progress} of ${this.target} (${pct}%)`}>
          <div class="fill" style={{ width: pct + '%' }}></div>
        </div>
        <div class="meta">{this.progress} / {this.target} ({pct}%)</div>
      </div>
    );
  }
}
