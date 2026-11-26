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
    position: relative;
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 1rem;
    margin-bottom: 1.6rem;
    padding-bottom: 1.25rem;
    border-bottom: 1px solid color-mix(in srgb, var(--primary) 16%, var(--border));
  }
  .title-group { display: grid; gap: .2rem; }
  .title-group .eyebrow { color: var(--primary); }
  .topbar h1 { margin: 0; font-size: clamp(1.8rem, 3.3vw, 2.45rem); letter-spacing: -.045em; }
  .subtitle { max-width: 62ch; margin: 0.15rem 0 0; color: var(--text-muted); font-size: .92rem; }
  .logout { border-color: var(--border); color: var(--text-muted); background: color-mix(in srgb, var(--surface-2) 55%, var(--surface)); }
  .logout:hover { color: var(--primary); border-color: color-mix(in srgb, var(--primary) 36%, var(--border)); }
  .logout svg { width: 1rem; height: 1rem; fill: none; stroke: currentColor; stroke-linecap: round; stroke-linejoin: round; stroke-width: 1.7; }
  @media (max-width: 520px) {
    .topbar { align-items: flex-start; margin-bottom: 1.25rem; padding-bottom: 1rem; }
    .logout { min-height: 38px; padding: .45rem .6rem; font-size: .78rem; }
  }
</style>
