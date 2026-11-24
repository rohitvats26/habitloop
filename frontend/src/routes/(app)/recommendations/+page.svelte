<script lang="ts">
  import Topbar from '$lib/components/Topbar.svelte';
  import { onMount } from 'svelte';
  import { api, ApiError } from '$lib/api';
  import { pushToast } from '$lib/stores/toast.svelte';
  import type { Recommendation } from '$lib/types';

  let items = $state<Recommendation[]>([]);
  const pct = (c: number) => Math.round(c * 100);

  async function load() {
    try {
      const res = (await api.recommendations.list()) as { items: Recommendation[] };
      items = res.items ?? [];
    } catch {
      items = [];
    }
  }

  onMount(load);

  // Accept a recommendation: apply the suggested timing/frequency to the habit (walkthrough step 7).
  async function accept(r: Recommendation) {
    if (!r.habitId) {
      pushToast('This suggestion is not tied to a habit', 'info');
      return;
    }
    const action = (r.action ?? {}) as { suggestedTime?: string; suggestedPerWeek?: number };
    const body: Record<string, unknown> = {};
    if (action.suggestedTime) body.schedule = { targetTime: action.suggestedTime };
    if (action.suggestedPerWeek) body.targetPerPeriod = action.suggestedPerWeek;
    if (Object.keys(body).length === 0) {
      pushToast('Nothing to apply for this suggestion', 'info');
      return;
    }
    try {
      await api.habits.update(r.habitId, body);
      pushToast('Recommendation applied to your habit', 'success');
      items = items.filter((x) => x !== r);
    } catch (e) {
      pushToast(e instanceof ApiError ? e.message : 'Failed to apply', 'error');
    }
  }
</script>

<Topbar title="Recommendations" />

<div class="grid list">
  {#each items as r}
    <div class="card rec">
      <div class="head"><span class="badge">{r.type}</span><span class="muted">{pct(r.confidence)}% confidence</span></div>
      <h2>{r.title}</h2>
      <p class="muted">{r.message}</p>
      {#if r.action?.label}
        <button class="btn btn-primary" onclick={() => accept(r)}>{r.action.label}</button>
      {/if}
    </div>
  {:else}
    <p class="muted">No recommendations yet. They appear once you have some activity history.</p>
  {/each}
</div>

<style>
  .list { grid-template-columns: repeat(auto-fit, minmax(280px, 1fr)); }
  .rec .head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 0.4rem; }
  .rec h2 { font-size: 1.05rem; }
</style>
