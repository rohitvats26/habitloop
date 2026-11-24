<script lang="ts">
  import Topbar from '$lib/components/Topbar.svelte';
  import { onMount } from 'svelte';
  import { page } from '$app/stores';
  import { api, ApiError } from '$lib/api';
  import { pushToast } from '$lib/stores/toast.svelte';
  import type { ChallengeDetail } from '$lib/types';

  let c = $state<ChallengeDetail | null>(null);
  let error = $state('');
  let inviteUserId = $state('');

  const id = $derived($page.params.id);

  async function load() {
    try {
      c = (await api.challenges.get(id)) as ChallengeDetail;
    } catch (e) {
      error = e instanceof ApiError ? e.message : 'Failed to load challenge';
    }
  }

  onMount(load);

  async function invite(e: SubmitEvent) {
    e.preventDefault();
    try {
      await api.challenges.invite(id, { userId: inviteUserId });
      inviteUserId = '';
      pushToast('Invitation sent', 'success');
    } catch (e) {
      pushToast(e instanceof ApiError ? e.message : 'Failed to invite', 'error');
    }
  }
</script>

<Topbar title={c?.title ?? 'Challenge'} />
{#if error}<div class="error-banner">{error}</div>{/if}

{#if c}
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
      <form onsubmit={invite}>
        <div class="field"><label class="label" for="userId">User ID (same tenant)</label><input class="input" id="userId" bind:value={inviteUserId} required /></div>
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
{:else if !error}
  <p class="muted">Loading…</p>
{/if}

<style>
  .two { align-items: stretch; margin-bottom: 1rem; }
  .grow { flex: 1; min-width: 280px; }
  .head { display: flex; gap: 0.5rem; margin-bottom: 0.5rem; }
  .progress { display: flex; align-items: center; gap: 0.8rem; margin-top: 0.75rem; }
  table { width: 100%; border-collapse: collapse; }
  th, td { text-align: left; padding: 0.5rem 0.4rem; border-bottom: 1px solid var(--border); }
  th { color: var(--text-muted); font-size: 0.85rem; }
</style>
