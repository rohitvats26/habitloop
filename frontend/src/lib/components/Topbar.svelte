<script lang="ts">
  import { goto } from '$app/navigation';
  import { logout } from '$lib/auth.svelte';

  let { title, subtitle }: { title: string; subtitle?: string } = $props();

  async function doLogout() {
    logout();
    await goto('/login');
  }
</script>

<header class="topbar">
  <div class="title-group">
    <span class="eyebrow">HABITLOOP / YOUR SPACE</span>
    <h1>{title}</h1>
    {#if subtitle}<p class="subtitle">{subtitle}</p>{/if}
  </div>
  <button class="btn logout" type="button" onclick={doLogout}>
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M10 17l5-5-5-5M15 12H3" />
      <path d="M12 3h6a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-6" />
    </svg>
    Sign out
  </button>
</header>

<style>
  .topbar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 1rem;
    margin-bottom: 1.75rem;
    padding-bottom: 1.2rem;
    border-bottom: 1px solid var(--border);
  }
  .title-group { display: grid; gap: .2rem; }
  .topbar h1 { margin: 0; }
  .subtitle { margin: 0.1rem 0 0; color: var(--text-muted); font-size: .9rem; }
  .logout { border-color: var(--border); color: var(--text-muted); }
  .logout svg { width: 1rem; height: 1rem; fill: none; stroke: currentColor; stroke-linecap: round; stroke-linejoin: round; stroke-width: 1.7; }
  @media (max-width: 520px) {
    .topbar { align-items: flex-start; }
    .logout { padding: .5rem .65rem; font-size: .82rem; }
  }
</style>
