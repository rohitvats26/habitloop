<script lang="ts">
  import Topbar from '$lib/components/Topbar.svelte';
  import LoadingState from '$lib/components/LoadingState.svelte';
  import { onMount } from 'svelte';
  import { api, ApiError } from '$lib/api';
  import { auth } from '$lib/auth.svelte';
  import { userDateString } from '$lib/date';
  import { downloadCsv, toCsv } from '$lib/csv';
  import type { AnalyticsSummary, CohortSummary, PatternSummary, WeeklyStat } from '$lib/types';

  let summary = $state<AnalyticsSummary | null>(null);
  let daily = $state<{ date: string; completions: number }[]>([]);
  let weekly = $state<WeeklyStat[]>([]);
  let pattern = $state<PatternSummary | null>(null);
  let loading = $state(true);
  let error = $state('');
  let fromDate = $state('');
  let toDate = $state('');
  let weeklyRange = $state(8);
  let cohort = $state<CohortSummary | null>(null);
  let cohortDate = $state('');
  let cohortLoading = $state(false);
  let cohortError = $state('');
  const isAdmin = $derived(auth.user?.roles.includes('TENANT_ADMIN') ?? false);

  async function load() {
    if (fromDate && toDate && fromDate > toDate) {
      error = 'Start date must be on or before end date.';
      return;
    }
    loading = true;
    error = '';
    const results = await Promise.allSettled([
      api.analytics.summary(),
      api.analytics.daily(fromDate || undefined, toDate || undefined),
      api.analytics.weekly(weeklyRange),
      api.analytics.pattern()
    ]);
    const failures: string[] = [];
    if (results[0].status === 'fulfilled') {
      summary = results[0].value as AnalyticsSummary;
    } else {
      failures.push(`Summary: ${message(results[0].reason)}`);
    }
    if (results[1].status === 'fulfilled') {
      daily = results[1].value as { date: string; completions: number }[];
    } else {
      failures.push(`Daily activity: ${message(results[1].reason)}`);
    }
    if (results[2].status === 'fulfilled') {
      weekly = results[2].value;
    } else {
      failures.push(`Weekly trend: ${message(results[2].reason)}`);
    }
    if (results[3].status === 'fulfilled') {
      pattern = results[3].value;
    } else {
      failures.push(`Activity patterns: ${message(results[3].reason)}`);
    }
    error = failures.join(' ');
    loading = false;
  }

  function message(reason: unknown): string {
    return reason instanceof Error ? reason.message : 'Request failed.';
  }

  async function loadCohort() {
    cohortLoading = true;
    cohortError = '';
    try {
      cohort = await api.analytics.cohort(cohortDate);
    } catch (reason) {
      cohortError = message(reason);
    } finally {
      cohortLoading = false;
    }
  }

  onMount(() => {
    void load();
    if (isAdmin) {
      void userDateString()
        .then((date) => {
          cohortDate = date;
          return loadCohort();
        })
        .catch((reason: unknown) => {
          cohortError = message(reason);
        });
    }
  });

  const dailyPoints = $derived(
    JSON.stringify((daily ?? []).map((d) => ({ label: d.date.slice(5), value: d.completions })))
  );
  const weeklyPoints = $derived(JSON.stringify(weekly.map((w) => ({ label: w.week, value: w.completions }))));
  const hourlyPattern = $derived(
    Object.entries(pattern?.byHour ?? {}).sort(([first], [second]) => Number(first) - Number(second))
  );
  const weekdayOrder = ['MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN'];
  const weekdayLabels: Record<string, string> = {
    MON: 'Monday', TUE: 'Tuesday', WED: 'Wednesday', THU: 'Thursday',
    FRI: 'Friday', SAT: 'Saturday', SUN: 'Sunday'
  };
  const weekdayPattern = $derived(
    Object.entries(pattern?.byWeekday ?? {}).sort(
      ([first], [second]) => weekdayOrder.indexOf(first) - weekdayOrder.indexOf(second)
    )
  );

  function exportDaily() {
    downloadCsv('habitloop-daily-analytics.csv', toCsv(
      ['Date (IST)', 'Completions'],
      daily.map((item) => [item.date, item.completions])
    ));
  }

  function exportWeekly() {
    downloadCsv('habitloop-weekly-analytics.csv', toCsv(
      ['Week', 'Completions'],
      weekly.map((item) => [item.week, item.completions])
    ));
  }
</script>

<Topbar title="Analytics" subtitle="See how your routines are trending over time." />

<form class="card filters" onsubmit={(event) => { event.preventDefault(); void load(); }}>
  <div class="field">
    <label class="label" for="analyticsFrom">From</label>
    <input class="input" id="analyticsFrom" type="date" bind:value={fromDate} />
  </div>
  <div class="field">
    <label class="label" for="analyticsTo">To</label>
    <input class="input" id="analyticsTo" type="date" bind:value={toDate} />
  </div>
  <div class="field">
    <label class="label" for="weeklyRange">Weekly trend</label>
    <select class="input" id="weeklyRange" bind:value={weeklyRange}>
      <option value={4}>4 weeks</option><option value={8}>8 weeks</option><option value={12}>12 weeks</option>
      <option value={26}>26 weeks</option><option value={52}>52 weeks</option>
    </select>
  </div>
  <button class="btn btn-primary" type="submit" disabled={loading}>Update analytics</button>
  <button class="btn" type="button" onclick={() => { fromDate = ''; toDate = ''; weeklyRange = 8; void load(); }} disabled={loading}>
    Reset
  </button>
