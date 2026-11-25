<script lang="ts">
  import Topbar from '$lib/components/Topbar.svelte';
  import LoadingState from '$lib/components/LoadingState.svelte';
  import { onMount } from 'svelte';
  import { api, ApiError } from '$lib/api';
  import { userDateString } from '$lib/date';
  import { completedHabitIdsForDay } from '$lib/dashboard';
  import { pushToast } from '$lib/stores/toast.svelte';
  import { INDIA_TIMEZONE_OFFSET } from '$lib/timezone';
  import type { ActivityLog, AnalyticsSummary, Habit, Page, Recommendation } from '$lib/types';

  let summary = $state<AnalyticsSummary | null>(null);
  let habits = $state<Habit[]>([]);
  let recs = $state<Recommendation[]>([]);
  let completedHabitIds = $state<string[]>([]);
  let completingHabitIds = $state<string[]>([]);
  let loading = $state(true);
  let error = $state('');

  async function load(showLoading = true) {
    if (showLoading) loading = true;
    error = '';
    let activityRequest: Promise<unknown>;
    try {
      const today = await userDateString();
      activityRequest = api.activity.log({
        type: 'HABIT_DONE',
        from: `${today}T00:00:00${INDIA_TIMEZONE_OFFSET}`,
        to: `${today}T23:59:59.999999999${INDIA_TIMEZONE_OFFSET}`,
        page: 0,
        size: 100
      });
    } catch (reason) {
      activityRequest = Promise.reject(reason);
    }

    const results = await Promise.allSettled([
      api.analytics.summary(),
      api.habits.list(),
      api.recommendations.list(),
      activityRequest
    ]);

    const failures: string[] = [];
    if (results[0].status === 'fulfilled') {
      summary = results[0].value as AnalyticsSummary;
    } else {
      failures.push(`Analytics: ${errorMessage(results[0].reason)}`);
    }
    if (results[1].status === 'fulfilled') {
      habits = (results[1].value as Page<Habit>).content ?? [];
    } else {
      failures.push(`Habits: ${errorMessage(results[1].reason)}`);
    }
    if (results[2].status === 'fulfilled') {
      recs = (results[2].value as { items: Recommendation[] }).items ?? [];
    } else {
      failures.push(`Recommendations: ${errorMessage(results[2].reason)}`);
    }
    if (results[3].status === 'fulfilled') {
      completedHabitIds = completedHabitIdsForDay((results[3].value as Page<ActivityLog>).content ?? []);
    } else {
      failures.push(`Today's habit progress: ${errorMessage(results[3].reason)}`);
    }
    error = failures.join(' ');
    if (showLoading) loading = false;
  }

  function errorMessage(reason: unknown): string {
    return reason instanceof ApiError ? reason.message : 'Request failed.';
  }

  async function completeHabit(id: string) {
    if (completedHabitIds.includes(id) || completingHabitIds.includes(id)) return;
    completingHabitIds = [...completingHabitIds, id];
    try {
      await api.habits.complete(id, { date: await userDateString() });
      completedHabitIds = [...completedHabitIds, id];
      pushToast('Marked done for today', 'success');
      await load(false);
    } catch (reason) {
      pushToast(reason instanceof Error ? reason.message : 'Failed to complete habit', 'error');
      await load(false);
    } finally {
      completingHabitIds = completingHabitIds.filter((habitId) => habitId !== id);
    }
  }

  onMount(() => {
    void load();
  });

  const activeHabits = $derived(habits.filter((h) => h.active).length);
  const activeHabitList = $derived(habits.filter((habit) => habit.active));
  const completedTodayCount = $derived(
    activeHabitList.filter((habit) => completedHabitIds.includes(habit.id)).length
  );
  const remainingHabits = $derived(activeHabitList.length - completedTodayCount);
  const bestStreak = $derived(habits.reduce((m, h) => Math.max(m, h.streak?.longest ?? 0), 0));
  const trendPoints = $derived(
    JSON.stringify((summary?.trend ?? []).map((w) => ({ label: w.week.slice(-3), value: w.completions })))
  );
</script>

<Topbar title="Dashboard" subtitle="A quick view of your habits, progress, and next steps." />

