<script lang="ts">
  import Topbar from '$lib/components/Topbar.svelte';
  import { enhance } from '$app/forms';
  let { data, form } = $props();
  const c = $derived(data.challenge);
</script>

<Topbar title={c.title} />
{#if form?.error}<div class="error-banner">{form.error}</div>{/if}
{#if form?.invited}<div class="card ok">Invitation sent.</div>{/if}

<div class="row two">
  <div class="card grow">
    <div class="head"><span class="badge">{c.visibility}</span><span class="badge">{c.status}</span></div>
    {#if c.description}<p>{c.description}</p>{/if}
    <p class="muted">Target {c.target} · {c.startsOn} → {c.endsOn}</p>
    <div class="progress">
      <hl-progress-ring value={c.myProgress ?? 0} max={c.target} label={`${c.myProgress ?? 0}/${c.target}`}></hl-progress-ring>
      <div>
        <strong>Your progress: {c.myProgress ?? 0}/{c.target}</strong>
        <div class="muted">{c.completed ? 'Completed 🎉' : 'In progress'}</div>
      </div>
    </div>
  </div>

  <div class="card grow">
    <h2>Invite a teammate</h2>
    <form method="POST" action="?/invite" use:enhance>
      <div class="field"><label class="label" for="userId">User ID (same tenant)</label><input class="input" id="userId" name="userId" required /></div>
      <button class="btn btn-primary" type="submit">Send invite</button>
    </form>
  </div>
</div>

<div class="card">
  <h2>Leaderboard</h2>
  {#if c.leaderboard.length}
    <table>
      <thead><tr><th>#</th><th>User</th><th>Progress</th><th></th></tr></thead>
      <tbody>
        {#each c.leaderboard as p, i (p.userId)}
          <tr><td>{i + 1}</td><td>{p.userId.slice(0, 8)}…</td><td>{p.progress}</td><td>{p.completed ? '✅' : ''}</td></tr>
        {/each}
      </tbody>
    </table>
  {:else}
    <p class="muted">No participants yet.</p>
  {/if}
</div>

<style>
  .two { align-items: stretch; margin-bottom: 1rem; }
  .grow { flex: 1; min-width: 280px; }
  .head { display: flex; gap: 0.5rem; margin-bottom: 0.5rem; }
  .progress { display: flex; align-items: center; gap: 0.8rem; margin-top: 0.75rem; }
  .ok { border-color: var(--success); color: var(--success); margin-bottom: 1rem; }
  table { width: 100%; border-collapse: collapse; }
  th, td { text-align: left; padding: 0.5rem 0.4rem; border-bottom: 1px solid var(--border); }
  th { color: var(--text-muted); font-size: 0.85rem; }
</style>
