<script lang="ts">
  import Topbar from '$lib/components/Topbar.svelte';
  import LoadingState from '$lib/components/LoadingState.svelte';
  import { goto } from '$app/navigation';
  import { onMount } from 'svelte';
  import { api, ApiError } from '$lib/api';
  import { pushToast } from '$lib/stores/toast.svelte';
  import type { Invitation, Page } from '$lib/types';

  let invitations = $state<Invitation[]>([]);
  let error = $state('');
  let loading = $state(true);
  let processingIds = $state<string[]>([]);

  async function load() {
    loading = true;
    error = '';
    try {
      const inv = (await api.invitations.pending()) as Page<Invitation>;
      invitations = inv.content ?? [];
    } catch (e) {
      error = e instanceof ApiError ? e.message : 'Failed to load invitations';
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
      <div class="card inv">
        <div>
          <strong>Challenge invite</strong>
          <div class="muted">From user {inv.inviterId.slice(0, 8)}…</div>
          <div class="muted">Challenge ID {inv.challengeId.slice(0, 8)}…</div>
        </div>
        <div class="ops">
          <button class="btn btn-primary" onclick={() => accept(inv.id)} disabled={processingIds.includes(inv.id)}>Accept</button>
          <button class="btn" onclick={() => acceptAndOpen(inv)} disabled={processingIds.includes(inv.id)} aria-busy={processingIds.includes(inv.id)}>
            {processingIds.includes(inv.id) ? 'Working…' : 'Accept & view'}
          </button>
          <button class="btn" onclick={() => reject(inv.id)} disabled={processingIds.includes(inv.id)}>Reject</button>
        </div>
      </div>
    {:else}
      <div class="empty-state">
        <div class="empty-state-copy"><strong>You're all caught up</strong><p>New challenge invitations will appear here.</p></div>
      </div>
    {/each}
  </div>
{/if}

<style>
  .list { grid-template-columns: 1fr; }
  .inv { display: flex; justify-content: space-between; align-items: center; gap: 1rem; flex-wrap: wrap; }
  .ops { display: flex; gap: 0.5rem; }
</style>
