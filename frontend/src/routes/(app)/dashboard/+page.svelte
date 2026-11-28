<script lang="ts">
  import Topbar from '$lib/components/Topbar.svelte';
  import LoadingState from '$lib/components/LoadingState.svelte';
  import { onMount } from 'svelte';
  import { api } from '$lib/api';
  import { userDateString } from '$lib/date';
  import { habitDayKind } from '$lib/dashboard';
  import { fetchAllPages } from '$lib/pagination';
  import { loadTodayActivity } from '$lib/today-activity';
  import { streakUnit } from '$lib/streak';
  import { pushToast } from '$lib/stores/toast.svelte';
  import type { AnalyticsSummary, Habit, Recommendation } from '$lib/types';

  let summary = $state<AnalyticsSummary | null>(null);
  let habits = $state<Habit[] | null>(null);
  let recs = $state<Recommendation[] | null>(null);
  let completedHabitIds = $state<string[] | null>(null);
  let today = $state<string | null>(null);
  let completingHabitIds = $state<string[]>([]);
  let loading = $state(true);
  let error = $state('');
  let loadRequest = 0;

  async function load() {
    const request = ++loadRequest;
    loading = true;
    error = '';
    const results = await Promise.allSettled([
      api.analytics.summary(),
      fetchAllPages(api.habits.list),
      api.recommendations.list(),
      loadTodayActivity()
    ]);
    if (request !== loadRequest) return;

    const failures: string[] = [];
    if (results[0].status === 'fulfilled') {
      summary = results[0].value;
    } else {
      summary = null;
      failures.push(`Analytics: ${errorMessage(results[0].reason)}`);
    }
    if (results[1].status === 'fulfilled') {
      habits = results[1].value;
    } else {
      habits = null;
      failures.push(`Habits: ${errorMessage(results[1].reason)}`);
    }
    if (results[2].status === 'fulfilled') {
      recs = results[2].value.items;
    } else {
      recs = null;
      failures.push(`Recommendations: ${errorMessage(results[2].reason)}`);
    }
    if (results[3].status === 'fulfilled') {
      completedHabitIds = results[3].value.completedHabitIds;
      today = results[3].value.date;
    } else {
      completedHabitIds = null;
      today = null;
      failures.push(`Today's habit progress: ${errorMessage(results[3].reason)}`);
    }
    error = failures.join(' ');
    loading = false;
  }

  function errorMessage(reason: unknown): string {
    return reason instanceof Error ? reason.message : 'Request failed.';
  }

  async function completeHabit(id: string) {
    if (completedHabitIds === null || completedHabitIds.includes(id) || completingHabitIds.includes(id)) return;
    completingHabitIds = [...completingHabitIds, id];
    try {
      const currentDate = await userDateString();
      if (currentDate !== today) {
        await load();
        pushToast('The day changed in IST. Check today’s status and try again.', 'info');
        return;
      }
      await api.habits.complete(id, { date: currentDate });
      pushToast('Marked done for today', 'success');
      await load();
    } catch (reason) {
      pushToast(reason instanceof Error ? reason.message : 'Failed to complete habit', 'error');
      await load();
    } finally {
      completingHabitIds = completingHabitIds.filter((habitId) => habitId !== id);
    }
  }

  onMount(() => {
    void load();
  });

  const activeHabitList = $derived((habits ?? []).filter((habit) => habit.active));
  const scheduledHabits = $derived(today ? activeHabitList.filter((habit) => habitDayKind(habit, today!) === 'scheduled') : []);
  const flexibleHabits = $derived(today ? activeHabitList.filter((habit) => habitDayKind(habit, today!) === 'flexible') : []);
  const restHabits = $derived(today ? activeHabitList.filter((habit) => habitDayKind(habit, today!) === 'rest') : []);
  const quickHabits = $derived([...scheduledHabits, ...flexibleHabits]);
  const completedTodayCount = $derived(scheduledHabits.filter((habit) => completedHabitIds?.includes(habit.id)).length);
  const bestDays = $derived((habits ?? []).filter((h) => streakUnit(h) === 'day').reduce((m, h) => Math.max(m, h.streak?.longest ?? 0), 0));
  const bestWeeks = $derived((habits ?? []).filter((h) => streakUnit(h) === 'week').reduce((m, h) => Math.max(m, h.streak?.longest ?? 0), 0));
  const trendPoints = $derived(
    JSON.stringify((summary?.trend ?? []).map((w) => ({ label: w.week.slice(-3), value: w.completions })))
  );
  const istHour = Number(new Intl.DateTimeFormat('en-IN', { timeZone: 'Asia/Kolkata', hour: 'numeric', hourCycle: 'h23' }).format(new Date()));
  const greeting = istHour < 12 ? 'Good morning' : istHour < 17 ? 'Good afternoon' : 'Good evening';
  let viewportWidth = $state(1280);
</script>

<svelte:window bind:innerWidth={viewportWidth} />

