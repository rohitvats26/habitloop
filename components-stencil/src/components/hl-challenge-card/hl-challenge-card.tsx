import { Component, Prop, h } from '@stencil/core';

/** Challenge summary card with a progress bar. */
@Component({
  tag: 'hl-challenge-card',
  shadow: true,
  styles: `
    :host { display: block; }
    .card { background: var(--surface,#fff); border: 1px solid var(--border,#e3e7ef); border-radius: 12px; padding: 1rem; }
    .title { font: 650 1rem var(--font,system-ui); color: var(--text,#1a1d29); }
    .badge { font-size: 0.7rem; padding: 0.1rem 0.5rem; border-radius: 999px; background: var(--surface-2,#f0f2f8); color: var(--text-muted,#5b6474); }
    .track { height: 8px; background: var(--border,#e3e7ef); border-radius: 999px; margin-top: 0.6rem; overflow: hidden; }
    .fill { height: 100%; background: var(--primary,#5b5bd6); }
    .meta { color: var(--text-muted,#5b6474); font-size: 0.8rem; margin-top: 0.4rem; }
  `
})
export class HlChallengeCard {
  @Prop() title = '';
  @Prop() visibility = 'PUBLIC';
  @Prop() progress = 0;
  @Prop() target = 1;

  render() {
    const pct = this.target > 0 ? Math.min(100, Math.round((this.progress / this.target) * 100)) : 0;
    return (
      <div class="card">
        <div class="title">{this.title} <span class="badge">{this.visibility}</span></div>
        <div class="track"><div class="fill" style={{ width: pct + '%' }}></div></div>
        <div class="meta">{this.progress} / {this.target} ({pct}%)</div>
      </div>
    );
  }
}
