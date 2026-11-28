<script lang="ts">
  import { logout } from '$lib/auth.svelte';
  import { INDIA_TIMEZONE } from '$lib/timezone';

  let { title, subtitle }: { title: string; subtitle?: string } = $props();

  const today = new Intl.DateTimeFormat('en-IN', {
    timeZone: INDIA_TIMEZONE,
    weekday: 'long',
    day: 'numeric',
    month: 'long'
  }).format(new Date());

  function doLogout() {
    logout();
    // Full reload so no cached page data from this session stays in memory.
    window.location.assign('/login');
  }
</script>

<svelte:head><title>{title} · HabitLoop</title></svelte:head>

<header class="topbar">
  <div class="title-group">
    <span class="eyebrow"><span class="dot" aria-hidden="true"></span>{today} · IST</span>
    <h1>{title}</h1>
    {#if subtitle}<p class="subtitle">{subtitle}</p>{/if}
  </div>
  <button class="btn btn-ghost logout" type="button" onclick={doLogout}>
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
    align-items: flex-start;
    justify-content: space-between;
    gap: 1rem;
    margin-bottom: 1.75rem;
  }
  .title-group { display: grid; gap: .3rem; }
  .eyebrow { display: inline-flex; align-items: center; gap: .5rem; color: var(--primary); letter-spacing: .1em; }
  .dot { width: .5rem; height: .5rem; border-radius: 50%; background: var(--primary); box-shadow: 0 0 0 4px color-mix(in srgb, var(--primary) 18%, transparent); }
  .topbar h1 {
    margin: 0;
    font-size: clamp(1.9rem, 3.6vw, 2.7rem);
    font-weight: 800;
    letter-spacing: -.045em;
    background: linear-gradient(120deg, var(--text) 30%, color-mix(in srgb, var(--primary) 70%, var(--text)));
    -webkit-background-clip: text;
    background-clip: text;
    color: transparent;
  }
  .subtitle { max-width: 62ch; margin: 0; color: var(--text-muted); font-size: .95rem; }
  .logout { flex: none; border: 1px solid var(--border); background: var(--surface); }
  .logout:hover:not(:disabled) { color: var(--danger); border-color: color-mix(in srgb, var(--danger) 35%, var(--border)); }
  .logout svg { width: 1rem; height: 1rem; fill: none; stroke: currentColor; stroke-linecap: round; stroke-linejoin: round; stroke-width: 1.8; }
  @media (max-width: 520px) {
    .topbar { margin-bottom: 1.25rem; }
    .logout { min-height: 38px; padding: .45rem .6rem; font-size: .78rem; }
  }
  @media (forced-colors: active) {
    .topbar h1 { color: CanvasText; background: none; }
  }
</style>
