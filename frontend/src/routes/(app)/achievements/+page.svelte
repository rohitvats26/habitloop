<script lang="ts">
  import Topbar from '$lib/components/Topbar.svelte';
  import LoadingState from '$lib/components/LoadingState.svelte';
  import { onMount } from 'svelte';
  import { api, ApiError } from '$lib/api';
  import { formatIstDate } from '$lib/date';
  import { fetchAllPages } from '$lib/pagination';
  import type { Achievement } from '$lib/types';

  let achievements = $state<Achievement[]>([]);
  let loading = $state(true);
  let error = $state('');
  let pageIndex = $state(0);
  const pageSize = 24;
  const pageCount = $derived(Math.ceil(achievements.length / pageSize));
  const currentPage = $derived(Math.min(pageIndex, Math.max(0, pageCount - 1)));
  const visible = $derived(achievements.slice(currentPage * pageSize, (currentPage + 1) * pageSize));

  async function load() {
    loading = true;
    error = '';
    try {
      achievements = (await fetchAllPages(api.achievements.list))
        .sort((first, second) => second.awardedAt.localeCompare(first.awardedAt));
    } catch (e) {
      error = e instanceof Error ? e.message : 'Could not load your achievements.';
    } finally {
      loading = false;
    }
  }

  onMount(() => {
    void load();
  });

  function achievementKind(code: string): 'streak' | 'challenge' | 'goal' {
    if (code.includes('STREAK')) return 'streak';
    if (code.includes('CHALLENGE')) return 'challenge';
    return 'goal';
  }
</script>

<Topbar title="Achievements" subtitle="Celebrate the steady progress behind every habit." />

{#if error}<div class="error-banner" role="alert">{error} <button class="btn" onclick={load}>Retry</button></div>{/if}

{#if loading}
  <LoadingState message="Loading your achievements…" />
{:else if achievements.length}
  <p class="muted count">{achievements.length} achievements earned</p>
  <div class="achievement-grid">
    {#each visible as achievement (achievement.id)}
      {@const kind = achievementKind(achievement.code)}
      <article class="card achievement {kind}">
        <span class="medal" aria-hidden="true">
          <svg viewBox="0 0 24 24"><path d="M8 4h8v4a4 4 0 0 1-8 0V4ZM8 6H5v2a4 4 0 0 0 4 4m7-6h3v2a4 4 0 0 1-4 4m-3 1v4m-4 3h8m-7 0v-3h6v3" /></svg>
        </span>
        <div>
          <span class="pill {kind === 'streak' ? 'tone-accent' : kind === 'challenge' ? 'tone-violet' : 'tone-primary'}">{kind === 'streak' ? 'Streak' : kind === 'challenge' ? 'Challenge' : 'Goal'}</span>
          <h2>{achievement.title}</h2>
          <p>{achievement.description}</p>
          <small class="muted">Earned {formatIstDate(achievement.awardedAt)}</small>
        </div>
      </article>
    {/each}
  </div>
  {#if pageCount > 1}
    <nav class="pagination" aria-label="Achievement pages">
      <button class="btn" onclick={() => (pageIndex = currentPage - 1)} disabled={currentPage === 0}>Previous</button>
      <span>Page {currentPage + 1} of {pageCount}</span>
      <button class="btn" onclick={() => (pageIndex = currentPage + 1)} disabled={currentPage + 1 >= pageCount}>Next</button>
    </nav>
  {/if}
{:else if !error}
  <div class="empty-state"><div class="empty-state-copy"><strong>Your next milestone starts today</strong><p>Complete habits and take part in challenges to earn achievements.</p></div></div>
{/if}

<style>
  .achievement-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(min(100%, 300px), 1fr)); gap: 1.1rem; }
  .achievement { --medal: var(--grad-brand); --glow: var(--primary); display: flex; align-items: flex-start; gap: 1rem; }
  .achievement.streak { --medal: var(--grad-warm); --glow: var(--accent); }
  .achievement.challenge { --medal: var(--grad-violet); --glow: var(--violet); }
  .medal { display: grid; width: 56px; height: 56px; flex: none; place-items: center; border-radius: 50%; background: var(--medal); color: #fff; box-shadow: 0 0 0 4px var(--surface), 0 0 0 6px color-mix(in srgb, var(--glow) 30%, transparent), 0 10px 24px color-mix(in srgb, var(--glow) 30%, transparent); }
  .medal svg { width: 26px; height: 26px; fill: none; stroke: currentColor; stroke-width: 1.8; stroke-linecap: round; stroke-linejoin: round; }
  .achievement h2 { margin: .5rem 0 .25rem; font-size: 1.08rem; }
  .achievement p { margin: 0 0 .7rem; color: var(--text-muted); font-size: .9rem; }
  .achievement small { font-size: .76rem; }
  .count { margin: 0 0 1rem; font-size: .88rem; font-weight: 600; }
</style>
