<script lang="ts">
  import Topbar from '$lib/components/Topbar.svelte';
  import LoadingState from '$lib/components/LoadingState.svelte';
  import { onMount } from 'svelte';
  import { api, ApiError } from '$lib/api';
  import { downloadCsv, toCsv } from '$lib/csv';
  import { activityWhen } from '$lib/date';
  import { fetchAllPages } from '$lib/pagination';
  import { pushToast } from '$lib/stores/toast.svelte';
  import { INDIA_TIMEZONE_OFFSET } from '$lib/timezone';
  import type { ActivityLog, ActivityQuery } from '$lib/types';

  let activity = $state<ActivityLog[]>([]);
  let habitNames = $state<Record<string, string> | null>(null);
  let error = $state('');
  let filterError = $state('');
  let loading = $state(true);
  let exporting = $state(false);
  let filterType = $state('');
  let fromDate = $state('');
  let toDate = $state('');
  let appliedQuery = $state<ActivityQuery>({});
  let currentPage = $state(0);
  let totalPages = $state(0);
  let totalElements = $state(0);
  const pageSize = 50;

  function habitLabel(item: ActivityLog): string {
    if (!item.habitId) return '—';
    if (habitNames === null) return 'Habit';
    return habitNames[item.habitId] ?? 'Deleted habit';
  }

  async function loadHabitNames() {
    try {
      const habits = await fetchAllPages(api.habits.list);
      habitNames = Object.fromEntries(habits.map((habit) => [habit.id, habit.name]));
    } catch {
      habitNames = null;
    }
  }

  async function load(page = currentPage, query = appliedQuery) {
    loading = true;
    error = '';
    try {
      const a = await api.activity.log({ ...query, page, size: pageSize });
      activity = a.content;
      appliedQuery = query;
      currentPage = a.page;
      totalPages = a.totalPages;
      totalElements = a.totalElements;
    } catch (e) {
      error = e instanceof ApiError ? e.message : 'Failed to load activity';
    } finally {
      loading = false;
    }
  }

  onMount(() => {
    void load();
    void loadHabitNames();
  });

  function applyFilters(event: SubmitEvent) {
    event.preventDefault();
    filterError = '';
    if (fromDate && toDate && fromDate > toDate) {
      filterError = 'Start date must be on or before end date.';
      return;
    }
    void load(0, {
      type: filterType.trim().toUpperCase() || undefined,
      from: fromDate ? `${fromDate}T00:00:00${INDIA_TIMEZONE_OFFSET}` : undefined,
      to: toDate ? `${toDate}T23:59:59.999999999${INDIA_TIMEZONE_OFFSET}` : undefined
    });
  }

  async function exportAll() {
    if (exporting) return;
    exporting = true;
    try {
      const rows = await fetchAllPages((page) => api.activity.log({ ...appliedQuery, page, size: 100, sort: 'id,asc' }));
      downloadCsv('habitloop-activity.csv', toCsv(
        ['When (IST)', 'Activity type', 'Habit', 'Value', 'Unit', 'Source'],
        rows.map((item) => [activityWhen(item), item.activityType, habitLabel(item), item.value, item.unit, item.source])
      ));
    } catch (e) {
      pushToast(e instanceof Error ? e.message : 'Could not export activity.', 'error');
    } finally {
      exporting = false;
    }
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
    <button class="btn" type="button" onclick={() => { filterType = ''; fromDate = ''; toDate = ''; void load(0, {}); }} disabled={loading}>
      Clear
    </button>
    <button class="btn" type="button" onclick={exportAll} disabled={loading || exporting || !totalElements} aria-busy={exporting}>
      {exporting ? 'Exporting…' : `Export all (${totalElements})`}
    </button>
  </div>
</form>
{#if filterError}<div class="error-banner" role="alert">{filterError}</div>{/if}
<p class="muted result-count">{totalElements} matching activities · dates shown in IST · habit completions show the day they count for</p>

{#if loading}
  <LoadingState message="Loading your activity…" />
{:else if error}
  <div class="error-banner" role="alert">{error} <button class="btn" onclick={() => load()}>Retry</button></div>
{:else}
  <div class="card">
    {#if activity.length}
      <table>
        <thead><tr><th>When</th><th>Type</th><th>Habit</th><th>Value</th><th>Source</th></tr></thead>
        <tbody>
          {#each activity as a (a.id)}
            <tr>
              <td>{activityWhen(a)}</td>
              <td>{a.activityType}</td>
              <td>{habitLabel(a)}</td>
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
  .filters { display: flex; align-items: end; flex-wrap: wrap; gap: 1rem; margin-bottom: .75rem; border-top: 3px solid color-mix(in srgb,var(--primary) 45%,transparent); }
  .filters .field { min-width: 160px; flex: 1; }
  .filter-actions { display: flex; flex-wrap: wrap; gap: .5rem; }
  .result-count { margin: 0 0 .75rem; font-size: .85rem; }
  .pagination { display: flex; justify-content: center; align-items: center; gap: 1rem; margin-top: 1rem; padding: .75rem; border: 1px solid var(--border); border-radius: 14px; background: color-mix(in srgb,var(--surface-2) 40%,var(--surface)); }
  .pagination span { font-size: .82rem; font-variant-numeric: tabular-nums; }
  table { min-width: 620px; }
  @media (max-width: 560px) { .filter-actions { width: 100%; } .filter-actions .btn { flex: 1 1 40%; justify-content: center; } }
</style>
