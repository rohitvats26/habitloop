<script lang="ts">
  import Topbar from '$lib/components/Topbar.svelte';
  let { data } = $props();
  const fmt = (iso: string) => new Date(iso).toLocaleString();
</script>

<Topbar title="Activity history" />

<div class="card">
  {#if data.activity.content.length}
    <table>
      <thead><tr><th>When</th><th>Type</th><th>Value</th><th>Source</th></tr></thead>
      <tbody>
        {#each data.activity.content as a (a.id)}
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
