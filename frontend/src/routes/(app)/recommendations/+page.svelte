<script lang="ts">
  import Topbar from '$lib/components/Topbar.svelte';
  import LoadingState from '$lib/components/LoadingState.svelte';
  import { onMount } from 'svelte';
  import { api, ApiError } from '$lib/api';
  import { fetchAllPages } from '$lib/pagination';
  import { pushToast } from '$lib/stores/toast.svelte';
  import {
    hasApplicableRecommendation,
    isRecommendationApplied,
    eligibleRecommendationHabits,
    recommendationsForCategory,
    recommendationUpdate,
    type RecommendationCategory
  } from '$lib/recommendations';
  import type { Habit, Recommendation } from '$lib/types';

  let items = $state<Recommendation[]>([]);
  let habits = $state<Habit[]>([]);
  let filterHabitId = $state('');
  let selectedCategory = $state<RecommendationCategory>('ALL');
  let activeRecommendation = $state<Recommendation | null>(null);
  let selectedHabitId = $state('');
  let loading = $state(true);
  let error = $state('');
  let actionError = $state('');
  let loadingHabits = $state(false);
  let habitsLoading = $state(false);
  let habitsError = $state('');
  let applying = $state(false);
  const categories: { value: RecommendationCategory; label: string }[] = [
    { value: 'ALL', label: 'All insights' },
    { value: 'TIMING', label: 'Best time' },
    { value: 'FREQUENCY', label: 'Weekly goal' },
    { value: 'CONSISTENCY', label: 'Consistency' }
  ];
  const visibleItems = $derived(recommendationsForCategory(items, selectedCategory));
  const eligibleHabits = $derived(activeRecommendation ? eligibleRecommendationHabits(activeRecommendation, habits) : []);
  const selectedHabit = $derived(eligibleHabits.find((habit) => habit.id === selectedHabitId));

  async function load() {
    loading = true;
    error = '';
    try {
      const res = filterHabitId
        ? await api.recommendations.forHabit(filterHabitId)
        : await api.recommendations.list();
      items = res.items ?? [];
    } catch (e) {
      error = e instanceof ApiError ? e.message : 'Failed to load recommendations';
    } finally {
      loading = false;
    }
  }

  async function loadHabits() {
    habitsLoading = true;
    habitsError = '';
    try {
      const allHabits = await fetchAllPages(api.habits.list);
      habits = allHabits.filter((habit) => habit.active);
    } catch (e) {
      habitsError = e instanceof ApiError ? e.message : 'Failed to load your habits.';
    } finally {
      habitsLoading = false;
    }
  }

  onMount(() => {
    void load();
    void loadHabits();
  });

  async function prepareApply(recommendation: Recommendation) {
    actionError = '';
    if (!hasApplicableRecommendation(recommendation)) return;
    if (habits.length === 0) {
      loadingHabits = true;
      try {
        await loadHabits();
      } finally {
        loadingHabits = false;
      }
      if (habitsError) return;
    }
    if (habits.length === 0) {
      pushToast('Create an active habit before applying this recommendation.', 'info');
      return;
    }
    const eligible = eligibleRecommendationHabits(recommendation, habits);
    if (eligible.length === 0) {
      pushToast('The recommended habit is no longer active or available.', 'info');
      return;
    }
    activeRecommendation = recommendation;
    selectedHabitId = (eligible.find((habit) => !isRecommendationApplied(recommendation, habit)) ?? eligible[0]).id;
  }

  async function apply(recommendation: Recommendation) {
    const habit = eligibleRecommendationHabits(recommendation, habits).find((candidate) => candidate.id === selectedHabitId);
    if (!habit) {
      actionError = 'Choose an active habit to apply this recommendation.';
      return;
    }
    applying = true;
    actionError = '';
    try {
      const current = (await api.habits.get(habit.id)) as Habit;
      habits = habits.map((candidate) => candidate.id === current.id ? current : candidate);
      if (!current.active) {
        actionError = 'This habit is no longer active.';
        return;
      }
      if (isRecommendationApplied(recommendation, current)) {
        actionError = 'This recommendation is already applied or scheduled for this habit.';
        return;
      }
      const body = recommendationUpdate(recommendation, current);
      if (!body) {
        actionError = 'This recommendation does not include a change that can be applied automatically.';
        return;
      }
      const updated = (await api.habits.update(current.id, body)) as Habit;
      habits = habits.map((candidate) => candidate.id === updated.id ? updated : candidate);
      pushToast(updated.pendingSchedule
        ? `Recommendation scheduled for ${updated.pendingSchedule.effectiveDate} (IST)`
        : 'Recommendation applied to your habit', 'success');
      activeRecommendation = null;
    } catch (e) {
      actionError = e instanceof ApiError ? e.message : 'Could not apply the recommendation.';
    } finally {
      applying = false;
    }
  }
