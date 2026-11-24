import { Component, Event, EventEmitter, Prop, h } from '@stencil/core';

/** Habit summary card. Emits `hlComplete` with the habit id when the button is clicked. */
@Component({
  tag: 'hl-habit-card',
  shadow: true,
  styles: `
    :host { display: block; }
    .card { background: var(--surface,#fff); border: 1px solid var(--border,#e3e7ef); border-radius: 12px; padding: 1rem; }
    .name { font: 650 1rem var(--font,system-ui); color: var(--text,#1a1d29); }
    .meta { color: var(--text-muted,#5b6474); font-size: 0.85rem; margin: 0.3rem 0 0.6rem; }
    button { border: none; background: var(--primary,#5b5bd6); color: #fff; border-radius: 10px; padding: 0.5rem 0.9rem; font: 550 0.9rem var(--font,system-ui); cursor: pointer; }
  `
})
export class HlHabitCard {
  @Prop() name = '';
  @Prop() habitId = '';
  @Prop() current = 0;
  @Prop() longest = 0;
  @Event() hlComplete: EventEmitter<string>;

  render() {
    return (
      <div class="card">
        <div class="name">{this.name}</div>
        <div class="meta">current {this.current} · best {this.longest}</div>
        <button onClick={() => this.hlComplete.emit(this.habitId)}>Mark done today</button>
      </div>
    );
  }
}
