<script lang="ts">
  import Topbar from '$lib/components/Topbar.svelte';
  let { data } = $props();

  const activeHabits = $derived(data.habits.content.filter((h) => h.active).length);
  const bestStreak = $derived(
    data.habits.content.reduce((m, h) => Math.max(m, h.streak?.longest ?? 0), 0)
  );
  const trendPoints = $derived(
    JSON.stringify((data.summary?.trend ?? []).map((w) => ({ label: w.week.slice(-3), value: w.completions })))
  );
</script>

<Topbar title="Dashboard" />

<div class="grid tiles">
  <div class="card tile"><div class="k">Completed today</div><div class="v">{data.summary?.completedToday ?? 0}</div></div>
  <div class="card tile"><div class="k">This week</div><div class="v">{data.summary?.weekCompletions ?? 0}</div></div>
  <div class="card tile"><div class="k">Active habits</div><div class="v">{activeHabits}</div></div>
  <div class="card tile">
    <div class="k">Best streak</div>
    <div class="ring">
      <hl-progress-ring value={bestStreak} max={Math.max(bestStreak, 30)} label={`${bestStreak}d`}></hl-progress-ring>
      <span class="v small">{bestStreak} days</span>
    </div>
  </div>
</div>

<div class="row two">
  <div class="card grow">
    <h2>Weekly trend</h2>
    {#if (data.summary?.trend ?? []).length}
      <hl-chart points={trendPoints}></hl-chart>
      <ul class="trend">
        {#each data.summary.trend as w}<li><span class="muted">{w.week}</span><strong>{w.completions}</strong></li>{/each}
      </ul>
    {:else}
      <p class="muted">No activity yet — complete a habit to see your trend.</p>
    {/if}
  </div>

  <div class="card grow">
    <h2>Recommendations</h2>
    {#if data.recs.items.length}
      {#each data.recs.items.slice(0, 3) as r}
        <div class="rec">
          <span class="badge">{r.type}</span>
          <strong>{r.title}</strong>
          <p class="muted">{r.message}</p>
        </div>
      {/each}
      <a href="/recommendations">See all →</a>
    {:else}
      <p class="muted">Recommendations appear once you have some activity history.</p>
    {/if}
  </div>
</div>

<style>
  .tiles { grid-template-columns: repeat(auto-fit, minmax(170px, 1fr)); margin-bottom: 1rem; }
  .tile .k { color: var(--text-muted); font-size: 0.85rem; }
  .tile .v { font-size: 2rem; font-weight: 700; }
  .tile .v.small { font-size: 1rem; }
  .ring { display: flex; align-items: center; gap: 0.6rem; }
  .two { align-items: stretch; }
  .grow { flex: 1; min-width: 280px; }
  .trend { list-style: none; padding: 0; margin: 0.75rem 0 0; }
  .trend li { display: flex; justify-content: space-between; padding: 0.25rem 0; border-top: 1px solid var(--border); }
  .rec { padding: 0.5rem 0; border-top: 1px solid var(--border); }
  .rec strong { display: block; margin: 0.2rem 0; }
</style>
