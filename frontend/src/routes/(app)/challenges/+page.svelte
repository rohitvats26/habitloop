<script lang="ts">
  import Topbar from '$lib/components/Topbar.svelte';
  import LoadingState from '$lib/components/LoadingState.svelte';
  import { onMount } from 'svelte';
  import { api, ApiError } from '$lib/api';
  import { fetchAllPages } from '$lib/pagination';
  import { pushToast } from '$lib/stores/toast.svelte';
  import type { ChallengeSummary } from '$lib/types';

  let feed = $state<ChallengeSummary[]>([]);
  let error = $state('');
  let loading = $state(true);
  let joiningIds = $state<string[]>([]);
  let search = $state('');
  let visibility = $state('ALL');
  let status = $state('ALL');
  let pageIndex = $state(0);
  const pageSize = 12;
  const filteredFeed = $derived(
    feed.filter((challenge) => {
      const matchesSearch = `${challenge.title} ${challenge.description ?? ''}`
        .toLowerCase()
        .includes(search.trim().toLowerCase());
      const matchesVisibility = visibility === 'ALL' || challenge.visibility === visibility;
      const matchesStatus = status === 'ALL' || challenge.status === status;
      return matchesSearch && matchesVisibility && matchesStatus;
    })
  );
  const pageCount = $derived(Math.ceil(filteredFeed.length / pageSize));
  const currentPage = $derived(Math.min(pageIndex, Math.max(0, pageCount - 1)));
  const visibleFeed = $derived(filteredFeed.slice(currentPage * pageSize, (currentPage + 1) * pageSize));
  const todayIst = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Kolkata' }).format(new Date());

  function daysLeft(endsOn: string): number {
    return Math.round((Date.parse(`${endsOn}T00:00:00Z`) - Date.parse(`${todayIst}T00:00:00Z`)) / 86_400_000);
  }

  function shortDate(date: string): string {
    return new Date(`${date}T00:00:00Z`).toLocaleDateString('en-IN', { timeZone: 'UTC', day: 'numeric', month: 'short' });
  }

  async function load() {
    loading = true;
    error = '';
    try {
      feed = await fetchAllPages(api.challenges.feed);
    } catch (e) {
      error = e instanceof Error ? e.message : 'Failed to load challenges';
    } finally {
      loading = false;
    }
  }

  onMount(() => {
    void load();
  });

  async function join(id: string) {
    if (joiningIds.includes(id)) return;
    joiningIds = [...joiningIds, id];
    try {
      await api.challenges.join(id);
      pushToast('Joined challenge', 'success');
      await load();
    } catch (e) {
      pushToast(e instanceof ApiError ? e.message : 'Failed to join', 'error');
    } finally {
      joiningIds = joiningIds.filter((challengeId) => challengeId !== id);
    }
  }
</script>

