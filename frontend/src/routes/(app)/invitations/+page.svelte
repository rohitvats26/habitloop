<script lang="ts">
  import Topbar from '$lib/components/Topbar.svelte';
  import LoadingState from '$lib/components/LoadingState.svelte';
  import { goto } from '$app/navigation';
  import { onMount } from 'svelte';
  import { api, ApiError } from '$lib/api';
  import { memberLabel } from '$lib/challenges';
  import { fetchAllPages } from '$lib/pagination';
  import { pushToast } from '$lib/stores/toast.svelte';
  import { refreshPendingInvitations, setPendingInvitationCount } from '$lib/stores/invitations.svelte';
  import type { Invitation } from '$lib/types';

  let invitations = $state<Invitation[]>([]);
  let error = $state('');
  let loading = $state(true);
  let processingIds = $state<string[]>([]);

  async function load() {
    loading = true;
    error = '';
    try {
      invitations = await fetchAllPages(api.invitations.pending);
      setPendingInvitationCount(invitations.length);
    } catch (e) {
      error = e instanceof Error ? e.message : 'Failed to load invitations';
    } finally {
      loading = false;
    }
  }

  onMount(() => {
    void load();
  });

  async function accept(id: string) {
    if (processingIds.includes(id)) return;
    processingIds = [...processingIds, id];
    try {
      await api.invitations.accept(id);
      pushToast('Invitation accepted', 'success');
      await load();
    } catch (e) {
      pushToast(e instanceof ApiError ? e.message : 'Failed', 'error');
    } finally {
      processingIds = processingIds.filter((invitationId) => invitationId !== id);
    }
  }

  async function acceptAndOpen(invitation: Invitation) {
    if (processingIds.includes(invitation.id)) return;
    processingIds = [...processingIds, invitation.id];
    try {
      await api.invitations.accept(invitation.id);
    } catch (e) {
      pushToast(e instanceof ApiError ? e.message : 'Could not accept the invitation.', 'error');
      processingIds = processingIds.filter((id) => id !== invitation.id);
      return;
    }

    pushToast('Invitation accepted', 'success');
    void refreshPendingInvitations(true);
    try {
      await goto(`/challenges/${invitation.challengeId}`);
    } catch {
      pushToast('Invitation accepted, but the challenge could not be opened. Find it in Challenges.', 'error');
    } finally {
      processingIds = processingIds.filter((id) => id !== invitation.id);
    }
  }

  async function reject(id: string) {
    if (processingIds.includes(id)) return;
    processingIds = [...processingIds, id];
    try {
      await api.invitations.reject(id);
      pushToast('Invitation rejected', 'info');
      await load();
    } catch (e) {
      pushToast(e instanceof ApiError ? e.message : 'Failed', 'error');
    } finally {
      processingIds = processingIds.filter((invitationId) => invitationId !== id);
    }
  }
</script>

<Topbar title="Invitations" subtitle="Manage invitations to your challenges." />
{#if loading}
  <LoadingState message="Loading your invitations…" />
{:else if error}
  <div class="error-banner" role="alert">{error} <button class="btn" onclick={load}>Retry</button></div>
{:else}
  <div class="grid list">
    {#each invitations as inv (inv.id)}
      {@const inviter = memberLabel(inv.inviterId, inv.inviterName)}
      <article class="card inv accent-top" style="--stripe: var(--grad-violet)" aria-labelledby={`invite-${inv.id}`}>
        <div class="invite-info">
          <span class="avatar" aria-hidden="true">{inviter.charAt(0).toUpperCase()}</span>
          <div>
            <span class="pill tone-violet">Challenge invite</span>
            <h2 id={`invite-${inv.id}`}>{inv.challengeTitle ?? 'Challenge invitation'}</h2>
            <p class="muted">Invited by <strong>{inviter}</strong></p>
            {#if !inv.challengeTitle}<p class="muted">This challenge may no longer be available.</p>{/if}
          </div>
        </div>
        <div class="ops">
          <button class="btn btn-primary" onclick={() => acceptAndOpen(inv)} disabled={processingIds.includes(inv.id)} aria-busy={processingIds.includes(inv.id)}>
            {processingIds.includes(inv.id) ? 'Working…' : 'Accept & view'}
          </button>
          <button class="btn" onclick={() => accept(inv.id)} disabled={processingIds.includes(inv.id)}>Accept</button>
          <button class="btn btn-ghost decline" onclick={() => reject(inv.id)} disabled={processingIds.includes(inv.id)}>Decline</button>
        </div>
      </article>
    {:else}
      <div class="empty-state">
        <div class="empty-state-copy"><strong>You're all caught up</strong><p>New challenge invitations will appear here.</p></div>
      </div>
    {/each}
  </div>
{/if}

<style>
  .list { grid-template-columns: repeat(auto-fill, minmax(min(100%, 380px), 1fr)); gap: 1.1rem; }
  .inv { display: grid; gap: 1.1rem; padding-top: 1.6rem; }
  .invite-info { display: flex; align-items: flex-start; gap: .9rem; }
  .invite-info h2 { margin: .45rem 0 .15rem; font-size: 1.15rem; }
  .invite-info p { margin: 0; font-size: .88rem; }
  .invite-info strong { color: var(--text); }
  .avatar { display: grid; width: 2.8rem; height: 2.8rem; flex: none; place-items: center; border-radius: 50%; background: var(--grad-violet); color: #fff; font-weight: 800; box-shadow: 0 6px 16px color-mix(in srgb, var(--violet) 30%, transparent); }
  .ops { display: flex; flex-wrap: wrap; gap: 0.5rem; padding-top: .9rem; border-top: 1px solid var(--border); }
  .decline { margin-left: auto; }
  .decline:hover:not(:disabled) { color: var(--danger); background: color-mix(in srgb, var(--danger) 10%, transparent); }
  @media (max-width: 560px) { .ops .btn { flex: 1; justify-content: center; } .decline { margin-left: 0; } }
</style>
