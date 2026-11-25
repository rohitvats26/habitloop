<script lang="ts">
  import Topbar from '$lib/components/Topbar.svelte';
  import LoadingState from '$lib/components/LoadingState.svelte';
  import { onMount } from 'svelte';
  import { api, ApiError } from '$lib/api';
  import { pushToast } from '$lib/stores/toast.svelte';
  import type { ChallengeSummary, Page } from '$lib/types';

  let feed = $state<ChallengeSummary[]>([]);
  let error = $state('');
  let loading = $state(true);
  let joiningIds = $state<string[]>([]);
  let search = $state('');
  let visibility = $state('ALL');
  let status = $state('ALL');
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

  async function load() {
    loading = true;
    error = '';
    try {
      const f = (await api.challenges.feed()) as Page<ChallengeSummary>;
      feed = f.content ?? [];
    } catch (e) {
      error = e instanceof ApiError ? e.message : 'Failed to load challenges';
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

<div class="actions"><a class="btn btn-primary" href="/challenges/new">+ Create challenge</a></div>
<div class="card filters" aria-label="Filter challenges">
  <div class="field grow">
    <label class="label" for="challengeSearch">Search challenges</label>
    <input class="input" id="challengeSearch" bind:value={search} placeholder="Search title or description" />
  </div>
  <div class="field">
    <label class="label" for="challengeVisibility">Visibility</label>
    <select class="input" id="challengeVisibility" bind:value={visibility}>
      <option value="ALL">All visibility</option><option value="PUBLIC">Public</option><option value="PRIVATE">Private</option>
    </select>
  </div>
  <div class="field">
    <label class="label" for="challengeStatus">Status</label>
    <select class="input" id="challengeStatus" bind:value={status}>
      <option value="ALL">All statuses</option><option value="DRAFT">Draft</option><option value="ACTIVE">Active</option><option value="ENDED">Ended</option>
    </select>
  </div>
</div>

{#if loading}
  <LoadingState message="Loading challenges…" />
{:else if !error}
  <div class="grid feed">
    {#if filteredFeed.length}
      {#each filteredFeed as c (c.id)}
        <div class="card ch">
          <div class="head">
            <a href={`/challenges/${c.id}`}><strong>{c.title}</strong></a>
            <span class="badge">{c.visibility}</span>
            <span class="badge">{c.status}</span>
          </div>
          {#if c.description}<p class="muted">{c.description}</p>{/if}
          <div class="meta muted">Target {c.target} · {c.startsOn} → {c.endsOn}</div>
          <div class="ops">
            <a class="btn" href={`/challenges/${c.id}`}>View</a>
            {#if c.visibility === 'PUBLIC'}
              <button class="btn btn-primary" onclick={() => join(c.id)} disabled={joiningIds.includes(c.id)}>
                {joiningIds.includes(c.id) ? 'Joining…' : 'Join'}
              </button>
            {/if}
          </div>
        </div>
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
{/if}

<style>
  .actions { margin-bottom: 1rem; }
  .filters { display: flex; align-items: end; flex-wrap: wrap; gap: 1rem; margin-bottom: 1rem; }
  .filters .field { min-width: 160px; }
  .filters .grow { flex: 1; }
  .feed { grid-template-columns: repeat(auto-fit, minmax(280px, 1fr)); }
  .head { display: flex; align-items: center; flex-wrap: wrap; gap: 0.5rem; }
  .ch { display: flex; flex-direction: column; align-items: flex-start; }
  .meta { margin: 0.4rem 0 0.6rem; font-size: 0.85rem; }
  .ops { display: flex; gap: 0.5rem; }
</style>
