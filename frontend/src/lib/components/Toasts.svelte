<script lang="ts">
  import { toasts, dismissToast } from '$lib/stores/toast.svelte';
</script>

<div class="toast-wrap">
  {#each toasts as t (t.id)}
    <button class="toast {t.type}" onclick={() => dismissToast(t.id)}>{t.message}</button>
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
  }
  .toast {
    border: 1px solid var(--border);
    background: var(--surface);
    color: var(--text);
    border-radius: 10px;
    padding: 0.6rem 0.9rem;
    box-shadow: var(--shadow);
    font: inherit;
    cursor: pointer;
    max-width: 320px;
    text-align: left;
  }
  .toast.success { border-color: var(--success); }
  .toast.error { border-color: var(--danger); }
</style>
