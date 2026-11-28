import { Component, Event, EventEmitter, Prop, h } from '@stencil/core';

/** Habit summary card. Emits `hlComplete` with the habit id when the button is clicked. */
@Component({
  tag: 'hl-habit-card',
  shadow: true,
  styles: `
    :host { display: block; }
    .card { display: grid; gap: .9rem; padding: 1rem; border: 1px solid var(--border,#dce8e7); border-radius: 17px; background: linear-gradient(145deg,color-mix(in srgb,var(--primary,#087f75) 4%,var(--surface,#fff)),var(--surface,#fff) 65%); transition: border-color .18s ease,box-shadow .18s ease,transform .18s ease; }
    .card:hover { border-color: color-mix(in srgb,var(--primary,#087f75) 32%,var(--border,#dce8e7)); box-shadow: 0 10px 25px color-mix(in srgb,var(--primary,#087f75) 8%,transparent); transform: translateY(-2px); }
    .top { display: flex; align-items: center; gap: .7rem; }
    .icon { display: grid; width: 2.45rem; height: 2.45rem; flex: none; place-items: center; border: 1px solid color-mix(in srgb,var(--primary,#087f75) 18%,transparent); border-radius: 13px; background: color-mix(in srgb,var(--primary,#087f75) 10%,var(--surface,#fff)); color: var(--primary,#087f75); }
    .icon svg { width: 1.3rem; height: 1.3rem; fill: none; stroke: currentColor; stroke-width: 1.7; stroke-linecap: round; stroke-linejoin: round; }
    .name { color: var(--text,#172b2b); font: 700 1rem/1.3 var(--font,system-ui); }
    .meta { margin-top: .2rem; color: var(--text-muted,#617574); font-size: .78rem; }
    .footer { display: flex; align-items: center; justify-content: space-between; gap: .7rem; }
    .streaks { display: flex; flex-wrap: wrap; align-items: center; gap: .35rem; }
    .current { color: var(--text,#172b2b); font-size: .78rem; font-weight: 650; }
    .best { padding: .18rem .45rem; border-radius: 999px; background: color-mix(in srgb,var(--warning,#b7791f) 12%,var(--surface,#fff)); color: var(--warning,#b7791f); font-size: .68rem; font-weight: 700; }
    button { flex: none; min-height: 40px; border: 0; background: var(--primary,#087f75); color: var(--primary-ink,#fff); border-radius: 11px; padding: .55rem .85rem; font: 650 .78rem var(--font,system-ui); white-space: nowrap; cursor: pointer; transition: transform .15s ease,filter .15s ease; }
    button:hover:not(:disabled) { filter: brightness(1.06); transform: translateY(-1px); }
    button:focus-visible { outline: 3px solid color-mix(in srgb,var(--primary,#087f75) 35%,transparent); outline-offset: 3px; }
    button:disabled { background: color-mix(in srgb,var(--success,#16845d) 12%,var(--surface-2,#eaf4f3)); color: var(--success,#16845d); cursor: default; }
    @media (max-width: 360px) { .footer { align-items: stretch; flex-direction: column; } button { width: 100%; } }
    @media (prefers-reduced-motion: reduce) { .card, button { transition: none; } }
  `
})
export class HlHabitCard {
  @Prop() name = '';
  @Prop() habitId = '';
  @Prop() current = 0;
  @Prop() longest = 0;
  @Prop() streakUnit: 'day' | 'week' = 'day';
  @Prop() completed = false;
  @Prop() loading = false;
  @Event() hlComplete: EventEmitter<string>;

  render() {
    return (
      <div class="card">
        <div class="top">
          <span class="icon" aria-hidden="true">
            <svg viewBox="0 0 24 24"><path d="M12.1 21s-7.3-4.4-7.3-10.2a4.2 4.2 0 0 1 7.2-3 4.2 4.2 0 0 1 7.2 3C19.2 16.6 12.1 21 12.1 21Z"></path><path d="M12 16c-1.6-1.2-2.2-2.4-1.2-3.6.1 1 1.4 1.3 1.3 2.3.8-.6 1.1-1.4.9-2.2 1.8 1.4 1.1 2.8-.2 3.5"></path></svg>
          </span>
          <div>
            <div class="name">{this.name}</div>
            <div class="meta">{this.completed ? 'Completed for today' : 'A small step, kept consistent'}</div>
          </div>
        </div>
        <div class="footer">
          <div class="streaks">
            <span class="current">{this.current} {this.streakUnit} streak</span>
            <span class="best">Best {this.longest}</span>
          </div>
          <button
            type="button"
            disabled={this.completed || this.loading}
            aria-busy={this.loading}
            aria-label={this.completed ? `${this.name} completed today` : `Mark ${this.name} done today`}
            onClick={() => this.hlComplete.emit(this.habitId)}
          >
            {this.loading ? 'Saving…' : this.completed ? 'Done today' : 'Mark done'}
          </button>
        </div>
      </div>
    );
  }
}
