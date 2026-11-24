<script lang="ts">
  import Topbar from '$lib/components/Topbar.svelte';
  import { goto } from '$app/navigation';
  import { api, ApiError } from '$lib/api';
  import type { ChallengeSummary } from '$lib/types';

  const today = new Date().toISOString().slice(0, 10);

  let title = $state('');
  let description = $state('');
  let visibility = $state('PUBLIC');
  let metric = $state('DAYS_COMPLETED');
  let target = $state(30);
  let startsOn = $state(today);
  let endsOn = $state('');
  let error = $state('');
  let loading = $state(false);

  async function submit(e: SubmitEvent) {
    e.preventDefault();
    error = '';
    loading = true;
    try {
      const created = (await api.challenges.create({
        title,
        description,
        visibility,
        metric,
        target: Number(target),
        startsOn,
        endsOn
      })) as ChallengeSummary;
      await goto(`/challenges/${created.id}`);
    } catch (e) {
      error = e instanceof ApiError ? e.message : 'Unexpected error';
    } finally {
      loading = false;
    }
  }
</script>

<Topbar title="Create challenge" />
{#if error}<div class="error-banner">{error}</div>{/if}

<form onsubmit={submit} class="card">
  <div class="field"><label class="label" for="title">Title</label><input class="input" id="title" bind:value={title} required /></div>
  <div class="field"><label class="label" for="description">Description</label><textarea class="input" id="description" bind:value={description} rows="2"></textarea></div>
  <div class="row">
    <div class="field"><label class="label" for="visibility">Visibility</label>
      <select class="input" id="visibility" bind:value={visibility}><option>PUBLIC</option><option>PRIVATE</option></select>
    </div>
    <div class="field"><label class="label" for="metric">Metric</label><input class="input" id="metric" bind:value={metric} /></div>
    <div class="field"><label class="label" for="target">Target</label><input class="input" id="target" type="number" min="1" bind:value={target} /></div>
  </div>
  <div class="row">
    <div class="field"><label class="label" for="startsOn">Starts</label><input class="input" id="startsOn" type="date" bind:value={startsOn} /></div>
    <div class="field"><label class="label" for="endsOn">Ends</label><input class="input" id="endsOn" type="date" bind:value={endsOn} /></div>
  </div>
  <button class="btn btn-primary" type="submit" disabled={loading}>{loading ? 'Creating…' : 'Create'}</button>
</form>
