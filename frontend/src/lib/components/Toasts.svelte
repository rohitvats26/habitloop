<script lang="ts">
  import { toasts, dismissToast } from '$lib/stores/toast.svelte';
</script>

<div class="toast-wrap" aria-label="Notifications">
  {#each toasts as t (t.id)}
    <div class="toast {t.type}" role={t.type === 'error' ? 'alert' : 'status'} aria-atomic="true">
      <span class="toast-mark" aria-hidden="true">
        {t.type === 'success' ? '✓' : t.type === 'error' ? '!' : 'i'}
      </span>
      <span class="toast-message">{t.message}</span>
      <button class="toast-close" type="button" aria-label={`Dismiss ${t.type} notification`} onclick={() => dismissToast(t.id)}>
        <svg viewBox="0 0 20 20" aria-hidden="true"><path d="m5 5 10 10M15 5 5 15" /></svg>
      </button>
    </div>
  {/each}
</div>

<style>
  .toast-wrap {
    position: fixed;
    top: 1rem;
    right: 1rem;
    display: flex;
    flex-direction: column;
    gap: 0.5rem;
    z-index: 1000;
    width: min(390px, calc(100vw - 2rem));
  }
  .toast {
    display: flex;
    align-items: flex-start;
    gap: .65rem;
    border: 1px solid var(--border);
    background: var(--surface);
    color: var(--text);
    border-radius: 13px;
    padding: 0.75rem .8rem;
    box-shadow: var(--shadow);
    animation: toast-enter .18s ease-out both;
  }
  .toast-message { flex: 1; font-size: .88rem; line-height: 1.4; overflow-wrap: anywhere; }
  .toast-mark {
    display: grid;
    width: 1.25rem;
    height: 1.25rem;
    flex: none;
    place-items: center;
    border-radius: 50%;
    background: color-mix(in srgb, currentColor 12%, transparent);
    font-weight: 800;
    line-height: 1;
  }
  .toast.success { border-color: color-mix(in srgb, var(--success) 45%, var(--border)); }
  .toast.success .toast-mark { color: var(--success); }
  .toast.error { border-color: color-mix(in srgb, var(--danger) 45%, var(--border)); }
  .toast.error .toast-mark { color: var(--danger); }
  .toast.info .toast-mark { color: var(--primary); }
  .toast-close {
    display: grid;
    width: 1.7rem;
    height: 1.7rem;
    flex: none;
    place-items: center;
    margin: -.25rem -.25rem 0 0;
    border: 0;
    border-radius: 8px;
    background: transparent;
    color: var(--text-muted);
    cursor: pointer;
  }
  .toast-close:hover { background: var(--surface-2); color: var(--text); }
  .toast-close svg { width: 1rem; height: 1rem; fill: none; stroke: currentColor; stroke-width: 1.8; stroke-linecap: round; }
  .toast-close:focus-visible { outline: 2px solid var(--primary); outline-offset: 2px; }
  @keyframes toast-enter { from { opacity: 0; transform: translateY(-.35rem); } to { opacity: 1; transform: translateY(0); } }
  @media (prefers-reduced-motion: reduce) { .toast { animation: none; } }
</style>