<Topbar title="Dashboard" subtitle="Your habits, progress and next steps at a glance." />

{#if error}
  <div class="error-banner" role="alert">{error} <button class="btn" onclick={() => load()}>Retry</button></div>
{/if}

{#if loading}
  <LoadingState message="Loading your dashboard…" />
{:else}
<section class="hero" aria-labelledby="hero-heading">
  <div class="hero-copy">
    <span class="hero-eyebrow">Today's focus</span>
    <h2 id="hero-heading">{greeting}</h2>
    {#if habits === null}
      <p>Your habits couldn't be loaded. Retry to see today's plan.</p>
    {:else if activeHabitList.length === 0}
      <p>Start with one small habit. A realistic schedule beats a perfect one.</p>
    {:else if completedHabitIds === null}
      <p>Today's completion status is unavailable right now.</p>
    {:else if scheduledHabits.length === 0}
      <p>Nothing is scheduled today. Work on a flexible goal or enjoy the rest.</p>
    {:else if completedTodayCount === scheduledHabits.length}
      <p role="status">All {scheduledHabits.length} scheduled habits are done. Great work — keep the streak alive tomorrow.</p>
    {:else}
      <p>{scheduledHabits.length - completedTodayCount} of {scheduledHabits.length} scheduled habits left today.</p>
    {/if}
    <div class="hero-meta">
      {#if completedHabitIds !== null && habits !== null}
        <span class="hero-chip">{flexibleHabits.length} flexible weekly {flexibleHabits.length === 1 ? 'goal' : 'goals'}</span>
        <span class="hero-chip">{restHabits.length} rest {restHabits.length === 1 ? 'day' : 'days'}</span>
      {/if}
      <a class="hero-link" href="/habits">{activeHabitList.length ? 'Manage habits' : 'Create your first habit'} <span aria-hidden="true">→</span></a>
    </div>
  </div>
  {#if completedHabitIds !== null && scheduledHabits.length > 0}
    <div class="hero-ring">
      <hl-progress-ring
        size={viewportWidth < 600 ? 112 : 148}
        value={completedTodayCount}
        max={scheduledHabits.length}
        label={`${completedTodayCount}/${scheduledHabits.length}`}
        ring-label="Scheduled habits completed today"
      ></hl-progress-ring>
      <span>done today</span>
    </div>
  {/if}
</section>

<div class="grid tiles">
  <hl-stat-card label="Completed today" icon="check" tone="primary" hint="All habits, IST" value={summary?.completedToday ?? 'Unavailable'}></hl-stat-card>
  <hl-stat-card label="This week" icon="calendar" tone="violet" hint="Since Monday" value={summary?.weekCompletions ?? 'Unavailable'}></hl-stat-card>
  <hl-stat-card label="Active habits" icon="target" tone="success" hint={habits === null ? undefined : `${(habits ?? []).length - activeHabitList.length} paused`} value={habits === null ? 'Unavailable' : activeHabitList.length}></hl-stat-card>
  <hl-stat-card label="Best streak" icon="flame" tone="accent" hint={habits === null ? undefined : `${bestWeeks} weeks for weekly goals`} value={habits === null ? 'Unavailable' : `${bestDays} days`}></hl-stat-card>
</div>

{#if habits !== null && completedHabitIds !== null && quickHabits.length > 0}
  <section class="card plan" aria-labelledby="plan-heading">
    <div class="section-head">
      <div>
        <h2 id="plan-heading">Quick complete</h2>
        <p class="muted">Scheduled habits first, then flexible weekly goals.</p>
      </div>
      {#if quickHabits.length > 6}<a class="btn btn-ghost" href="/habits">View all {activeHabitList.length} <span aria-hidden="true">→</span></a>{/if}
    </div>
    <div class="grid quick-list">
      {#each quickHabits.slice(0, 6) as habit (habit.id)}
        {@const flexible = habitDayKind(habit, today!) === 'flexible'}
        <div class="quick-item">
          <span class="pill {completedHabitIds.includes(habit.id) ? 'tone-success' : flexible ? 'tone-violet' : 'tone-primary'}">
            {completedHabitIds.includes(habit.id) ? 'Done today' : flexible ? 'Flexible weekly goal' : 'Scheduled today'}
          </span>
          <hl-habit-card
            name={habit.name}
            habit-id={habit.id}
            current={habit.streak?.current ?? 0}
            longest={habit.streak?.longest ?? 0}
            streak-unit={streakUnit(habit)}
            completed={completedHabitIds.includes(habit.id)}
            loading={completingHabitIds.includes(habit.id)}
            onhlComplete={(event: CustomEvent<string>) => completeHabit(event.detail)}
          ></hl-habit-card>
        </div>
      {/each}
    </div>
  </section>
{/if}

<div class="insights">
  <section class="card" aria-labelledby="trend-heading">
    <div class="section-head">
      <h2 id="trend-heading">Weekly trend</h2>
      <a class="btn btn-ghost" href="/analytics">Analytics <span aria-hidden="true">→</span></a>
    </div>
    {#if summary === null}
      <p class="muted">Weekly trend is unavailable.</p>
    {:else if summary.trend.length}
      <hl-chart points={trendPoints}></hl-chart>
    {:else}
      <div class="empty-state compact">
        <div class="empty-state-copy"><strong>Build your first streak</strong><p>Complete a habit to start seeing your weekly trend.</p></div>
      </div>
    {/if}
  </section>

  <section class="card" aria-labelledby="recs-heading">
    <div class="section-head">
      <h2 id="recs-heading">For you</h2>
      {#if recs?.length}<a class="btn btn-ghost" href="/recommendations">All tips <span aria-hidden="true">→</span></a>{/if}
    </div>
    {#if recs === null}
      <p class="muted">Recommendations are unavailable.</p>
    {:else if recs.length}
      <div class="recs">
        {#each recs.slice(0, 2) as r}
          <hl-recommendation-card type={r.type} headline={r.title} message={r.message} confidence={r.confidence}></hl-recommendation-card>
        {/each}
      </div>
    {:else}
      <div class="empty-state compact">
        <div class="empty-state-copy"><strong>Your tips are on the way</strong><p>Recommendations will appear as you build activity history.</p></div>
      </div>
    {/if}
  </section>
</div>
{/if}

<style>
  .hero {
    position: relative;
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 2rem;
    overflow: hidden;
    margin-bottom: 1.25rem;
    padding: clamp(1.5rem, 3vw, 2.25rem);
    border-radius: calc(var(--radius) + 4px);
    background:
      radial-gradient(22rem 14rem at 90% 10%, rgba(94, 234, 212, 0.35), transparent 70%),
      radial-gradient(18rem 12rem at 10% 120%, rgba(167, 139, 250, 0.28), transparent 70%),
      linear-gradient(135deg, #0b3a37 0%, #0a5f58 55%, #0b7a70 100%);
    color: #e9fbf8;
    box-shadow: var(--shadow-lg);
  }
  .hero::after {
    position: absolute;
    right: -60px;
    bottom: -90px;
    width: 260px;
    height: 260px;
    border: 1px solid rgba(255, 255, 255, 0.1);
    border-radius: 50%;
    box-shadow: 0 0 0 30px rgba(255, 255, 255, 0.03), 0 0 0 60px rgba(255, 255, 255, 0.02);
    content: '';
    pointer-events: none;
  }
  .hero-copy { position: relative; z-index: 1; max-width: 560px; }
  .hero-eyebrow { color: #99f6e4; font-size: .72rem; font-weight: 750; letter-spacing: .16em; text-transform: uppercase; }
  .hero h2 { margin: .35rem 0 .5rem; color: #fff; font-size: clamp(1.7rem, 3.4vw, 2.4rem); font-weight: 800; letter-spacing: -.04em; }
  .hero p { margin: 0; color: rgba(233, 251, 248, 0.85); font-size: 1.02rem; }
  .hero-meta { display: flex; flex-wrap: wrap; align-items: center; gap: .5rem; margin-top: 1.1rem; }
  .hero-chip { padding: .3rem .7rem; border: 1px solid rgba(255, 255, 255, 0.16); border-radius: 999px; background: rgba(255, 255, 255, 0.08); font-size: .78rem; font-weight: 600; }
  .hero-link { margin-left: .25rem; color: #fff; font-weight: 700; font-size: .88rem; text-decoration: underline; text-decoration-color: rgba(255, 255, 255, 0.35); text-underline-offset: 4px; }
  .hero-link:hover { color: #ccfbf1; text-decoration-color: currentColor; }
  .hero-link:focus-visible { outline-color: #fff; }
  .hero-ring { position: relative; z-index: 1; display: grid; justify-items: center; gap: .35rem; padding: .75rem; border-radius: 50%; }
  .hero-ring span { color: rgba(233, 251, 248, 0.8); font-size: .78rem; font-weight: 650; letter-spacing: .04em; }
  .tiles { grid-template-columns: repeat(auto-fit, minmax(190px, 1fr)); margin-bottom: 1.25rem; }
  .plan { margin-bottom: 1.25rem; }
  .plan .section-head p { margin: .15rem 0 0; font-size: .85rem; }
  .quick-list { grid-template-columns: repeat(auto-fill, minmax(min(100%, 270px), 1fr)); }
  .quick-item { display: grid; gap: .5rem; }
  .insights { display: grid; grid-template-columns: minmax(0, 1.2fr) minmax(0, 1fr); gap: 1.25rem; align-items: start; }
  .insights > .card { min-width: 0; overflow-x: auto; }
  .recs { display: grid; gap: .75rem; }
  @media (max-width: 960px) { .insights { grid-template-columns: minmax(0, 1fr); } }
  @media (max-width: 600px) {
    .hero { flex-direction: column; align-items: flex-start; }
    .hero-ring { align-self: center; }
  }
</style>