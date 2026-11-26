<script lang="ts">
  import Topbar from '$lib/components/Topbar.svelte';
  import LoadingState from '$lib/components/LoadingState.svelte';
  import { onMount } from 'svelte';
  import { api, ApiError } from '$lib/api';
  import { pushToast } from '$lib/stores/toast.svelte';
  import type { ChallengeDetail } from '$lib/types';
  import type { PageProps } from './$types';

  let { params }: PageProps = $props();

  let c = $state<ChallengeDetail | null>(null);
  let error = $state('');
  let loading = $state(true);
  let sendingInvite = $state(false);
  let inviteUserId = $state('');

  const id = $derived(params.id);
  const userIdPattern = /^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/;

  async function load() {
    loading = true;
    error = '';
    try {
      c = (await api.challenges.get(id)) as ChallengeDetail;
    } catch (e) {
      error = e instanceof ApiError ? e.message : 'Failed to load challenge';
    } finally {
      loading = false;
    }
  }

  onMount(() => {
    void load();
  });

  async function invite(e: SubmitEvent) {
    e.preventDefault();
    if (sendingInvite) return;
    const userId = inviteUserId.trim();
    if (!userIdPattern.test(userId)) {
      pushToast('Enter a valid user ID', 'error');
      return;
    }

    sendingInvite = true;
    try {
      await api.challenges.invite(id, { userId });
      inviteUserId = '';
      pushToast('Invitation sent', 'success');
    } catch (e) {
      const fieldError = e instanceof ApiError
        ? e.details.find((detail) => detail.startsWith('userId:'))?.replace(/^userId:\s*/, '')
        : undefined;
      pushToast(fieldError ?? (e instanceof ApiError ? e.message : 'Failed to invite'), 'error');
    } finally {
      sendingInvite = false;
    }
  }
</script>

<Topbar title={c?.title ?? 'Challenge'} subtitle="Follow progress and invite teammates to join." />
{#if error}<div class="error-banner" role="alert">{error} <button class="btn" onclick={load}>Retry</button></div>{/if}

{#if loading}
  <LoadingState message="Loading challenge details…" />
{:else if c}
  <div class="row two">
    <div class="grow">
      <hl-challenge-card
        challenge-title={c.title}
        visibility={c.visibility}
        progress={c.myProgress ?? 0}
        target={c.target}
      ></hl-challenge-card>
      <div class="card challenge-details">
        <div class="head"><span class="badge">{c.status}</span></div>
        {#if c.description}<p>{c.description}</p>{/if}
        <p class="muted">Target {c.target} · {c.startsOn} → {c.endsOn}</p>
        <div class="progress">
          <div>
            {#if c.myProgress == null}
              <strong>Not joined</strong>
              <div class="muted">Join this challenge to track your progress.</div>
            {:else}
              <strong>Your progress: {c.myProgress}/{c.target}</strong>
              <div class="muted">{c.completed ? 'Completed 🎉' : 'In progress'}</div>
            {/if}
          </div>
        </div>
      </div>
    </div>

    <div class="card grow">
      <h2>Invite a teammate</h2>
      <form onsubmit={invite}>
        <div class="field">
          <label class="label" for="userId">User ID (same tenant)</label>
          <input class="input" id="userId" bind:value={inviteUserId} required placeholder="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx" />
          <span class="field-hint">Use the teammate's user ID from Profile &amp; settings, not their email.</span>
        </div>
        <button class="btn btn-primary" type="submit" disabled={sendingInvite}>
          {sendingInvite ? 'Sending…' : 'Send invite'}
        </button>
      </form>
    </div>
  </div>

  <section class="card leaderboard">
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
      <div class="empty-state compact">
        <div class="empty-state-copy"><strong>You're first on the board</strong><p>Invite teammates to start building the leaderboard.</p></div>
    </div>
    {/if}
  </section>
{/if}

<style>
  .two { align-items: stretch; margin-bottom: 1rem; }
  .grow { flex: 1; min-width: 280px; }
  .head { display: flex; gap: 0.5rem; margin: 0.5rem 0; }
  .progress { display: flex; align-items: center; gap: 0.8rem; margin-top: 0.75rem; }
  .card { overflow-x: auto; }
  .challenge-details { margin-top: .75rem; }
  .challenge-details p { margin: .55rem 0; }
  .progress strong { color: var(--primary); font-size: 1.05rem; }
  .leaderboard { margin-top: .5rem; }
  .leaderboard table { min-width: 440px; }
  .leaderboard td:first-child { color: var(--primary); font-weight: 750; }
</style>
