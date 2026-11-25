<script lang="ts">
  import Topbar from '$lib/components/Topbar.svelte';
  import LoadingState from '$lib/components/LoadingState.svelte';
  import { onMount } from 'svelte';
  import { api, ApiError } from '$lib/api';
  import { downloadCsv, toCsv } from '$lib/csv';
  import { INDIA_TIMEZONE_OFFSET } from '$lib/timezone';
  import type { ActivityLog, Page } from '$lib/types';

  let activity = $state<ActivityLog[]>([]);
  let error = $state('');
  let loading = $state(true);
  let filterType = $state('');
  let fromDate = $state('');
  let toDate = $state('');
  let currentPage = $state(0);
  let totalPages = $state(0);
  let totalElements = $state(0);
  const pageSize = 50;
  const fmt = (iso: string) => new Date(iso).toLocaleString('en-IN', { timeZone: 'Asia/Kolkata' });

  async function load(page = currentPage) {
    if (fromDate && toDate && fromDate > toDate) {
      error = 'Start date must be on or before end date.';
      return;
    }
    loading = true;
    error = '';
    try {
      const a = (await api.activity.log({
        type: filterType.trim() || undefined,
        from: fromDate ? `${fromDate}T00:00:00${INDIA_TIMEZONE_OFFSET}` : undefined,
        to: toDate ? `${toDate}T23:59:59.999999999${INDIA_TIMEZONE_OFFSET}` : undefined,
        page,
        size: pageSize
      })) as Page<ActivityLog>;
      activity = a.content ?? [];
      currentPage = a.page ?? page;
      totalPages = a.totalPages ?? 0;
      totalElements = a.totalElements ?? 0;
    } catch (e) {
      error = e instanceof ApiError ? e.message : 'Failed to load activity';
    } finally {
      loading = false;
    }
  }

  onMount(() => {
    void load();
  });

  function applyFilters(event: SubmitEvent) {
    event.preventDefault();
    void load(0);
  }

  function exportPage() {
    const csv = toCsv(
      ['When (IST)', 'Activity type', 'Value', 'Unit', 'Source'],
      activity.map((item) => [fmt(item.occurredAt), item.activityType, item.value, item.unit, item.source])
    );
    downloadCsv('habitloop-activity.csv', csv);
  }
</script>

<Topbar title="Activity history" subtitle="Review the habits and activity you've logged." />

<form class="card filters" onsubmit={applyFilters}>
  <div class="field">
    <label class="label" for="activityType">Activity type</label>
    <input class="input" id="activityType" bind:value={filterType} maxlength="40" placeholder="e.g. WALKING" />
  </div>
  <div class="field">
    <label class="label" for="fromDate">From</label>
    <input class="input" id="fromDate" type="date" bind:value={fromDate} />
  </div>
  <div class="field">
    <label class="label" for="toDate">To</label>
    <input class="input" id="toDate" type="date" bind:value={toDate} />
  </div>
  <div class="filter-actions">
    <button class="btn btn-primary" type="submit" disabled={loading}>Apply filters</button>
    <button class="btn" type="button" onclick={() => { filterType = ''; fromDate = ''; toDate = ''; void load(0); }} disabled={loading}>
      Clear
    </button>
    <button class="btn" type="button" onclick={exportPage} disabled={!activity.length}>Export this page</button>
  </div>
</form>
<p class="muted result-count">{totalElements} matching activities · dates shown in IST</p>

{#if loading}
  <LoadingState message="Loading your activity…" />
{:else if error}
  <div class="error-banner" role="alert">{error} <button class="btn" onclick={() => load()}>Retry</button></div>
{:else}
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
      <div class="empty-state">
        <div class="empty-state-copy"><strong>No activity recorded yet</strong><p>Complete a habit and it will show up here.</p></div>
      </div>
    {/if}
  </div>
  {#if totalPages > 1}
    <nav class="pagination" aria-label="Activity pages">
      <button class="btn" onclick={() => load(currentPage - 1)} disabled={currentPage === 0 || loading}>Previous</button>
      <span class="muted">Page {currentPage + 1} of {totalPages}</span>
      <button class="btn" onclick={() => load(currentPage + 1)} disabled={currentPage + 1 >= totalPages || loading}>Next</button>
    </nav>
  {/if}
{/if}

<style>
  .card { overflow-x: auto; }
  .filters { display: flex; align-items: end; flex-wrap: wrap; gap: 1rem; margin-bottom: .75rem; }
  .filters .field { min-width: 160px; flex: 1; }
  .filter-actions { display: flex; flex-wrap: wrap; gap: .5rem; }
  .result-count { margin: 0 0 .75rem; font-size: .85rem; }
  .pagination { display: flex; justify-content: center; align-items: center; gap: 1rem; margin-top: 1rem; }
</style>
