<script lang="ts">
  import Topbar from '$lib/components/Topbar.svelte';
  let { data } = $props();
  const dailyPoints = $derived(
    JSON.stringify((data.daily ?? []).map((d) => ({ label: d.date.slice(5), value: d.completions })))
  );
</script>

<Topbar title="Analytics" />

<div class="grid tiles">
  <div class="card tile"><div class="k">Completed today</div><div class="v">{data.summary?.completedToday ?? 0}</div></div>
  <div class="card tile"><div class="k">This week</div><div class="v">{data.summary?.weekCompletions ?? 0}</div></div>
</div>

<div class="card">
  <h2>Daily completions</h2>
  {#if (data.daily ?? []).length}
    <hl-chart points={dailyPoints}></hl-chart>
    <table>
      <thead><tr><th>Date</th><th>Completions</th></tr></thead>
      <tbody>{#each data.daily as d}<tr><td>{d.date}</td><td>{d.completions}</td></tr>{/each}</tbody>
    </table>
  {:else}
    <p class="muted">No analytics yet — complete a habit or receive a wearable event.</p>
  {/if}
</div>

<div class="card">
  <h2>Weekly trend</h2>
  {#if (data.summary?.trend ?? []).length}
    <table>
      <thead><tr><th>Week</th><th>Completions</th></tr></thead>
      <tbody>{#each data.summary.trend as w}<tr><td>{w.week}</td><td>{w.completions}</td></tr>{/each}</tbody>
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
