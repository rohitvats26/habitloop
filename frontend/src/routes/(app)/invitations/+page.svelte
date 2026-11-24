<script lang="ts">
  import Topbar from '$lib/components/Topbar.svelte';
  import { onMount } from 'svelte';
  import { api, ApiError } from '$lib/api';
  import { pushToast } from '$lib/stores/toast.svelte';
  import type { Invitation, Page } from '$lib/types';

  let invitations = $state<Invitation[]>([]);
  let error = $state('');

  async function load() {
    try {
      const inv = (await api.invitations.pending()) as Page<Invitation>;
      invitations = inv.content ?? [];
    } catch (e) {
      error = e instanceof ApiError ? e.message : 'Failed to load invitations';
    }
  }

  onMount(load);

  async function accept(id: string) {
    try {
      await api.invitations.accept(id);
      pushToast('Invitation accepted', 'success');
      await load();
    } catch (e) {
      pushToast(e instanceof ApiError ? e.message : 'Failed', 'error');
    }
  }

  async function reject(id: string) {
    try {
      await api.invitations.reject(id);
      pushToast('Invitation rejected', 'info');
      await load();
    } catch (e) {
      pushToast(e instanceof ApiError ? e.message : 'Failed', 'error');
    }
  }
</script>

<Topbar title="Invitations" />
{#if error}<div class="error-banner">{error}</div>{/if}

<div class="grid list">
  {#each invitations as inv (inv.id)}
    <div class="card inv">
      <div>
        <strong>Challenge invite</strong>
        <div class="muted">Challenge {inv.challengeId.slice(0, 8)}… · from {inv.inviterId.slice(0, 8)}…</div>
      </div>
      <div class="ops">
        <button class="btn btn-primary" onclick={() => accept(inv.id)}>Accept</button>
        <button class="btn" onclick={() => reject(inv.id)}>Reject</button>
      </div>
    </div>
  {:else}
    <p class="muted">No pending invitations.</p>
  {/each}
</div>

<style>
  .list { grid-template-columns: 1fr; }
  .inv { display: flex; justify-content: space-between; align-items: center; gap: 1rem; flex-wrap: wrap; }
  .ops { display: flex; gap: 0.5rem; }
</style>
