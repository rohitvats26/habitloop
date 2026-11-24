import { Component, Event, EventEmitter, Prop, h } from '@stencil/core';

/** Partner/wearable connection tile. Emits `hlRevoke` with the connection id. */
@Component({
  tag: 'hl-partner-tile',
  shadow: true,
  styles: `
    :host { display: block; }
    .tile { background: var(--surface,#fff); border: 1px solid var(--border,#e3e7ef); border-radius: 12px; padding: 1rem; }
    .row { display: flex; justify-content: space-between; align-items: center; }
    .name { font: 650 1rem var(--font,system-ui); color: var(--text,#1a1d29); }
    .status { font-size: 0.72rem; padding: 0.1rem 0.5rem; border-radius: 999px; background: var(--surface-2,#f0f2f8); color: var(--text-muted,#5b6474); }
    .status.CONNECTED { color: var(--success,#2f9e63); }
    .status.REVOKED { color: var(--danger,#d1435b); }
    button { margin-top: 0.6rem; border: 1px solid var(--border,#e3e7ef); background: var(--surface,#fff); color: var(--danger,#d1435b); border-radius: 10px; padding: 0.4rem 0.8rem; cursor: pointer; font: 550 0.85rem var(--font,system-ui); }
  `
})
export class HlPartnerTile {
  @Prop() partner = '';
  @Prop() status = 'CONNECTED';
  @Prop() connectionId = '';
  @Event() hlRevoke: EventEmitter<string>;

  render() {
    return (
      <div class="tile">
        <div class="row">
          <span class="name">{this.partner}</span>
          <span class={'status ' + this.status}>{this.status}</span>
        </div>
        {this.status === 'CONNECTED' && (
          <button onClick={() => this.hlRevoke.emit(this.connectionId)}>Revoke</button>
        )}
      </div>
    );
  }
}