<Topbar title="Challenges" subtitle="Build momentum together with shared goals." />
{#if error}<div class="error-banner" role="alert">{error} <button class="btn" onclick={load}>Retry</button></div>{/if}

<div class="toolbar card">
  <div class="search">
    <svg viewBox="0 0 24 24" aria-hidden="true"><path d="m21 21-4.3-4.3M11 18a7 7 0 1 0 0-14 7 7 0 0 0 0 14Z" /></svg>
    <label class="sr-only" for="challengeSearch">Search challenges</label>
    <input class="input" id="challengeSearch" bind:value={search} oninput={() => (pageIndex = 0)} placeholder="Search challenges" />
  </div>
  <label class="sr-only" for="challengeVisibility">Visibility</label>
  <select class="input compact" id="challengeVisibility" bind:value={visibility} onchange={() => (pageIndex = 0)}>
    <option value="ALL">All visibility</option><option value="PUBLIC">Public</option><option value="PRIVATE">Private</option>
  </select>
  <label class="sr-only" for="challengeStatus">Status</label>
  <select class="input compact" id="challengeStatus" bind:value={status} onchange={() => (pageIndex = 0)}>
    <option value="ALL">All statuses</option><option value="DRAFT">Draft</option><option value="ACTIVE">Active</option><option value="ENDED">Ended</option>
  </select>
  <a class="btn btn-primary" href="/challenges/new">+ Create challenge</a>
</div>

{#if loading}
  <LoadingState message="Loading challenges…" />
{:else if !error}
  <div class="grid feed">
    {#if filteredFeed.length}
      {#each visibleFeed as c (c.id)}
        {@const left = daysLeft(c.endsOn)}
        <article class="card ch" class:ended={c.status === 'ENDED'} class:private={c.visibility === 'PRIVATE'} aria-labelledby={`challenge-${c.id}`}>
          <div class="banner" aria-hidden="true">
            <svg viewBox="0 0 24 24"><path d="M8 4h8v4a4 4 0 0 1-8 0V4ZM8 6H5v2a4 4 0 0 0 4 4M16 6h3v2a4 4 0 0 1-4 4M12 12v5m-4 3h8m-7 0v-3h6v3" /></svg>
          </div>
          <div class="pills">
            <span class="pill {c.status === 'ACTIVE' ? 'tone-success' : c.status === 'ENDED' ? 'tone-muted' : 'tone-accent'}">{c.status === 'ACTIVE' ? 'Active' : c.status === 'ENDED' ? 'Ended' : 'Draft'}</span>
            <span class="pill {c.visibility === 'PRIVATE' ? 'tone-violet' : 'tone-primary'}">{c.visibility === 'PRIVATE' ? 'Private' : 'Public'}</span>
            {#if c.joined}<span class="pill tone-success">Joined</span>{/if}
          </div>
          <h2 id={`challenge-${c.id}`}><a href={`/challenges/${c.id}`}>{c.title}</a></h2>
          {#if c.description}<p class="muted desc">{c.description}</p>{/if}
          <dl class="meta">
            <div><dt>Target</dt><dd>{c.target} activities</dd></div>
            <div><dt>Dates</dt><dd>{shortDate(c.startsOn)} – {shortDate(c.endsOn)}</dd></div>
            <div><dt>Time left</dt><dd>{c.status === 'ENDED' || left < 0 ? 'Finished' : left === 0 ? 'Last day' : `${left} ${left === 1 ? 'day' : 'days'}`}</dd></div>
          </dl>
          <div class="ops">
            {#if !c.joined && c.visibility === 'PUBLIC' && c.status === 'ACTIVE'}
              <button class="btn btn-primary" onclick={() => join(c.id)} disabled={joiningIds.includes(c.id)}>
                {joiningIds.includes(c.id) ? 'Joining…' : 'Join challenge'}
              </button>
            {:else if !c.joined && c.status === 'ENDED'}
              <span class="muted closed">Joining closed</span>
            {/if}
            <a class="btn btn-ghost" href={`/challenges/${c.id}`}>View details <span aria-hidden="true">→</span></a>
          </div>
        </article>
      {/each}
    {:else if !feed.length}
      <div class="empty-state">
        <div class="empty-state-copy"><strong>No challenges yet</strong><p>Create a shared goal and invite your community to join.</p></div>
      </div>
    {:else}
      <div class="empty-state">
        <div class="empty-state-copy"><strong>No matching challenges</strong><p>Try changing your search or filters.</p></div>
      </div>
    {/if}
  </div>
  {#if pageCount > 1}
    <nav class="pagination" aria-label="Challenge pages">
      <button class="btn" onclick={() => (pageIndex = currentPage - 1)} disabled={currentPage === 0}>Previous</button>
      <span>Page {currentPage + 1} of {pageCount} · {filteredFeed.length} challenges</span>
      <button class="btn" onclick={() => (pageIndex = currentPage + 1)} disabled={currentPage + 1 >= pageCount}>Next</button>
    </nav>
  {/if}
{/if}

<style>
  .toolbar { display: flex; align-items: center; flex-wrap: wrap; gap: .6rem; margin-bottom: 1.25rem; padding: .75rem; }
  .search { position: relative; flex: 1; min-width: 220px; }
  .search svg { position: absolute; top: 50%; left: .8rem; width: 1.05rem; height: 1.05rem; transform: translateY(-50%); fill: none; stroke: var(--text-muted); stroke-width: 1.9; stroke-linecap: round; pointer-events: none; }
  .search .input { padding-left: 2.4rem; }
  .compact { width: auto; min-width: 150px; }
  .feed { grid-template-columns: repeat(auto-fill, minmax(min(100%, 320px), 1fr)); gap: 1.1rem; }
  .ch { position: relative; display: flex; flex-direction: column; gap: .7rem; overflow: hidden; padding-top: 0; }
  .banner {
    display: flex; align-items: flex-end; justify-content: flex-end;
    height: 84px; margin: 0 -1.45rem .2rem; padding: .8rem 1.1rem;
    background:
      radial-gradient(10rem 6rem at 85% 0%, rgba(255, 255, 255, 0.35), transparent 70%),
      var(--grad-brand);
  }
  .ch.private .banner { background: radial-gradient(10rem 6rem at 85% 0%, rgba(255, 255, 255, 0.35), transparent 70%), var(--grad-violet); }
  .ch.ended .banner { background: linear-gradient(135deg, var(--surface-3), var(--surface-2)); }
  .banner svg { width: 2.6rem; height: 2.6rem; fill: none; stroke: rgba(255, 255, 255, 0.85); stroke-width: 1.5; stroke-linecap: round; stroke-linejoin: round; }
  .ch.ended .banner svg { stroke: var(--text-muted); }
  .pills { display: flex; flex-wrap: wrap; gap: .35rem; }
  .ch h2 { margin: 0; font-size: 1.15rem; }
  .ch h2 a { color: var(--text); }
  .ch h2 a:hover { color: var(--primary); }
  .desc { display: -webkit-box; margin: 0; overflow: hidden; font-size: .9rem; -webkit-line-clamp: 2; line-clamp: 2; -webkit-box-orient: vertical; }
  .meta { display: grid; grid-template-columns: .9fr 1.35fr .8fr; gap: .5rem; margin: auto 0 0; padding: .75rem; border-radius: 14px; background: var(--surface-2); }
  .meta dt { color: var(--text-muted); font-size: .68rem; font-weight: 700; letter-spacing: .06em; text-transform: uppercase; }
  .meta dd { margin: .15rem 0 0; font-size: .84rem; font-weight: 650; }
  .ops { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: .5rem; }
  .ops .btn-ghost { margin-left: auto; }
  .closed { font-size: .85rem; }
  @media (max-width: 560px) { .compact { flex: 1; } .toolbar .btn-primary { width: 100%; justify-content: center; } .banner { margin: 0 -1rem .2rem; } }
</style>
