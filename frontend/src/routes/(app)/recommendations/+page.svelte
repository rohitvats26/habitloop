<script lang="ts">
  import Topbar from '$lib/components/Topbar.svelte';
  import LoadingState from '$lib/components/LoadingState.svelte';
  import { onMount } from 'svelte';
  import { api, ApiError } from '$lib/api';
  import { pushToast } from '$lib/stores/toast.svelte';
  import { hasApplicableRecommendation, recommendationUpdate } from '$lib/recommendations';
  import type { Habit, Page, Recommendation } from '$lib/types';

  let items = $state<Recommendation[]>([]);
  let habits = $state<Habit[]>([]);
  let filterHabitId = $state('');
  let activeRecommendation = $state<Recommendation | null>(null);
  let selectedHabitId = $state('');
  let loading = $state(true);
  let error = $state('');
  let actionError = $state('');
  let loadingHabits = $state(false);
  let habitsLoading = $state(false);
  let habitsError = $state('');
  let applying = $state(false);

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
      const page = (await api.habits.list()) as Page<Habit>;
      habits = (page.content ?? []).filter((habit) => habit.active);
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
        const page = (await api.habits.list()) as Page<Habit>;
        habits = (page.content ?? []).filter((habit) => habit.active);
      } catch (e) {
        pushToast(e instanceof ApiError ? e.message : 'Could not load your habits', 'error');
        return;
      } finally {
        loadingHabits = false;
      }
    }
    if (habits.length === 0) {
      pushToast('Create an active habit before applying this recommendation.', 'info');
      return;
    }
    activeRecommendation = recommendation;
    selectedHabitId = recommendation.habitId && habits.some((habit) => habit.id === recommendation.habitId)
      ? recommendation.habitId
      : habits[0].id;
  }

  async function apply(recommendation: Recommendation) {
    const habit = habits.find((candidate) => candidate.id === selectedHabitId);
    if (!habit) {
      actionError = 'Choose an active habit to apply this recommendation.';
      return;
    }
    const body = recommendationUpdate(recommendation, habit);
    if (!body) {
      actionError = 'This recommendation does not include a change that can be applied automatically.';
      return;
    }
    applying = true;
    actionError = '';
    try {
      await api.habits.update(habit.id, body);
      pushToast('Recommendation applied to your habit', 'success');
      items = items.filter((item) => item !== recommendation);
      activeRecommendation = null;
    } catch (e) {
      actionError = e instanceof ApiError ? e.message : 'Could not apply the recommendation.';
    } finally {
      applying = false;
    }
  }
</script>

<Topbar title="Recommendations" subtitle="Personalized ideas to make your routines work better." />

<form class="card habit-filter" onsubmit={(event) => { event.preventDefault(); activeRecommendation = null; void load(); }}>
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
  <div class="grid list">
    {#each items as r}
      <div class="card rec">
        <hl-recommendation-card
          type={r.type}
          headline={r.title}
          message={r.message}
          confidence={r.confidence}
        ></hl-recommendation-card>
        {#if r.action?.label}
          {#if hasApplicableRecommendation(r)}
            <button class="btn btn-primary" onclick={() => prepareApply(r)} disabled={loadingHabits || applying}>
              {loadingHabits ? 'Loading habits…' : r.action.label}
            </button>
            {#if activeRecommendation === r}
              <div class="apply-panel">
                <label class="label" for="recommendation-habit">Apply to habit</label>
                <select class="input" id="recommendation-habit" bind:value={selectedHabitId}>
                  {#each habits as habit (habit.id)}
                    <option value={habit.id}>{habit.name}</option>
                  {/each}
                </select>
                <div class="apply-actions">
                  <button class="btn btn-primary" onclick={() => apply(r)} disabled={applying}>
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
      </div>
    {:else}
      <div class="empty-state">
        <div class="empty-state-copy"><strong>No recommendations just yet</strong><p>Keep logging activity and we'll find useful ways to support your routine.</p></div>
      </div>
    {/each}
  </div>
{/if}

<style>
  .list { grid-template-columns: repeat(auto-fit, minmax(280px, 1fr)); }
  .rec { display: grid; align-content: start; gap: .75rem; }
  .apply-panel { display: grid; gap: .5rem; }
  .apply-actions { display: flex; flex-wrap: wrap; gap: .5rem; }
  .error-text { color: var(--danger); margin: 0; font-size: .88rem; }
  .habit-filter { display: flex; align-items: end; flex-wrap: wrap; gap: .75rem; margin-bottom: 1rem; }
  .habit-filter .field { min-width: 220px; flex: 1; margin: 0; }
</style>
