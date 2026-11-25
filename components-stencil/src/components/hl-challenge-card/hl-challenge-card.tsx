import { Component, Prop, h } from '@stencil/core';
import { progressRatio } from '../../utils';

/** Challenge summary card with a progress bar. */
@Component({
  tag: 'hl-challenge-card',
  shadow: true,
  styles: `
    :host { display: block; }
    .card { background: var(--surface,#fff); border: 1px solid var(--border,#e3e7ef); border-radius: 14px; padding: 1rem; }
    .title { font: 650 1rem var(--font,system-ui); color: var(--text,#1a1d29); }
    .badge { font-size: 0.7rem; padding: 0.1rem 0.5rem; border-radius: 999px; background: var(--surface-2,#f0f2f8); color: var(--text-muted,#5b6474); }
    .track { height: 8px; background: var(--border,#e3e7ef); border-radius: 999px; margin-top: 0.6rem; overflow: hidden; }
    .fill { height: 100%; background: var(--primary,#5b5bd6); border-radius: inherit; transition: width .35s ease; }
    .meta { color: var(--text-muted,#5b6474); font-size: 0.8rem; margin-top: 0.4rem; }
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
