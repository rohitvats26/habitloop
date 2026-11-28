<script lang="ts">
  import Topbar from '$lib/components/Topbar.svelte';
  import LoadingState from '$lib/components/LoadingState.svelte';
  import { onMount } from 'svelte';
  import { api, ApiError } from '$lib/api';
  import { auth } from '$lib/auth.svelte';
  import { canJoinChallenge, memberLabel } from '$lib/challenges';
  import { pushToast } from '$lib/stores/toast.svelte';
  import type { ChallengeDetail } from '$lib/types';
  import type { PageProps } from './$types';

  let { params }: PageProps = $props();

  let c = $state<ChallengeDetail | null>(null);
  let error = $state('');
  let loading = $state(true);
  let joining = $state(false);
  let sendingInvite = $state(false);
  let inviteUserId = $state('');
  let inviteError = $state('');

  const id = $derived(params.id);
  const joined = $derived(c?.myProgress != null);
  const userIdPattern = /^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/;

  async function load() {
    loading = true;
    error = '';
    try {
      c = await api.challenges.get(id);
    } catch (e) {
      c = null;
      error = e instanceof ApiError ? e.message : 'Failed to load challenge';
    } finally {
      loading = false;
    }
  }

  onMount(() => {
    void load();
  });

  async function join() {
    if (joining) return;
    joining = true;
    try {
      c = await api.challenges.join(id);
      pushToast('Joined challenge', 'success');
    } catch (e) {
      pushToast(e instanceof ApiError ? e.message : 'Failed to join', 'error');
      await load();
    } finally {
      joining = false;
    }
  }

  async function invite(e: SubmitEvent) {
    e.preventDefault();
    if (sendingInvite) return;
    inviteError = '';
    const userId = inviteUserId.trim();
    if (!userIdPattern.test(userId)) {
      inviteError = 'Enter a valid user ID (for example 1b9d6bcd-bbfd-4b2d-9b5d-ab8dfbbd4bed).';
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
      inviteError = fieldError ?? (e instanceof ApiError ? e.message : 'Failed to invite');
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
      <div class="card challenge-details accent-top" style={c.visibility === 'PRIVATE' ? '--stripe: var(--grad-violet)' : undefined}>
        <div class="head"><span class="badge">{c.status}</span><span class="badge">{c.visibility}</span></div>
        {#if c.description}<p>{c.description}</p>{/if}
        <p class="muted">Target {c.target} activities · {c.startsOn} → {c.endsOn} (IST, inclusive)</p>
        <div class="progress">
          {#if joined}
            <div>
              <strong>Your progress: {c.myProgress}/{c.target}</strong>
              <div class="muted">{c.completed ? 'Completed 🎉' : 'In progress'}</div>
            </div>
          {:else if canJoinChallenge(c, joined)}
            <div>
              <strong>Not joined yet</strong>
              <div class="muted">Join to start tracking your progress.</div>
            </div>
            <button class="btn btn-primary" onclick={join} disabled={joining} aria-busy={joining}>
              {joining ? 'Joining…' : 'Join challenge'}
            </button>
          {:else if c.status === 'ENDED'}
            <div><strong>This challenge has ended</strong><div class="muted">Joining is closed.</div></div>
          {:else if c.visibility === 'PRIVATE'}
            <div><strong>Invitation only</strong><div class="muted">Ask the creator for an invitation to join.</div></div>
          {:else}
            <div><strong>Not open yet</strong><div class="muted">You can join once the challenge starts on {c.startsOn}.</div></div>
          {/if}
        </div>
      </div>
    </div>

    {#if c.canInvite}
      <div class="card grow">
        <h2>Invite a teammate</h2>
        <form onsubmit={invite} novalidate>
          <div class="field">
            <label class="label" for="userId">User ID (same tenant)</label>
            <input
              class="input"
              id="userId"
              bind:value={inviteUserId}
              oninput={() => (inviteError = '')}
              required
              aria-invalid={inviteError ? 'true' : undefined}
              aria-describedby={inviteError ? 'userIdError userIdHint' : 'userIdHint'}
              placeholder="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
            />
            <span class="field-hint" id="userIdHint">Use the teammate's user ID from Profile &amp; settings, not their email.</span>
            {#if inviteError}<span class="error-text" id="userIdError" role="alert">{inviteError}</span>{/if}
          </div>
          <button class="btn btn-primary" type="submit" disabled={sendingInvite}>
            {sendingInvite ? 'Sending…' : 'Send invite'}
          </button>
        </form>
      </div>
    {/if}
  </div>

  <section class="card leaderboard" aria-labelledby="leaderboard-heading">
    <h2 id="leaderboard-heading">Leaderboard</h2>
    {#if c.leaderboard.length}
      <table>
        <thead><tr><th>#</th><th>Member</th><th>Progress</th><th><span class="sr-only">Completed</span></th></tr></thead>
        <tbody>
          {#each c.leaderboard as p, i (p.userId)}
            <tr class:me={p.userId === auth.user?.userId} aria-current={p.userId === auth.user?.userId ? 'true' : undefined}>
              <td><span class="rank rank-{i + 1}">{i + 1}</span></td>
              <td>{memberLabel(p.userId, p.displayName, auth.user?.userId)}</td>
              <td>
                <span class="progress-cell">
                  <span class="mini-bar" aria-hidden="true"><span style={`width: ${Math.min(100, (Number(p.progress) / Number(c.target)) * 100)}%`}></span></span>
                  {p.progress}/{c.target}
                </span>
              </td>
              <td>{#if p.completed}<span aria-label="Completed">✅</span>{/if}</td>
            </tr>
          {/each}
        </tbody>
      </table>
    {:else}
      <div class="empty-state compact">
        <div class="empty-state-copy"><strong>No participants yet</strong><p>Join or invite teammates to start building the leaderboard.</p></div>
      </div>
    {/if}
  </section>
{/if}

<style>
  .two { align-items: stretch; margin-bottom: 1rem; }
  .grow { flex: 1; min-width: 280px; }
  .head { display: flex; gap: 0.5rem; margin: 0.5rem 0; }
  .progress { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 0.8rem; margin-top: 0.75rem; }
  .card { overflow-x: auto; }
  .challenge-details { margin-top: .75rem; }
  .challenge-details p { margin: .55rem 0; }
  .progress strong { color: var(--primary); font-size: 1.05rem; }
  .leaderboard { margin-top: .5rem; }
  .leaderboard table { min-width: 440px; }
  .rank { display: inline-grid; width: 1.9rem; height: 1.9rem; place-items: center; border-radius: 50%; background: var(--surface-2); color: var(--text-muted); font-size: .8rem; font-weight: 800; }
  .rank-1 { background: var(--grad-warm); color: #2b1300; }
  .rank-2 { background: linear-gradient(135deg, #cbd5e1, #94a3b8); color: #0f172a; }
  .rank-3 { background: linear-gradient(135deg, #f0b27a, #c2703d); color: #2b1300; }
  .progress-cell { display: inline-flex; align-items: center; gap: .6rem; font-variant-numeric: tabular-nums; }
  .mini-bar { width: 90px; height: 7px; overflow: hidden; border-radius: 999px; background: var(--surface-2); }
  .mini-bar span { display: block; height: 100%; border-radius: inherit; background: var(--grad-brand); }
  @media (max-width: 560px) { .leaderboard table { min-width: 0; } .mini-bar { display: none; } }
  .leaderboard tr.me td { background: color-mix(in srgb,var(--primary) 9%,var(--surface)); font-weight: 650; }
  .error-text { display: block; margin-top: .35rem; color: var(--danger); font-size: .82rem; }
  .sr-only { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); white-space: nowrap; }
</style>
