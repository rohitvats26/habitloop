<script lang="ts">
  import Topbar from '$lib/components/Topbar.svelte';
  import { onMount } from 'svelte';
  import { api, ApiError } from '$lib/api';
  import { pushToast } from '$lib/stores/toast.svelte';
  import type { ChallengeSummary, Page } from '$lib/types';

  let feed = $state<ChallengeSummary[]>([]);
  let error = $state('');

  async function load() {
    try {
      const f = (await api.challenges.feed()) as Page<ChallengeSummary>;
      feed = f.content ?? [];
    } catch (e) {
      error = e instanceof ApiError ? e.message : 'Failed to load challenges';
    }
  }

  onMount(load);

  async function join(id: string) {
    try {
      await api.challenges.join(id);
      pushToast('Joined challenge', 'success');
      await load();
    } catch (e) {
      pushToast(e instanceof ApiError ? e.message : 'Failed to join', 'error');
    }
  }
</script>

<Topbar title="Challenges" />
{#if error}<div class="error-banner">{error}</div>{/if}

<div class="actions"><a class="btn btn-primary" href="/challenges/new">+ Create challenge</a></div>

<div class="grid feed">
  {#each feed as c (c.id)}
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
          <button class="btn btn-primary" onclick={() => join(c.id)}>Join</button>
        {/if}
      </div>
    </div>
  {:else}
    <p class="muted">No challenges yet. Create one to get started.</p>
  {/each}
</div>

<style>
  .actions { margin-bottom: 1rem; }
  .feed { grid-template-columns: repeat(auto-fit, minmax(280px, 1fr)); }
  .head { display: flex; align-items: center; gap: 0.5rem; }
  .meta { margin: 0.4rem 0 0.6rem; font-size: 0.85rem; }
  .ops { display: flex; gap: 0.5rem; }
</style>
