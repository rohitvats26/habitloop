import { Component, Event, EventEmitter, Prop, h } from '@stencil/core';

/** Habit summary card. Emits `hlComplete` with the habit id when the button is clicked. */
@Component({
  tag: 'hl-habit-card',
  shadow: true,
  styles: `
    :host { display: block; }
    .card { background: var(--surface,#fff); border: 1px solid var(--border,#e3e7ef); border-radius: 14px; padding: 1rem; transition: border-color .18s ease, box-shadow .18s ease, transform .18s ease; }
    .card:hover { border-color: color-mix(in srgb, var(--primary,#5b5bd6) 35%, var(--border,#e3e7ef)); box-shadow: 0 8px 24px rgba(27,35,67,.08); transform: translateY(-1px); }
    .name { font: 650 1rem var(--font,system-ui); color: var(--text,#1a1d29); }
    .meta { color: var(--text-muted,#5b6474); font-size: 0.85rem; margin: 0.3rem 0 0.6rem; }
    button { border: none; background: var(--primary,#5b5bd6); color: #fff; border-radius: 10px; padding: 0.55rem 0.9rem; font: 600 0.9rem var(--font,system-ui); cursor: pointer; transition: transform .15s ease, filter .15s ease; }
    button:hover:not(:disabled) { filter: brightness(1.08); transform: translateY(-1px); }
    button:focus-visible { outline: 3px solid color-mix(in srgb, var(--primary,#5b5bd6) 35%, transparent); outline-offset: 3px; }
    button:disabled { background: var(--surface-2,#f0efff); color: var(--success,#16845d); cursor: default; }
    @media (prefers-reduced-motion: reduce) { .card, button { transition: none; } }
  `
})
export class HlHabitCard {
  @Prop() name = '';
  @Prop() habitId = '';
  @Prop() current = 0;
  @Prop() longest = 0;
  @Prop() completed = false;
  @Prop() loading = false;
  @Event() hlComplete: EventEmitter<string>;

  render() {
    return (
      <div class="card">
        <div class="name">{this.name}</div>
        <div class="meta">current {this.current} · best {this.longest}</div>
        <button
          disabled={this.completed || this.loading}
          aria-label={this.completed ? `${this.name} completed today` : `Mark ${this.name} done today`}
          onClick={() => this.hlComplete.emit(this.habitId)}
        >
          {this.loading ? 'Saving…' : this.completed ? 'Done for today' : 'Mark done today'}
        </button>
      </div>
    );
  }
}
