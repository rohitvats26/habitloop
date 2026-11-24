<script lang="ts">
  import Topbar from '$lib/components/Topbar.svelte';
  import { onMount } from 'svelte';
  import { api } from '$lib/api';
  import type { AnalyticsSummary } from '$lib/types';

  let summary = $state<AnalyticsSummary | null>(null);
  let daily = $state<{ date: string; completions: number }[]>([]);

  async function safe<T>(p: Promise<unknown>, fb: T): Promise<T> {
    try {
      return (await p) as T;
    } catch {
      return fb;
    }
  }

  onMount(async () => {
    summary = await safe<AnalyticsSummary | null>(api.analytics.summary(), null);
    daily = await safe<{ date: string; completions: number }[]>(api.analytics.daily(), []);
  });

  const dailyPoints = $derived(
    JSON.stringify((daily ?? []).map((d) => ({ label: d.date.slice(5), value: d.completions })))
  );
</script>

<Topbar title="Analytics" />

<div class="grid tiles">
  <div class="card tile"><div class="k">Completed today</div><div class="v">{summary?.completedToday ?? 0}</div></div>
  <div class="card tile"><div class="k">This week</div><div class="v">{summary?.weekCompletions ?? 0}</div></div>
</div>

<div class="card">
  <h2>Daily completions</h2>
  {#if (daily ?? []).length}
    <hl-chart points={dailyPoints}></hl-chart>
    <table>
      <thead><tr><th>Date</th><th>Completions</th></tr></thead>
      <tbody>{#each daily as d}<tr><td>{d.date}</td><td>{d.completions}</td></tr>{/each}</tbody>
    </table>
  {:else}
    <p class="muted">No analytics yet — complete a habit or receive a wearable event.</p>
  {/if}
</div>

<div class="card">
  <h2>Weekly trend</h2>
  {#if (summary?.trend ?? []).length}
    <table>
      <thead><tr><th>Week</th><th>Completions</th></tr></thead>
      <tbody>{#each summary!.trend as w}<tr><td>{w.week}</td><td>{w.completions}</td></tr>{/each}</tbody>
    </table>
  {:else}<p class="muted">No weekly data yet.</p>{/if}
</div>

<style>
  .tiles { grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); margin-bottom: 1rem; }
  .tile .k { color: var(--text-muted); font-size: 0.85rem; }
  .tile .v { font-size: 2rem; font-weight: 700; }
  table { width: 100%; border-collapse: collapse; margin-top: 0.75rem; }
  th, td { text-align: left; padding: 0.5rem 0.4rem; border-bottom: 1px solid var(--border); }
  th { color: var(--text-muted); font-size: 0.85rem; }
</style>
