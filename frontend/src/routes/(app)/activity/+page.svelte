<script lang="ts">
  import Topbar from '$lib/components/Topbar.svelte';
  import { onMount } from 'svelte';
  import { api, ApiError } from '$lib/api';
  import type { ActivityLog, Page } from '$lib/types';

  let activity = $state<ActivityLog[]>([]);
  let error = $state('');
  const fmt = (iso: string) => new Date(iso).toLocaleString();

  onMount(async () => {
    try {
      const a = (await api.activity.log()) as Page<ActivityLog>;
      activity = a.content ?? [];
    } catch (e) {
      error = e instanceof ApiError ? e.message : 'Failed to load activity';
    }
  });
</script>

<Topbar title="Activity history" />

{#if error}<div class="error-banner">{error}</div>{/if}

<div class="card">
  {#if activity.length}
    <table>
      <thead><tr><th>When</th><th>Type</th><th>Value</th><th>Source</th></tr></thead>
      <tbody>
        {#each activity as a (a.id)}
          <tr>
            <td>{fmt(a.occurredAt)}</td>
            <td>{a.activityType}</td>
            <td>{a.value ?? '—'} {a.unit ?? ''}</td>
            <td><span class="badge">{a.source}</span></td>
          </tr>
        {/each}
      </tbody>
    </table>
  {:else}
    <p class="muted">No activity logged yet.</p>
  {/if}
</div>

<style>
  table { width: 100%; border-collapse: collapse; }
  th, td { text-align: left; padding: 0.55rem 0.4rem; border-bottom: 1px solid var(--border); }
  th { color: var(--text-muted); font-weight: 600; font-size: 0.85rem; }
</style>
