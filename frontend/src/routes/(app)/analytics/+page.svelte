<script lang="ts">
  import Topbar from '$lib/components/Topbar.svelte';
  import LoadingState from '$lib/components/LoadingState.svelte';
  import { onMount } from 'svelte';
  import { api } from '$lib/api';
  import { auth } from '$lib/auth.svelte';
  import { userDateString } from '$lib/date';
  import { downloadCsv, toCsv } from '$lib/csv';
  import type { AnalyticsSummary, CohortSummary, PatternSummary, WeeklyStat } from '$lib/types';

  let summary = $state<AnalyticsSummary | null>(null);
  let daily = $state<{ date: string; completions: number }[] | null>(null);
  let weekly = $state<WeeklyStat[] | null>(null);
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
      error = 'Start date must be on or before end date. Results still reflect the previous selection.';
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
      summary = results[0].value;
    } else {
      summary = null;
      failures.push(`Summary: ${message(results[0].reason)}`);
    }
    if (results[1].status === 'fulfilled') {
      daily = results[1].value;
    } else {
      daily = null;
      failures.push(`Daily activity: ${message(results[1].reason)}`);
    }
    if (results[2].status === 'fulfilled') {
      weekly = results[2].value;
    } else {
      weekly = null;
      failures.push(`Weekly trend: ${message(results[2].reason)}`);
    }
    if (results[3].status === 'fulfilled') {
      pattern = results[3].value;
    } else {
      pattern = null;
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
      cohort = null;
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
  const weeklyPoints = $derived(JSON.stringify((weekly ?? []).map((w) => ({ label: w.week.replace(/^\d{4}-/, ''), value: w.completions }))));
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

  const rangeTotal = $derived((daily ?? []).reduce((sum, d) => sum + d.completions, 0));
  const dailyAverage = $derived(daily?.length ? (rangeTotal / daily.length).toFixed(1) : '0');
  const bestDay = $derived((daily ?? []).reduce<{ date: string; completions: number } | null>((best, d) => (!best || d.completions > best.completions ? d : best), null));
  const weekdayMax = $derived(Math.max(1, ...weekdayPattern.map(([, count]) => count)));
  const hourMax = $derived(Math.max(1, ...hourlyPattern.map(([, count]) => count)));

  function shortDay(date: string): string {
    return new Date(`${date}T00:00:00Z`).toLocaleDateString('en-IN', { timeZone: 'UTC', weekday: 'short', day: 'numeric', month: 'short' });
  }

  function exportDaily() {
    if (loading || daily === null) return;
    downloadCsv('habitloop-daily-analytics.csv', toCsv(
      ['Date (IST)', 'Completions'],
      daily.map((item) => [item.date, item.completions])
    ));
  }

  function exportWeekly() {
    if (loading || weekly === null) return;
    downloadCsv('habitloop-weekly-analytics.csv', toCsv(
      ['Week', 'Completions'],
      weekly.map((item) => [item.week, item.completions])
    ));
  }
</script>

<Topbar title="Analytics" subtitle="See how your routines are trending over time." />

<form class="card filters" aria-label="Analytics range" onsubmit={(event) => { event.preventDefault(); void load(); }}>
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
  <hl-stat-card label="Completed today" icon="check" tone="primary" hint="All habits, IST" value={summary?.completedToday ?? 'Unavailable'}></hl-stat-card>
  <hl-stat-card label="This week" icon="calendar" tone="violet" hint="Since Monday (IST)" value={summary?.weekCompletions ?? 'Unavailable'}></hl-stat-card>
  <hl-stat-card label="In this range" icon="target" tone="success" hint={daily?.length ? `${daily.length} days · ${dailyAverage} per day` : undefined} value={daily === null ? 'Unavailable' : rangeTotal}></hl-stat-card>
  <hl-stat-card label="Best day" icon="flame" tone="accent" hint={bestDay ? shortDay(bestDay.date) : undefined} value={daily === null ? 'Unavailable' : bestDay?.completions ?? 0}></hl-stat-card>
</div>
{/if}

{#if isAdmin}
  <section class="card cohort accent-top" style="--stripe: var(--grad-violet)" aria-labelledby="cohort-heading">
    <div class="section-head">
      <div>
        <h2 id="cohort-heading">Workspace cohort</h2>
        <p class="muted">Everyone in your workspace on a single IST day. Admins only.</p>
      </div>
      <form class="cohort-form" onsubmit={(event) => { event.preventDefault(); void loadCohort(); }}>
        <label class="sr-only" for="cohortDate">Date</label>
        <input class="input" id="cohortDate" type="date" bind:value={cohortDate} required />
        <button class="btn btn-primary" type="submit" disabled={cohortLoading} aria-busy={cohortLoading}>
          {cohortLoading ? 'Loading…' : 'View'}
        </button>
      </form>
    </div>
    {#if cohortError}
      <div class="error-banner" role="alert">{cohortError}</div>
    {:else if cohort}
      <dl class="cohort-stats">
        <div><dt>Active members</dt><dd>{cohort.activeUsers}</dd></div>
        <div><dt>Completions</dt><dd>{cohort.totalCompletions}</dd></div>
        <div><dt>Per active member</dt><dd>{cohort.activeUsers ? (cohort.totalCompletions / cohort.activeUsers).toFixed(1) : '0'}</dd></div>
      </dl>
    {/if}
  </section>
{/if}

<div class="charts">
  <section class="card" aria-labelledby="daily-heading">
    <div class="section-head">
      <h2 id="daily-heading">Daily completions</h2>
      <button class="btn btn-ghost" type="button" onclick={exportDaily} disabled={loading || !daily?.length}>Export CSV</button>
    </div>
    {#if loading}
      <LoadingState message="Loading daily completions…" />
    {:else if daily === null}
      <p class="muted">Daily completions are unavailable.</p>
    {:else if daily.length}
      <hl-chart points={dailyPoints}></hl-chart>
      <details>
        <summary>Show data table</summary>
        <table>
          <thead><tr><th>Date</th><th>Completions</th></tr></thead>
          <tbody>{#each daily as d}<tr><td>{d.date}</td><td>{d.completions}</td></tr>{/each}</tbody>
        </table>
      </details>
    {:else}
      <div class="empty-state compact">
        <div class="empty-state-copy"><strong>Your analytics will grow with you</strong><p>Complete a habit or sync a wearable activity to begin.</p></div>
      </div>
    {/if}
  </section>

  <section class="card" aria-labelledby="weekly-heading">
    <div class="section-head">
      <h2 id="weekly-heading">Weekly trend</h2>
      <button class="btn btn-ghost" type="button" onclick={exportWeekly} disabled={loading || !weekly?.length}>Export CSV</button>
    </div>
    {#if loading}
      <LoadingState message="Loading weekly trend…" />
    {:else if weekly === null}
      <p class="muted">Weekly trend is unavailable.</p>
    {:else if weekly.length}
      <hl-chart points={weeklyPoints}></hl-chart>
      <details>
        <summary>Show data table</summary>
        <table>
          <thead><tr><th>Week</th><th>Completions</th></tr></thead>
          <tbody>{#each weekly as w}<tr><td>{w.week}</td><td>{w.completions}</td></tr>{/each}</tbody>
        </table>
      </details>
    {:else}
      <div class="empty-state compact">
        <div class="empty-state-copy"><strong>No weekly trend yet</strong><p>Weekly progress will appear after your first habit completion.</p></div>
      </div>
    {/if}
  </section>
</div>

<div class="grid patterns">
  <section class="card" aria-labelledby="weekday-heading">
    <h2 id="weekday-heading">When in the week</h2>
    {#if loading}
      <LoadingState message="Loading activity patterns…" />
    {:else if pattern === null}
      <p class="muted">Activity patterns are unavailable.</p>
    {:else if weekdayPattern.length}
      <ul class="bars">
        {#each weekdayPattern as [day, count]}
          <li class:peak={count === weekdayMax}>
            <span class="bar-label">{weekdayLabels[day] ?? day}</span>
            <span class="bar-track" aria-hidden="true"><span style={`width: ${(count / weekdayMax) * 100}%`}></span></span>
            <strong>{count}</strong>
          </li>
        {/each}
      </ul>
    {:else}
      <p class="muted">Weekday patterns will appear after you log activity.</p>
    {/if}
  </section>
  <section class="card" aria-labelledby="hour-heading">
    <h2 id="hour-heading">Time of day (IST)</h2>
    {#if loading}
      <LoadingState message="Loading activity patterns…" />
    {:else if pattern === null}
      <p class="muted">Activity patterns are unavailable.</p>
    {:else if hourlyPattern.length}
      <ul class="bars warm">
        {#each hourlyPattern as [hour, count]}
          <li class:peak={count === hourMax}>
            <span class="bar-label">{String(hour).padStart(2, '0')}:00</span>
            <span class="bar-track" aria-hidden="true"><span style={`width: ${(count / hourMax) * 100}%`}></span></span>
            <strong>{count}</strong>
          </li>
        {/each}
      </ul>
    {:else}
      <p class="muted">Time-of-day patterns will appear after you log activity.</p>
    {/if}
  </section>
</div>

<style>
  .filters { display: flex; align-items: end; flex-wrap: wrap; gap: .75rem; margin-bottom: 1.25rem; }
  .filters .field { min-width: 150px; flex: 1; margin: 0; }
  .tiles { grid-template-columns: repeat(auto-fit, minmax(190px, 1fr)); margin-bottom: 1.25rem; }
  .card { overflow-x: auto; }
  .cohort { margin-bottom: 1.25rem; padding-top: 1.6rem; }
  .cohort .section-head { align-items: flex-start; flex-wrap: wrap; }
  .cohort .section-head p { margin: .15rem 0 0; font-size: .88rem; }
  .cohort-form { display: flex; gap: .5rem; }
  .cohort-form .input { width: auto; }
  .cohort-stats { display: grid; grid-template-columns: repeat(auto-fit, minmax(160px, 1fr)); gap: .75rem; margin: 0; }
  .cohort-stats div { padding: .9rem 1rem; border-radius: 14px; background: color-mix(in srgb, var(--violet) 8%, var(--surface-2)); }
  .cohort-stats dt { color: var(--text-muted); font-size: .78rem; font-weight: 650; }
  .cohort-stats dd { margin: .2rem 0 0; font-size: 1.6rem; font-weight: 800; letter-spacing: -.03em; font-variant-numeric: tabular-nums; }
  .charts { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(100%, 420px), 1fr)); gap: 1.25rem; margin-bottom: 1.25rem; }
  .charts > .card { min-width: 0; }
  details { margin-top: .9rem; }
  summary { width: fit-content; color: var(--primary); font-size: .85rem; font-weight: 650; cursor: pointer; }
  details table { margin-top: .6rem; }
  .patterns { grid-template-columns: repeat(auto-fit, minmax(min(100%, 320px), 1fr)); gap: 1.25rem; }
  .bars { display: grid; gap: .55rem; margin: 0; padding: 0; list-style: none; }
  .bars li { display: grid; grid-template-columns: 6.5rem 1fr 2.5rem; align-items: center; gap: .75rem; font-size: .88rem; }
  .bars strong { text-align: right; font-variant-numeric: tabular-nums; }
  .bar-label { color: var(--text-muted); }
  .bar-track { height: 10px; overflow: hidden; border-radius: 999px; background: var(--surface-2); }
  .bar-track span { display: block; height: 100%; border-radius: inherit; background: color-mix(in srgb, var(--primary) 55%, var(--surface-2)); }
  .bars li.peak .bar-track span { background: var(--grad-brand); }
  .bars li.peak .bar-label { color: var(--text); font-weight: 700; }
  .warm .bar-track span { background: color-mix(in srgb, var(--accent) 50%, var(--surface-2)); }
  .warm li.peak .bar-track span { background: var(--grad-warm); }
</style>