</script>

<Topbar title="Recommendations" subtitle="Personalized ideas to make your routines work better." />

<div class="insight-note">
  <span class="note-icon" aria-hidden="true">✦</span>
  <p>These ideas reflect patterns in your recent activity. Adjust or ignore any suggestion—your routine is yours.</p>
  <span class="note-label">Optional guidance</span>
</div>

<form class="card habit-filter" onsubmit={(event) => { event.preventDefault(); activeRecommendation = null; void Promise.all([load(), loadHabits()]); }}>
  <div class="field">
    <label class="label" for="recommendation-filter-habit">Show recommendations for</label>
    <select class="input" id="recommendation-filter-habit" bind:value={filterHabitId} disabled={habitsLoading}>
      <option value="">All my habits</option>
      {#each habits as habit (habit.id)}
        <option value={habit.id}>{habit.name}</option>
      {/each}
    </select>
  </div>
  <button class="btn btn-primary" type="submit" disabled={loading || habitsLoading}>
    {loading ? 'Refreshing…' : 'Refresh recommendations'}
  </button>
  {#if habitsError}<span class="error-text" role="alert">{habitsError}</span>{/if}
</form>

{#if error}
  <div class="error-banner" role="alert">{error} <button class="btn" onclick={load}>Retry</button></div>
{/if}

{#if loading}
  <LoadingState message="Loading your recommendations…" />
{:else if !error}
  {#if items.length > 0}
    <div class="recommendation-toolbar">
      <div class="filter-label">
        <span class="eyebrow">EXPLORE BY FOCUS</span>
        <p class="muted">Choose the kind of nudge you need today.</p>
      </div>
      <div class="category-filters" role="group" aria-label="Filter recommendations by focus">
        {#each categories as category (category.value)}
          <button
            type="button"
            class="category-filter"
            class:selected={selectedCategory === category.value}
            aria-pressed={selectedCategory === category.value}
            onclick={() => (selectedCategory = category.value)}
          >
            {category.label}
            <span class="filter-count">{recommendationsForCategory(items, category.value).length}</span>
          </button>
        {/each}
      </div>
    </div>
  {/if}
  <div class="grid list">
    {#each visibleItems as r}
      <article class="rec">
        <hl-recommendation-card
          type={r.type}
          headline={r.title}
          message={r.message}
          confidence={r.confidence}
        ></hl-recommendation-card>
        {#if r.action?.label}
          {#if hasApplicableRecommendation(r)}
            {@const targets = eligibleRecommendationHabits(r, habits)}
            {@const allApplied = !habitsLoading && !habitsError && targets.length > 0 && targets.every((habit) => isRecommendationApplied(r, habit))}
            <button class="btn btn-primary" onclick={() => prepareApply(r)} disabled={loadingHabits || habitsLoading || applying || allApplied}>
              {#if loadingHabits || habitsLoading}Loading habits…
              {:else if allApplied}{r.habitId ? 'Already applied or scheduled' : 'Applied or scheduled for all active habits'}
              {:else}{r.action.label}{/if}
            </button>
            {#if activeRecommendation === r}
              <div class="apply-panel">
                <label class="label" for="recommendation-habit">Apply to habit</label>
                <select class="input" id="recommendation-habit" bind:value={selectedHabitId}>
                  {#each eligibleHabits as habit (habit.id)}
                    <option value={habit.id}>{habit.name}{isRecommendationApplied(r, habit) ? ' (applied or scheduled)' : ''}</option>
                  {/each}
                </select>
                {#if selectedHabit && typeof r.action.suggestedPerWeek === 'number' && 'daysOfWeek' in (selectedHabit.pendingSchedule?.schedule ?? selectedHabit.schedule)}
                  <p class="field-hint">Applying this weekly goal replaces the habit's selected weekdays with a flexible weekly target.</p>
                {/if}
                <div class="apply-actions">
                  <button class="btn btn-primary" onclick={() => apply(r)} disabled={applying || !selectedHabit || isRecommendationApplied(r, selectedHabit)}>
                    {applying ? 'Applying…' : 'Apply recommendation'}
                  </button>
                  <button class="btn" onclick={() => (activeRecommendation = null)} disabled={applying}>Cancel</button>
                </div>
                {#if actionError}<p class="error-text" role="alert">{actionError}</p>{/if}
              </div>
            {/if}
          {:else}
            <a class="btn" href="/habits">View habits</a>
          {/if}
        {/if}
      </article>
    {:else}
      {#if items.length === 0}
        <div class="empty-state">
          <div class="empty-state-copy"><strong>No recommendations just yet</strong><p>Keep logging activity to uncover patterns and get useful ideas for your routine.</p></div>
        </div>
      {:else}
        <div class="empty-state no-category-results">
          <div class="empty-state-copy">
            <strong>No {categories.find((category) => category.value === selectedCategory)?.label.toLowerCase()} ideas right now</strong>
            <p>Try another focus or check back after logging more activity.</p>
          </div>
          <button class="btn" type="button" onclick={() => (selectedCategory = 'ALL')}>Show all insights</button>
        </div>
      {/if}
    {/each}
  </div>
{/if}

<style>
  .insight-note {
    display: flex; align-items: center; gap: .75rem; margin: -0.75rem 0 1rem; padding: .8rem 1rem;
    border: 1px solid color-mix(in srgb,var(--primary) 25%,var(--border)); border-radius: 14px;
    background: linear-gradient(110deg,color-mix(in srgb,var(--primary) 8%,var(--surface)),var(--surface));
  }
  .note-icon { display: grid; width: 2rem; height: 2rem; flex: none; place-items: center; border-radius: 10px; background: color-mix(in srgb,var(--primary) 15%,var(--surface)); color: var(--primary); }
  .insight-note p { flex: 1; margin: 0; color: var(--text-muted); font-size: .84rem; }
  .note-label { color: var(--primary-strong); font-size: .72rem; font-weight: 700; white-space: nowrap; }
  .list { grid-template-columns: repeat(auto-fit, minmax(min(100%, 300px), 1fr)); }
  .rec { display: grid; align-content: start; gap: .75rem; }
  .no-category-results button { grid-column: 2; justify-self: start; }
  .apply-panel { display: grid; gap: .5rem; }
  .apply-actions { display: flex; flex-wrap: wrap; gap: .5rem; }
  .error-text { color: var(--danger); margin: 0; font-size: .88rem; }
  .habit-filter { display: flex; align-items: end; flex-wrap: wrap; gap: .75rem; margin-bottom: 1rem; }
  .habit-filter .field { min-width: 220px; flex: 1; margin: 0; }
  .recommendation-toolbar { display: flex; align-items: end; justify-content: space-between; flex-wrap: wrap; gap: 1rem; margin: 1.25rem 0 .8rem; }
  .filter-label p { margin: .15rem 0 0; font-size: .8rem; }
  .category-filters { display: flex; flex-wrap: wrap; gap: .4rem; }
  .category-filter {
    display: inline-flex; align-items: center; gap: .45rem; padding: .45rem .68rem;
    border: 1px solid var(--border); border-radius: 999px; background: var(--surface);
    color: var(--text-muted); font: 600 .78rem var(--font); cursor: pointer;
    transition: border-color .15s ease, color .15s ease, background .15s ease;
  }
  .category-filter:hover { border-color: color-mix(in srgb,var(--primary) 45%,var(--border)); color: var(--text); }
  .category-filter.selected { border-color: color-mix(in srgb,var(--primary) 40%,var(--border)); background: color-mix(in srgb,var(--primary) 12%,var(--surface)); color: var(--primary-strong); }
  .filter-count { display: grid; min-width: 1.2rem; height: 1.2rem; place-items: center; padding: 0 .25rem; border-radius: 999px; background: color-mix(in srgb,var(--primary) 12%,var(--surface)); font-size: .68rem; font-variant-numeric: tabular-nums; }
  .category-filter:focus-visible { outline: 2px solid var(--primary); outline-offset: 2px; }
  @media (max-width: 560px) {
    .insight-note { align-items: flex-start; flex-wrap: wrap; }
    .note-label { margin-left: 2.75rem; }
    .recommendation-toolbar { align-items: flex-start; flex-direction: column; }
  }
  @media (prefers-reduced-motion: reduce) { .category-filter { transition: none; } }
</style>