{#if error}
  <div class="error-banner" role="alert">{error} <button class="btn" onclick={() => load()}>Retry</button></div>
{/if}

{#if loading}
  <LoadingState message="Loading your dashboard…" />
{:else}
<div class="grid tiles">
  <hl-stat-card label="Completed today" value={summary?.completedToday ?? 0}></hl-stat-card>
  <hl-stat-card label="This week" value={summary?.weekCompletions ?? 0}></hl-stat-card>
  <hl-stat-card label="Active habits" value={activeHabits}></hl-stat-card>
  <hl-stat-card label="Best streak" value={`${bestStreak} days`}></hl-stat-card>
</div>

{#if activeHabits > 0}
  <section class="card quick">
    <div class="quick-heading">
      <div>
        <h2>Today's plan</h2>
        <p class="muted">{completedTodayCount} of {activeHabits} active habits completed · India Standard Time</p>
      </div>
      <a class="btn" href="/habits">Manage habits</a>
    </div>
    <progress
      class="daily-progress"
      max={activeHabits}
      value={completedTodayCount}
      aria-label={`${completedTodayCount} of ${activeHabits} habits completed today`}
    ></progress>
    {#if remainingHabits === 0}
      <p class="progress-message" role="status">Your active habits are complete for today. Great work!</p>
    {:else}
      <h3 class="quick-label">Quick complete</h3>
    {/if}
    <div class="grid quick-list">
      {#each activeHabitList.slice(0, 3) as habit (habit.id)}
        <hl-habit-card
          name={habit.name}
          habit-id={habit.id}
          current={habit.streak?.current ?? 0}
          longest={habit.streak?.longest ?? 0}
          completed={completedHabitIds.includes(habit.id)}
          loading={completingHabitIds.includes(habit.id)}
          onhlComplete={(event: CustomEvent<string>) => completeHabit(event.detail)}
        ></hl-habit-card>
      {/each}
    </div>
    {#if activeHabitList.length > 3}
      <p class="muted more-habits">Showing your first 3 habits. <a href="/habits">View all {activeHabits}</a></p>
    {/if}
  </section>
{:else}
  <section class="card welcome">
    <span class="eyebrow">A fresh start</span>
    <h2>Build your first healthy routine</h2>
    <p class="muted">Choose one small habit, set a schedule that feels realistic, and track your progress here.</p>
    <a class="btn btn-primary" href="/habits">Create your first habit</a>
  </section>
{/if}

<div class="row two">
  <div class="card grow">
    <h2>Weekly trend</h2>
    {#if (summary?.trend ?? []).length}
      <hl-chart points={trendPoints}></hl-chart>
      <ul class="trend">
        {#each summary!.trend as w}<li><span class="muted">{w.week}</span><strong>{w.completions}</strong></li>{/each}
      </ul>
    {:else}
      <div class="empty-state compact">
        <div class="empty-state-copy"><strong>Build your first streak</strong><p>Complete a habit to start seeing your weekly trend.</p></div>
      </div>
    {/if}
  </div>

  <div class="card grow">
    <h2>Recommendations</h2>
    {#if recs.length}
      {#each recs.slice(0, 3) as r}
        <div class="rec">
          <span class="badge">{r.type}</span>
          <strong>{r.title}</strong>
          <p class="muted">{r.message}</p>
        </div>
      {/each}
      <a href="/recommendations">See all →</a>
    {:else}
      <div class="empty-state compact">
        <div class="empty-state-copy"><strong>Your tips are on the way</strong><p>Recommendations will appear as you build activity history.</p></div>
      </div>
    {/if}
  </div>
</div>
{/if}

<style>
  .tiles { grid-template-columns: repeat(auto-fit, minmax(170px, 1fr)); margin-bottom: 1rem; }
  .quick { margin-bottom: 1rem; }
  .quick-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 1rem; }
  .quick-heading p { margin: .2rem 0 .75rem; font-size: .85rem; }
  .daily-progress { width: 100%; height: .6rem; margin: 0 0 .8rem; accent-color: var(--primary); }
  .quick-label { margin: .15rem 0 .75rem; font-size: .95rem; }
  .progress-message { margin: .3rem 0 .8rem; color: var(--success); font-weight: 650; }
  .more-habits { margin: .8rem 0 0; font-size: .85rem; }
  .welcome { display: grid; justify-items: start; gap: .45rem; margin-bottom: 1rem; }
  .welcome h2, .welcome p { margin: 0; }
  .welcome .btn { margin-top: .5rem; }
  .quick-list { grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); }
  .two { align-items: stretch; }
  .grow { flex: 1; min-width: 280px; }
  .trend { list-style: none; padding: 0; margin: 0.75rem 0 0; }
  .trend li { display: flex; justify-content: space-between; padding: 0.25rem 0; border-top: 1px solid var(--border); }
  .rec { padding: 0.5rem 0; border-top: 1px solid var(--border); }
  .rec strong { display: block; margin: 0.2rem 0; }
  @media (max-width: 560px) {
    .quick-heading { flex-direction: column; }
    .quick-heading .btn { width: 100%; justify-content: center; }
  }
</style>
