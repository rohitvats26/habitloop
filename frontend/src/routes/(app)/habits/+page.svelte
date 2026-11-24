<script lang="ts">
  import Topbar from '$lib/components/Topbar.svelte';
  import { onMount } from 'svelte';
  import { api, ApiError } from '$lib/api';
  import { pushToast } from '$lib/stores/toast.svelte';
  import type { Habit, Page } from '$lib/types';

  let habits = $state<Habit[]>([]);
  let error = $state('');
  let showForm = $state(false);

  // create form
  let name = $state('');
  let description = $state('');
  let frequency = $state('DAILY');
  let targetPerPeriod = $state(1);
  let targetTime = $state('');

  async function reload() {
    try {
      const h = (await api.habits.list()) as Page<Habit>;
      habits = h.content ?? [];
    } catch (e) {
      error = e instanceof ApiError ? e.message : 'Failed to load habits';
    }
  }

  onMount(reload);

  async function create(e: SubmitEvent) {
    e.preventDefault();
    error = '';
    try {
      await api.habits.create({
        name,
        description,
        frequency,
        targetPerPeriod: Number(targetPerPeriod),
        schedule: targetTime ? { targetTime } : {}
      });
      name = description = targetTime = '';
      frequency = 'DAILY';
      targetPerPeriod = 1;
      showForm = false;
      await reload();
      pushToast('Habit created', 'success');
    } catch (e) {
      error = e instanceof ApiError ? e.message : 'Unexpected error';
    }
  }

  async function complete(id: string) {
    try {
      await api.habits.complete(id, { date: new Date().toISOString().slice(0, 10) });
      await reload();
      pushToast('Marked done for today', 'success');
    } catch (e) {
      pushToast(e instanceof ApiError ? e.message : 'Failed', 'error');
    }
  }

  async function remove(id: string) {
    try {
      await api.habits.remove(id);
      await reload();
      pushToast('Habit deleted', 'info');
    } catch (e) {
      pushToast(e instanceof ApiError ? e.message : 'Failed', 'error');
    }
  }
</script>

<Topbar title="Habits" />

{#if error}<div class="error-banner">{error}</div>{/if}

<div class="actions">
  <button class="btn btn-primary" onclick={() => (showForm = !showForm)}>
    {showForm ? 'Cancel' : '+ New habit'}
  </button>
</div>

{#if showForm}
  <form onsubmit={create} class="card create">
    <div class="row">
      <div class="field grow"><label class="label" for="name">Name</label><input class="input" id="name" bind:value={name} required /></div>
      <div class="field"><label class="label" for="frequency">Frequency</label>
        <select class="input" id="frequency" bind:value={frequency}>
          <option>DAILY</option><option>WEEKLY</option><option>CUSTOM</option>
        </select>
      </div>
    </div>
    <div class="field"><label class="label" for="description">Description</label><input class="input" id="description" bind:value={description} /></div>
    <div class="row">
      <div class="field"><label class="label" for="targetPerPeriod">Target / period</label><input class="input" id="targetPerPeriod" type="number" min="1" bind:value={targetPerPeriod} /></div>
      <div class="field"><label class="label" for="targetTime">Target time</label><input class="input" id="targetTime" type="time" bind:value={targetTime} /></div>
    </div>
    <button class="btn btn-primary" type="submit">Create habit</button>
  </form>
{/if}

<div class="grid list">
  {#each habits as h (h.id)}
    <div class="card habit">
      <div class="info">
        <div class="head"><strong>{h.name}</strong> <span class="badge">{h.frequency}</span>{#if !h.active}<span class="badge">inactive</span>{/if}</div>
        {#if h.description}<p class="muted">{h.description}</p>{/if}
        <div class="streak">
          <hl-progress-ring value={h.streak?.current ?? 0} max={Math.max(h.streak?.longest ?? 0, 7)} label={`${h.streak?.current ?? 0}`}></hl-progress-ring>
          <span class="muted">current {h.streak?.current ?? 0} · best {h.streak?.longest ?? 0}</span>
        </div>
      </div>
      <div class="ops">
        <button class="btn btn-primary" onclick={() => complete(h.id)}>Mark done today</button>
        <button class="btn btn-danger" onclick={() => remove(h.id)}>Delete</button>
      </div>
    </div>
  {:else}
    <p class="muted">No habits yet. Create your first one above.</p>
  {/each}
</div>

<style>
  .actions { margin-bottom: 1rem; }
  .create { margin-bottom: 1rem; }
  .grow { flex: 1; }
  .list { grid-template-columns: 1fr; }
  .habit { display: flex; justify-content: space-between; align-items: center; gap: 1rem; flex-wrap: wrap; }
  .head { display: flex; align-items: center; gap: 0.5rem; }
  .streak { display: flex; align-items: center; gap: 0.6rem; margin-top: 0.5rem; }
  .ops { display: flex; gap: 0.5rem; }
</style>
