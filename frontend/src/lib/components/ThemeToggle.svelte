<script lang="ts">
  import { setTheme, theme, type ThemePreference } from '$lib/stores/theme.svelte';

  let { variant = 'sidebar' }: { variant?: 'sidebar' | 'surface' } = $props();

  const options: { value: ThemePreference; label: string; icon: string }[] = [
    { value: 'light', label: 'Light', icon: 'M12 4V2m0 20v-2m8-8h2M2 12h2m13.66-5.66 1.41-1.41M4.93 19.07l1.41-1.41m0-11.32L4.93 4.93m14.14 14.14-1.41-1.41M12 7a5 5 0 1 0 0 10 5 5 0 0 0 0-10Z' },
    { value: 'dark', label: 'Dark', icon: 'M20 14.5A8.5 8.5 0 0 1 9.5 4a8.5 8.5 0 1 0 10.5 10.5Z' },
    { value: 'system', label: 'System', icon: 'M4 5h16v11H4zM9 20h6M12 16v4' }
  ];
</script>

<div class="theme-toggle {variant}" role="group" aria-label="Colour theme">
  {#each options as option (option.value)}
    <button
      type="button"
      aria-pressed={theme.preference === option.value}
      title={`${option.label} theme`}
      onclick={() => setTheme(option.value)}
    >
      <svg viewBox="0 0 24 24" aria-hidden="true"><path d={option.icon} /></svg>
      <span>{option.label}</span>
    </button>
  {/each}
</div>

<style>
  .theme-toggle { display: grid; grid-template-columns: repeat(3, 1fr); gap: .2rem; padding: .2rem; border: 1px solid rgba(255, 255, 255, 0.1); border-radius: 12px; background: rgba(255, 255, 255, 0.05); }
  button { display: inline-flex; align-items: center; justify-content: center; gap: .3rem; min-height: 32px; padding: .3rem .35rem; border: 0; border-radius: 9px; background: transparent; color: var(--sidebar-muted); font: 600 .72rem var(--font); cursor: pointer; transition: background-color .15s ease, color .15s ease; }
  button:hover { color: #fff; }
  button[aria-pressed='true'] { background: rgba(255, 255, 255, 0.14); color: #fff; box-shadow: 0 1px 3px rgba(0, 0, 0, 0.25); }
  button:focus-visible { outline: 2px solid #5eead4; outline-offset: 1px; }
  svg { width: .95rem; height: .95rem; fill: none; stroke: currentColor; stroke-width: 1.8; stroke-linecap: round; stroke-linejoin: round; }
  .surface { max-width: 360px; border-color: var(--border); background: var(--surface-2); }
  .surface button { min-height: 38px; color: var(--text-muted); font-size: .8rem; }
  .surface button:hover { color: var(--text); }
  .surface button[aria-pressed='true'] { background: var(--surface); color: var(--primary); box-shadow: var(--shadow-sm); }
  .surface button:focus-visible { outline-color: var(--primary); }
</style>