</form>

{#if error}
  <div class="error-banner" role="alert">{error} <button class="btn" onclick={load}>Retry</button></div>
{/if}

{#if loading}
  <LoadingState message="Loading your analytics…" />
{:else}
<div class="grid tiles">
  <div class="card tile"><div class="k">Completed today</div><div class="v">{summary?.completedToday ?? 0}</div></div>
  <div class="card tile"><div class="k">This week</div><div class="v">{summary?.weekCompletions ?? 0}</div></div>
</div>
{/if}

{#if isAdmin}
  <section class="card cohort">
    <h2>Tenant cohort insights</h2>
    <form class="row" onsubmit={(event) => { event.preventDefault(); void loadCohort(); }}>
      <div class="field">
        <label class="label" for="cohortDate">Date</label>
        <input class="input" id="cohortDate" type="date" bind:value={cohortDate} required />
      </div>
      <button class="btn btn-primary" type="submit" disabled={cohortLoading} aria-busy={cohortLoading}>
        {#if cohortLoading}
          <span class="loading-spinner loading-spinner-small" aria-hidden="true"></span>
          Loading…
        {:else}
          View cohort
        {/if}
      </button>
    </form>
    {#if cohortError}
      <div class="error-banner" role="alert">{cohortError}</div>
    {:else if cohortLoading}
      <LoadingState message="Loading cohort insights…" />
    {:else if cohort}
      <div class="row">
        <div><span class="muted">Date</span><strong>{cohort.date}</strong></div>
        <div><span class="muted">Active users</span><strong>{cohort.activeUsers}</strong></div>
        <div><span class="muted">Completions</span><strong>{cohort.totalCompletions}</strong></div>
      </div>
    {/if}
  </section>
{/if}

<div class="card">
  <div class="section-heading">
    <h2>Daily completions</h2>
    <button class="btn" type="button" onclick={exportDaily} disabled={!daily.length}>Export CSV</button>
  </div>
  {#if (daily ?? []).length}
    <hl-chart points={dailyPoints}></hl-chart>
    <table>
      <thead><tr><th>Date</th><th>Completions</th></tr></thead>
      <tbody>{#each daily as d}<tr><td>{d.date}</td><td>{d.completions}</td></tr>{/each}</tbody>
    </table>
  {:else}
    <div class="empty-state compact">
      <div class="empty-state-copy"><strong>Your analytics will grow with you</strong><p>Complete a habit or sync a wearable activity to begin.</p></div>
    </div>
  {/if}
</div>

<div class="card">
  <div class="section-heading">
    <h2>Weekly trend</h2>
    <button class="btn" type="button" onclick={exportWeekly} disabled={!weekly.length}>Export CSV</button>
  </div>
  {#if weekly.length}
    <hl-chart points={weeklyPoints}></hl-chart>
    <table>
      <thead><tr><th>Week</th><th>Completions</th></tr></thead>
      <tbody>{#each weekly as w}<tr><td>{w.week}</td><td>{w.completions}</td></tr>{/each}</tbody>
    </table>
  {:else}
    <div class="empty-state compact">
      <div class="empty-state-copy"><strong>No weekly trend yet</strong><p>Weekly progress will appear after your first habit completion.</p></div>
    </div>
  {/if}
</div>

<div class="grid patterns">
  <section class="card">
    <h2>Activity by weekday</h2>
    {#if weekdayPattern.length}
      <table>
        <thead><tr><th>Weekday</th><th>Activities</th></tr></thead>
        <tbody>{#each weekdayPattern as [day, count]}<tr><td>{weekdayLabels[day] ?? day}</td><td>{count}</td></tr>{/each}</tbody>
      </table>
    {:else}
      <p class="muted">Weekday patterns will appear after you log activity.</p>
    {/if}
  </section>
  <section class="card">
    <h2>Activity by hour (IST)</h2>
    {#if hourlyPattern.length}
      <table>
        <thead><tr><th>Time</th><th>Activities</th></tr></thead>
        <tbody>{#each hourlyPattern as [hour, count]}<tr><td>{String(hour).padStart(2, '0')}:00</td><td>{count}</td></tr>{/each}</tbody>
      </table>
    {:else}
      <p class="muted">Time-of-day patterns will appear after you log activity.</p>
    {/if}
  </section>
</div>

<style>
  .tiles { grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); margin-bottom: 1rem; }
  .tile .k { color: var(--text-muted); font-size: 0.85rem; }
  .tile .v { font-size: 2rem; font-weight: 700; }
  .card { overflow-x: auto; margin-bottom: 1rem; }
  .filters { display: flex; align-items: end; flex-wrap: wrap; gap: .75rem; }
  .filters .field { min-width: 150px; flex: 1; margin: 0; }
  .section-heading { display: flex; align-items: center; justify-content: space-between; gap: .75rem; }
  .patterns { grid-template-columns: repeat(auto-fit, minmax(250px, 1fr)); }
  .patterns .card { margin-bottom: 0; }
  .cohort { margin-top: 1rem; }
  .cohort form { align-items: end; }
  .cohort .field { min-width: 180px; }
  .cohort .row > div { display: grid; gap: 0.25rem; }
</style>
