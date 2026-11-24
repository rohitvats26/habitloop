<script lang="ts">
  import Topbar from '$lib/components/Topbar.svelte';
  import { enhance } from '$app/forms';
  let { data, form } = $props();
  let showForm = $state(false);
</script>

<Topbar title="Habits" />

{#if form?.error}<div class="error-banner">{form.error}</div>{/if}

<div class="actions">
  <button class="btn btn-primary" onclick={() => (showForm = !showForm)}>
    {showForm ? 'Cancel' : '+ New habit'}
  </button>
</div>

{#if showForm}
  <form method="POST" action="?/create" use:enhance={() => async ({ update }) => { await update(); showForm = false; }} class="card create">
    <div class="row">
      <div class="field grow"><label class="label" for="name">Name</label><input class="input" id="name" name="name" required /></div>
      <div class="field"><label class="label" for="frequency">Frequency</label>
        <select class="input" id="frequency" name="frequency">
          <option>DAILY</option><option>WEEKLY</option><option>CUSTOM</option>
        </select>
      </div>
    </div>
    <div class="field"><label class="label" for="description">Description</label><input class="input" id="description" name="description" /></div>
    <div class="row">
      <div class="field"><label class="label" for="targetPerPeriod">Target / period</label><input class="input" id="targetPerPeriod" name="targetPerPeriod" type="number" min="1" value="1" /></div>
      <div class="field"><label class="label" for="targetTime">Target time</label><input class="input" id="targetTime" name="targetTime" type="time" /></div>
    </div>
    <button class="btn btn-primary" type="submit">Create habit</button>
  </form>
{/if}

<div class="grid list">
  {#each data.habits.content as h (h.id)}
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
        <form method="POST" action="?/complete" use:enhance>
          <input type="hidden" name="habitId" value={h.id} />
          <button class="btn btn-primary" type="submit">Mark done today</button>
        </form>
        <form method="POST" action="?/remove" use:enhance>
          <input type="hidden" name="habitId" value={h.id} />
          <button class="btn btn-danger" type="submit">Delete</button>
        </form>
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
