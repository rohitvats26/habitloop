<script lang="ts">
  import Topbar from '$lib/components/Topbar.svelte';
  import LoadingState from '$lib/components/LoadingState.svelte';
  import { onMount } from 'svelte';
  import { api, ApiError } from '$lib/api';
  import type { Achievement, Page } from '$lib/types';

  let achievements = $state<Achievement[]>([]);
  let loading = $state(true);
  let error = $state('');

  async function load() {
    loading = true;
    error = '';
    try {
      const result = await api.achievements.list();
      achievements = (result as Page<Achievement>).content ?? [];
    } catch (e) {
      error = e instanceof ApiError ? e.message : 'Could not load your achievements.';
    } finally {
      loading = false;
    }
  }

  onMount(() => {
    void load();
  });

  function formatDate(value: string) {
    return new Intl.DateTimeFormat('en-IN', { dateStyle: 'medium', timeZone: 'Asia/Kolkata' })
      .format(new Date(value));
  }
</script>

<Topbar title="Achievements" subtitle="Celebrate the steady progress behind every habit." />

{#if error}<div class="error-banner" role="alert">{error} <button class="btn" onclick={load}>Retry</button></div>{/if}

{#if loading}
  <LoadingState message="Loading your achievements…" />
{:else if achievements.length}
  <div class="achievement-grid">
    {#each achievements as achievement (achievement.id)}
      <article class="card achievement">
        <span class="achievement-mark" aria-hidden="true">
          <svg viewBox="0 0 24 24"><path d="M8 4h8v4a4 4 0 0 1-8 0V4ZM8 6H5v2a4 4 0 0 0 4 4m7-6h3v2a4 4 0 0 1-4 4m-3 1v4m-4 3h8m-7 0v-3h6v3" /></svg>
        </span>
        <div>
          <span class="badge">{achievement.code.replaceAll('_', ' ')}</span>
          <h2>{achievement.title}</h2>
          <p>{achievement.description}</p>
          <small class="muted">Earned {formatDate(achievement.awardedAt)}</small>
        </div>
      </article>
    {/each}
  </div>
{:else}
  <div class="empty-state"><div class="empty-state-copy"><strong>Your next milestone starts today</strong><p>Complete habits and take part in challenges to earn achievements.</p></div></div>
{/if}

<style>
  .achievement-grid { display: grid; grid-template-columns: repeat(auto-fit,minmax(min(100%,290px),1fr)); gap: .9rem; }
  .achievement { display: flex; align-items: flex-start; gap: .85rem; border-top: 3px solid color-mix(in srgb,var(--primary) 50%,transparent); }
  .achievement-mark { display: grid; width: 42px; height: 42px; flex: none; place-items: center; border-radius: 14px; background: color-mix(in srgb,var(--primary) 12%,var(--surface)); color: var(--primary); }
  .achievement-mark svg { width: 24px; height: 24px; fill: none; stroke: currentColor; stroke-width: 1.7; stroke-linecap: round; stroke-linejoin: round; }
  .achievement h2 { margin: .55rem 0 .25rem; font-size: 1.05rem; }
  .achievement p { margin: 0 0 .7rem; color: var(--text-muted); }
  .achievement small { font-size: .75rem; }
</style>
