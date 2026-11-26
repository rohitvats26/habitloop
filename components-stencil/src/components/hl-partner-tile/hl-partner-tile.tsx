import { Component, Event, EventEmitter, Prop, h } from '@stencil/core';

/** Partner/wearable connection tile. Emits `hlRevoke` with the connection id. */
@Component({
  tag: 'hl-partner-tile',
  shadow: true,
  styles: `
    :host { display: block; }
    .tile { padding: 1rem; border: 1px solid var(--border,#dce8e7); border-radius: 15px; background: linear-gradient(145deg,color-mix(in srgb,var(--primary,#087f75) 3%,var(--surface,#fff)),var(--surface,#fff)); }
    .row { display: flex; justify-content: space-between; align-items: center; gap: .75rem; }
    .name { color: var(--text,#172b2b); font: 700 1rem var(--font,system-ui); }
    .status { display: inline-flex; align-items: center; gap: .35rem; padding: .22rem .55rem; border-radius: 999px; background: var(--surface-2,#eaf4f3); color: var(--text-muted,#617574); font: 700 .65rem var(--font,system-ui); letter-spacing: .05em; }
    .status::before { width: 6px; height: 6px; border-radius: 50%; background: currentColor; content: ''; }
    .status.CONNECTED { background: color-mix(in srgb,var(--success,#16845d) 12%,var(--surface,#fff)); color: var(--success,#16845d); }
    .status.REVOKED { background: color-mix(in srgb,var(--danger,#c83e58) 10%,var(--surface,#fff)); color: var(--danger,#c83e58); }
    button { min-height: 38px; margin-top: .75rem; padding: .42rem .75rem; border: 1px solid color-mix(in srgb,var(--danger,#c83e58) 24%,var(--border,#dce8e7)); border-radius: 10px; background: color-mix(in srgb,var(--danger,#c83e58) 5%,var(--surface,#fff)); color: var(--danger,#c83e58); cursor: pointer; font: 600 .78rem var(--font,system-ui); transition: background .15s ease,border-color .15s ease; }
    button:hover { border-color: var(--danger,#c83e58); background: color-mix(in srgb,var(--danger,#c83e58) 10%,var(--surface,#fff)); }
    button:focus-visible { outline: 3px solid color-mix(in srgb,var(--danger,#c83e58) 30%,transparent); outline-offset: 2px; }
    @media (prefers-reduced-motion: reduce) { button { transition: none; } }
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
          <button type="button" aria-label={`Revoke ${this.partner} integration`} onClick={() => this.hlRevoke.emit(this.connectionId)}>Revoke connection</button>
        )}
      </div>
    );
  }
}
