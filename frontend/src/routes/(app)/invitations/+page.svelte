<script lang="ts">
  import Topbar from '$lib/components/Topbar.svelte';
  import { enhance } from '$app/forms';
  let { data, form } = $props();
</script>

<Topbar title="Invitations" />
{#if form?.error}<div class="error-banner">{form.error}</div>{/if}

<div class="grid list">
  {#each data.invitations.content as inv (inv.id)}
    <div class="card inv">
      <div>
        <strong>Challenge invite</strong>
        <div class="muted">Challenge {inv.challengeId.slice(0, 8)}… · from {inv.inviterId.slice(0, 8)}…</div>
      </div>
      <div class="ops">
        <form method="POST" action="?/accept" use:enhance>
          <input type="hidden" name="id" value={inv.id} />
          <button class="btn btn-primary" type="submit">Accept</button>
        </form>
        <form method="POST" action="?/reject" use:enhance>
          <input type="hidden" name="id" value={inv.id} />
          <button class="btn" type="submit">Reject</button>
        </form>
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
