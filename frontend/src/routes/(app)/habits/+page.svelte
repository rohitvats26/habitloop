<script lang="ts">
  import { streakUnit } from '$lib/streak';
  import Topbar from '$lib/components/Topbar.svelte';
  import LoadingState from '$lib/components/LoadingState.svelte';
  import { onMount } from 'svelte';
  import { api, ApiError } from '$lib/api';
  import { habitDayKind } from '$lib/dashboard';
  import { fetchAllPages } from '$lib/pagination';
  import { loadTodayActivity } from '$lib/today-activity';
  import { userDateString } from '$lib/date';
  import { pushToast } from '$lib/stores/toast.svelte';
  import { guardUnsavedChanges, UNSAVED_CHANGES_MESSAGE } from '$lib/unsaved';
  import type { ActivityLog, Habit } from '$lib/types';

  let habits = $state<Habit[]>([]);
  let listError = $state('');
  let statusError = $state('');
  let formError = $state('');
  let formDetails = $state<string[]>([]);
  let hasLoadedHabits = $state(false);
  let today = $state<string | null>(null);
  let completedHabitIds = $state<string[] | null>(null);
  let pageIndex = $state(0);
  const pageSize = 20;
  type HabitStatus = 'done' | 'scheduled' | 'flexible' | 'rest' | 'paused' | 'unknown';
  type HabitFilter = 'all' | 'todo' | 'done' | 'paused';
  let filter = $state<HabitFilter>('all');
  const statusMeta: Record<HabitStatus, { label: string; tone: string; stripe: string }> = {
    done: { label: 'Done today', tone: 'tone-success', stripe: 'var(--success)' },
    scheduled: { label: 'Scheduled today', tone: 'tone-primary', stripe: 'var(--primary)' },
    flexible: { label: 'Flexible weekly goal', tone: 'tone-violet', stripe: 'var(--violet)' },
    rest: { label: 'Rest day', tone: 'tone-muted', stripe: 'var(--border)' },
    paused: { label: 'Paused', tone: 'tone-accent', stripe: 'var(--accent)' },
    unknown: { label: 'Status unavailable', tone: 'tone-muted', stripe: 'var(--border)' }
  };
  function habitStatus(habit: Habit): HabitStatus {
    if (!habit.active) return 'paused';
    if (!today || !completedHabitIds) return 'unknown';
    if (completedHabitIds.includes(habit.id)) return 'done';
    return habitDayKind(habit, today) as HabitStatus;
  }
  const counts = $derived({
    all: habits.length,
    todo: habits.filter((h) => ['scheduled', 'flexible'].includes(habitStatus(h))).length,
    done: habits.filter((h) => habitStatus(h) === 'done').length,
    paused: habits.filter((h) => !h.active).length
  });
  const filteredHabits = $derived(habits.filter((h) => {
    const status = habitStatus(h);
    if (filter === 'todo') return status === 'scheduled' || status === 'flexible';
    if (filter === 'done') return status === 'done';
    if (filter === 'paused') return status === 'paused';
    return true;
  }));
  const pageCount = $derived(Math.ceil(filteredHabits.length / pageSize));
  const currentPage = $derived(Math.min(pageIndex, Math.max(0, pageCount - 1)));
  const visibleHabits = $derived(filteredHabits.slice(currentPage * pageSize, (currentPage + 1) * pageSize));
  let loading = $state(true);
  let saving = $state(false);
  let completingHabitIds = $state<string[]>([]);
  let deletingHabitIds = $state<string[]>([]);
  let showForm = $state(false);
  let editingHabitId = $state<string | null>(null);
  let historyHabitId = $state<string | null>(null);
  let completionHistory = $state<ActivityLog[]>([]);
  let historyLoading = $state(false);
  let historyError = $state('');
  let historyRequest = 0;
  let historyPage = $state(0);
  let historyTotalPages = $state(0);
  let reloadRequest = 0;

  let name = $state('');
  let description = $state('');
  let frequency = $state<Habit['frequency']>('DAILY');
  let targetPerPeriod = $state(1);
  let targetTime = $state('');
  let selectedDays = $state<number[]>([]);
  let pausingHabitIds = $state<string[]>([]);
  const weekdays = [
    { value: 1, label: 'Monday' },
    { value: 2, label: 'Tuesday' },
    { value: 3, label: 'Wednesday' },
    { value: 4, label: 'Thursday' },
    { value: 5, label: 'Friday' },
    { value: 6, label: 'Saturday' },
    { value: 7, label: 'Sunday' }
  ];

  function describeSchedule(plan: Pick<Habit, 'frequency' | 'schedule' | 'targetPerPeriod'>): string {
    const days = plan.schedule.daysOfWeek;
    const parts: string[] = [];
    if (Array.isArray(days) && days.length > 0) {
      parts.push(days.map((day) => weekdays.find((item) => item.value === day)?.label ?? String(day)).join(', '));
      if (typeof plan.schedule.perWeek === 'number') parts.push(`(${plan.schedule.perWeek} per week)`);
    } else if (plan.frequency === 'DAILY') {
      parts.push('Every day');
    } else {
      parts.push(`${plan.schedule.perWeek ?? plan.targetPerPeriod} times per week`);
    }
    if (typeof plan.schedule.targetTime === 'string') parts.push(`at ${plan.schedule.targetTime}`);
    return parts.join(' ');
  }

  async function reload() {
    const request = ++reloadRequest;
    loading = true;
    listError = '';
    statusError = '';
    const [habitResult, activityResult] = await Promise.allSettled([
      fetchAllPages(api.habits.list),
      loadTodayActivity()
    ]);
    if (request !== reloadRequest) return;
    if (habitResult.status === 'fulfilled') {
      habits = habitResult.value;
      hasLoadedHabits = true;
    } else {
      listError = habitResult.reason instanceof Error ? habitResult.reason.message : 'Failed to load habits.';
    }
    if (activityResult.status === 'fulfilled') {
      today = activityResult.value.date;
      completedHabitIds = activityResult.value.completedHabitIds;
    } else {
      today = null;
      completedHabitIds = null;
      statusError = activityResult.reason instanceof Error ? activityResult.reason.message : 'Failed to load today’s status.';
    }
    loading = false;
  }

  onMount(reload);

  let formSnapshot = $state('');
  const formValues = () => JSON.stringify([name, description, frequency, Number(targetPerPeriod), targetTime, [...selectedDays].sort()]);
  const formDirty = () => showForm && formValues() !== formSnapshot;
  guardUnsavedChanges(formDirty);

  function confirmDiscard(): boolean {
    return !formDirty() || window.confirm(UNSAVED_CHANGES_MESSAGE);
  }

  function openNewForm() {
    resetForm();
    formError = '';
    formDetails = [];
    formSnapshot = formValues();
    showForm = true;
  }

  function closeForm() {
    if (!confirmDiscard()) return;
    resetForm();
    formError = '';
    formDetails = [];
    showForm = false;
  }

  async function saveHabit(e: SubmitEvent) {
    e.preventDefault();
    formError = '';
    formDetails = [];
    if (!name.trim()) {
      formError = 'Enter a habit name that is not blank.';
      return;
    }
    saving = true;
    try {
      const body = {
        name,
        description,
        frequency,
        targetPerPeriod: Number(targetPerPeriod),
        schedule: {
          ...(targetTime ? { targetTime } : {}),
          ...(frequency === 'CUSTOM' && selectedDays.length > 0 ? { daysOfWeek: selectedDays } : {}),
          ...(frequency !== 'DAILY' ? { perWeek: Number(targetPerPeriod) } : {})
        }
      };
      if (editingHabitId) {
        const updated = await api.habits.update(editingHabitId, body) as Habit;
        pushToast(updated.pendingSchedule
          ? `Habit updated; schedule changes start ${updated.pendingSchedule.effectiveDate} (IST)`
          : 'Habit updated', 'success');
      } else {
        await api.habits.create(body);
        pushToast('Habit created', 'success');
      }
      resetForm();
      showForm = false;
      await reload();
    } catch (e) {
      formError = e instanceof Error ? e.message : 'Unexpected error';
      formDetails = e instanceof ApiError ? e.details : [];
    } finally {
      saving = false;
    }
  }

  function edit(habit: Habit) {
    if (!confirmDiscard()) return;
    editingHabitId = habit.id;
    name = habit.name;
    description = habit.description ?? '';
    const planned = habit.pendingSchedule ?? habit;
    frequency = planned.frequency;
    targetPerPeriod = typeof planned.schedule?.perWeek === 'number'
      ? planned.schedule.perWeek
      : planned.targetPerPeriod;
    targetTime = String(planned.schedule?.targetTime ?? '');
    selectedDays = Array.isArray(planned.schedule?.daysOfWeek)
      ? planned.schedule.daysOfWeek.filter((day): day is number => typeof day === 'number')
      : [];
    formError = '';
    formDetails = [];
    formSnapshot = formValues();
    showForm = true;
  }

  function resetForm() {
    editingHabitId = null;
    name = '';
    description = '';
    frequency = 'DAILY';
    targetPerPeriod = 1;
    targetTime = '';
    selectedDays = [];
  }

  function toggleDay(day: number, checked: boolean) {
    selectedDays = checked
      ? [...new Set([...selectedDays, day])]
      : selectedDays.filter((selected) => selected !== day);
  }

  async function complete(id: string) {
    if (completedHabitIds === null || completedHabitIds.includes(id) || completingHabitIds.includes(id)) return;
    completingHabitIds = [...completingHabitIds, id];
    try {
      const currentDate = await userDateString();
      if (currentDate !== today) {
        await reload();
        pushToast('The day changed in IST. Check today’s status and try again.', 'info');
        return;
      }
      await api.habits.complete(id, { date: currentDate });
      await reload();
      pushToast('Marked done for today', 'success');
    } catch (e) {
      pushToast(e instanceof Error ? e.message : 'Failed', 'error');
    } finally {
      completingHabitIds = completingHabitIds.filter((habitId) => habitId !== id);
    }
  }

  async function togglePause(habit: Habit) {
    if (pausingHabitIds.includes(habit.id)) return;
    pausingHabitIds = [...pausingHabitIds, habit.id];
    try {
      if (habit.active) {
        await api.habits.pause(habit.id, 7);
        pushToast('Habit paused for 7 days', 'info');
      } else {
        await api.habits.resume(habit.id);
        pushToast('Habit resumed', 'success');
      }
      await reload();
    } catch (e) {
      pushToast(e instanceof ApiError ? e.message : 'Could not update the habit pause.', 'error');
    } finally {
      pausingHabitIds = pausingHabitIds.filter((habitId) => habitId !== habit.id);
    }
  }

  async function remove(id: string) {
    const habit = habits.find((candidate) => candidate.id === id);
    if (!habit) {
      pushToast('This habit is no longer available. Refresh the list and try again.', 'error');
      return;
    }
    if (deletingHabitIds.includes(id)) return;
    if (!window.confirm(`Remove "${habit.name}"? It will no longer appear in your habit list.`)) return;
    deletingHabitIds = [...deletingHabitIds, id];
    try {
      await api.habits.remove(id);
      await reload();
      pushToast('Habit deleted', 'info');
    } catch (e) {
      pushToast(e instanceof ApiError ? e.message : 'Failed', 'error');
    } finally {
      deletingHabitIds = deletingHabitIds.filter((habitId) => habitId !== id);
    }
  }

  async function toggleHistory(id: string) {
    if (historyHabitId === id) {
      historyHabitId = null;
      historyError = '';
      historyRequest += 1;
      return;
    }

    historyHabitId = id;
    await loadHistory(id, 0);
  }

  async function loadHistory(id: string, pageNumber: number) {
    completionHistory = [];
    historyError = '';
    historyLoading = true;
    const request = ++historyRequest;
    try {
      const page = await api.habits.completions(id, pageNumber);
      if (request === historyRequest) {
        completionHistory = page.content;
        historyPage = page.page;
        historyTotalPages = page.totalPages;
      }
    } catch (e) {
      if (request === historyRequest) {
        historyError = e instanceof ApiError ? e.message : 'Failed to load completion history.';
      }
    } finally {
      if (request === historyRequest) historyLoading = false;
    }
  }

  function formatCompletionDate(value: string) {
    return new Date(value).toLocaleDateString('en-IN', { timeZone: 'Asia/Kolkata', dateStyle: 'medium' });
  }
</script>

<Topbar title="Habits" subtitle="Create routines, set targets, and track your streaks." />

{#if listError}
  <div class="error-banner" role="alert">Habits: {listError}{hasLoadedHabits ? ' Showing the last loaded list.' : ''} <button class="btn" onclick={reload}>Retry</button></div>
{/if}
{#if statusError}
  <div class="error-banner" role="alert">Today’s completion status is unavailable: {statusError} <button class="btn" onclick={reload}>Retry</button></div>
{/if}

<div class="toolbar">
  <div class="filters" role="group" aria-label="Filter habits">
    {#each [['all', 'All'], ['todo', 'To do today'], ['done', 'Done today'], ['paused', 'Paused']] as [value, label] (value)}
      <button
        type="button"
        aria-pressed={filter === value}
        disabled={(value === 'todo' || value === 'done') && completedHabitIds === null}
        onclick={() => { filter = value as HabitFilter; pageIndex = 0; }}
      >
        {label}<span class="count">{counts[value as HabitFilter]}</span>
      </button>
    {/each}
  </div>
  <button class="btn {showForm ? '' : 'btn-primary'}" onclick={() => (showForm ? closeForm() : openNewForm())}>
    {showForm ? 'Cancel' : '+ New habit'}
  </button>
</div>

{#if showForm}
  <form onsubmit={saveHabit} class="card create accent-top">
    <h2>{editingHabitId ? 'Edit habit' : 'Create habit'}</h2>
    {#if formError}
      <div class="error-banner" role="alert">
        {formError}
        {#if formDetails.length}<ul>{#each formDetails as detail}<li>{detail}</li>{/each}</ul>{/if}
      </div>
    {/if}
    <div class="row">
      <div class="field grow"><label class="label" for="name">Name</label><input class="input" id="name" bind:value={name} maxlength="120" required /></div>
      <div class="field"><label class="label" for="frequency">Frequency</label>
        <select class="input" id="frequency" bind:value={frequency}>
          <option>DAILY</option><option>WEEKLY</option><option>CUSTOM</option>
        </select>
      </div>
    </div>
    <div class="field"><label class="label" for="description">Description</label><input class="input" id="description" bind:value={description} maxlength="500" /></div>
    <div class="row">
      <div class="field"><label class="label" for="targetPerPeriod">{frequency === 'DAILY' ? 'Target per day' : 'Weekly target'}</label><input class="input" id="targetPerPeriod" type="number" min="1" max={frequency === 'DAILY' ? 1 : 7} step="1" bind:value={targetPerPeriod} required /></div>
      <div class="field"><label class="label" for="targetTime">Target time</label><input class="input" id="targetTime" type="time" bind:value={targetTime} /></div>
    </div>
    {#if frequency === 'CUSTOM'}
      <fieldset class="weekdays">
        <legend class="label">Active weekdays <span class="field-hint">Choose days for a fixed schedule, or leave all unselected for a flexible weekly goal.</span></legend>
        {#each weekdays as day}
          <label>
            <input
              type="checkbox"
              checked={selectedDays.includes(day.value)}
              onchange={(event) => toggleDay(day.value, event.currentTarget.checked)}
            />
            {day.label}
          </label>
        {/each}
      </fieldset>
    {/if}
    <button class="btn btn-primary" type="submit" disabled={saving}>
      {saving ? 'Saving…' : editingHabitId ? 'Save changes' : 'Create habit'}
    </button>
  </form>
{/if}

{#if loading}
  <LoadingState message="Loading your habits…" />
{:else if hasLoadedHabits}
  <div class="grid list">
    {#each visibleHabits as h (h.id)}
      {@const status = habitStatus(h)}
      {@const done = completedHabitIds?.includes(h.id) ?? false}
      <article class="card habit accent-top" style={`--stripe: ${statusMeta[status].stripe}`} aria-labelledby={`habit-${h.id}`}>
        <header class="habit-head">
          <div>
            <h2 id={`habit-${h.id}`}>{h.name}</h2>
            {#if h.description}<p class="muted desc">{h.description}</p>{/if}
          </div>
          <span class="pill {statusMeta[status].tone}">{statusMeta[status].label}{status === 'done' && !h.active ? ' · paused' : ''}</span>
        </header>

        <div class="habit-body">
          <hl-progress-ring
            value={h.streak?.current ?? 0}
            max={Math.max(h.streak?.longest ?? 0, 7)}
            label={`${h.streak?.current ?? 0}`}
            ring-label={`${h.name} current streak`}
          ></hl-progress-ring>
          <div class="habit-stats">
            <p class="streak-line"><strong>{h.streak?.current ?? 0}-{streakUnit(h)} streak</strong>{' '}<span class="muted">· best {h.streak?.longest ?? 0}</span></p>
            <p class="schedule-line">
              <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 6h16v14H4zM8 3v4M16 3v4M4 10h16" /></svg>
              <span>{describeSchedule(h)}</span>
            </p>
            {#if h.pendingSchedule}
              <p class="pending">From {h.pendingSchedule.effectiveDate}: {describeSchedule(h.pendingSchedule)}</p>
            {/if}
          </div>
        </div>

        <div class="consistency" aria-label="Weekly schedule consistency">
          {#if h.active && h.weeklyProgress.expected > 0}
            <div class="consistency-label"><span>This week {h.weeklyProgress.completed}/{h.weeklyProgress.expected}</span><strong>{h.weeklyProgress.consistencyPercent}%</strong></div>
            <div class="bar" role="progressbar" aria-valuemin="0" aria-valuemax="100" aria-valuenow={h.weeklyProgress.consistencyPercent} aria-label={`${h.weeklyProgress.consistencyPercent}% weekly consistency`}>
              <span style={`width: ${Math.min(100, h.weeklyProgress.consistencyPercent)}%`}></span>
            </div>
          {:else if !h.active}
            <p class="muted">Paused{h.pausedUntil ? ` until ${h.pausedUntil}` : ''} — streaks are not affected.</p>
          {:else}
            <p class="muted">No scheduled days yet this week.</p>
          {/if}
        </div>

        <footer class="ops">
          <button class="btn btn-primary done-btn" class:is-done={done} onclick={() => complete(h.id)} disabled={completedHabitIds === null || done || completingHabitIds.includes(h.id)} aria-busy={completingHabitIds.includes(h.id)}>
            {completingHabitIds.includes(h.id) ? 'Saving…' : done ? '✓ Done today' : 'Mark done today'}
          </button>
          <div class="secondary">
            <button class="btn btn-ghost" onclick={() => toggleHistory(h.id)} aria-expanded={historyHabitId === h.id}>
              {historyHabitId === h.id ? 'Hide history' : 'History'}
            </button>
            <button class="btn btn-ghost" onclick={() => edit(h)}>Edit</button>
            <button class="btn btn-ghost" onclick={() => togglePause(h)} disabled={pausingHabitIds.includes(h.id)} aria-busy={pausingHabitIds.includes(h.id)}>
              {pausingHabitIds.includes(h.id) ? 'Updating…' : h.active ? 'Pause 7 days' : 'Resume'}
            </button>
            <button class="btn btn-ghost danger" onclick={() => remove(h.id)} disabled={deletingHabitIds.includes(h.id)} aria-busy={deletingHabitIds.includes(h.id)}>
              {deletingHabitIds.includes(h.id) ? 'Removing…' : 'Delete'}
            </button>
          </div>
        </footer>
        {#if historyHabitId === h.id}
          <section class="history" aria-label={`${h.name} completion history`}>
            <h3>Recent completions</h3>
            {#if historyLoading}
              <span class="muted">Loading history…</span>
            {:else if historyError}
              <p class="error-text" role="alert">{historyError}</p>
            {:else if completionHistory.length}
              <ul>
                {#each completionHistory as completion (completion.id)}
                  <li>{formatCompletionDate(completion.occurredAt)}</li>
                {/each}
              </ul>
              {#if historyTotalPages > 1}
                <nav class="pagination" aria-label={`${h.name} completion history pages`}>
                  <button class="btn" onclick={() => loadHistory(h.id, historyPage - 1)} disabled={historyPage === 0}>Previous</button>
                  <span>Page {historyPage + 1} of {historyTotalPages}</span>
                  <button class="btn" onclick={() => loadHistory(h.id, historyPage + 1)} disabled={historyPage + 1 >= historyTotalPages}>Next</button>
                </nav>
              {/if}
            {:else}
              <p class="muted">No completions recorded yet.</p>
            {/if}
          </section>
        {/if}
      </article>
    {:else}
      <div class="empty-state">
        {#if habits.length}
          <div class="empty-state-copy"><strong>No habits match this filter</strong><p>Try another filter to see the rest of your habits.</p></div>
        {:else}
          <div class="empty-state-copy"><strong>Your habit list is ready</strong><p>Create a habit to start tracking your progress.</p></div>
        {/if}
      </div>
    {/each}
  </div>
  {#if pageCount > 1}
    <nav class="pagination" aria-label="Habit pages">
      <button class="btn" onclick={() => (pageIndex = currentPage - 1)} disabled={currentPage === 0}>Previous</button>
      <span>Page {currentPage + 1} of {pageCount} · {filteredHabits.length} habits</span>
      <button class="btn" onclick={() => (pageIndex = currentPage + 1)} disabled={currentPage + 1 >= pageCount}>Next</button>
    </nav>
  {/if}
{/if}

<style>
  .toolbar { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: .75rem; margin-bottom: 1.25rem; }
  .filters { display: inline-flex; flex-wrap: wrap; gap: .25rem; padding: .3rem; border: 1px solid var(--border); border-radius: 14px; background: var(--surface); box-shadow: var(--shadow-sm); }
  .filters button { display: inline-flex; align-items: center; gap: .45rem; min-height: 36px; padding: .35rem .8rem; border: 0; border-radius: 10px; background: transparent; color: var(--text-muted); font: 620 .85rem var(--font); cursor: pointer; transition: background-color .15s ease, color .15s ease; }
  .filters button:hover:not(:disabled) { color: var(--text); background: var(--surface-2); }
  .filters button[aria-pressed='true'] { background: color-mix(in srgb, var(--primary) 14%, var(--surface)); color: var(--primary-strong); }
  .filters button:disabled { opacity: .5; cursor: not-allowed; }
  .filters button:focus-visible { outline: 2px solid var(--primary); outline-offset: 2px; }
  .count { min-width: 1.4rem; padding: 0 .35rem; border-radius: 999px; background: var(--surface-2); font-size: .72rem; text-align: center; font-variant-numeric: tabular-nums; }
  [aria-pressed='true'] .count { background: color-mix(in srgb, var(--primary) 22%, var(--surface)); }
  .create { max-width: 900px; margin-bottom: 1.25rem; }
  .create h2 { font-size: 1.25rem; }
  .grow { flex: 1; }
  .list { grid-template-columns: repeat(auto-fill, minmax(min(100%, 420px), 1fr)); gap: 1.1rem; }
  .habit { display: grid; gap: 1rem; align-content: start; padding-top: 1.5rem; }
  .habit-head { display: flex; align-items: flex-start; justify-content: space-between; gap: .75rem; }
  .habit-head h2 { margin: 0; font-size: 1.15rem; }
  .desc { margin: .2rem 0 0; font-size: .88rem; }
  .habit-body { display: flex; align-items: center; gap: 1rem; }
  .habit-stats { display: grid; gap: .3rem; min-width: 0; }
  .habit-stats p { margin: 0; }
  .streak-line strong { font-size: 1rem; }
  .schedule-line { display: flex; align-items: center; gap: .4rem; color: var(--text-muted); font-size: .85rem; }
  .schedule-line svg { width: 1rem; height: 1rem; flex: none; fill: none; stroke: currentColor; stroke-width: 1.8; stroke-linecap: round; stroke-linejoin: round; }
  .pending { padding: .3rem .55rem; border-radius: 8px; background: color-mix(in srgb, var(--violet) 10%, transparent); color: color-mix(in srgb, var(--violet) 80%, var(--text)); font-size: .78rem; font-weight: 600; }
  .consistency p { margin: 0; font-size: .85rem; }
  .consistency-label { display: flex; justify-content: space-between; margin-bottom: .35rem; color: var(--text-muted); font-size: .8rem; }
  .consistency-label strong { color: var(--text); }
  .bar { height: 8px; overflow: hidden; border-radius: 999px; background: var(--surface-2); }
  .bar span { display: block; height: 100%; border-radius: inherit; background: var(--grad-brand); transition: width .5s cubic-bezier(.2, .8, .2, 1); }
  .ops { display: grid; gap: .6rem; padding-top: .9rem; border-top: 1px solid var(--border); }
  .done-btn { justify-content: center; }
  .done-btn.is-done { background: color-mix(in srgb, var(--success) 13%, var(--surface)); color: color-mix(in srgb, var(--success) 85%, var(--text)); box-shadow: none; text-shadow: none; opacity: 1; cursor: default; }
  .secondary { display: flex; flex-wrap: wrap; gap: .2rem; }
  .secondary .btn { min-height: 36px; padding: .35rem .65rem; font-size: .84rem; }
  .danger:hover:not(:disabled) { color: var(--danger); background: color-mix(in srgb, var(--danger) 10%, transparent); }
  .history { border-top: 1px solid var(--border); padding-top: .85rem; }
  .history ul { display: flex; flex-wrap: wrap; gap: .4rem; margin: 0; padding: 0; list-style: none; }
  .history li { padding: .35rem .65rem; border-radius: 999px; background: color-mix(in srgb, var(--success) 12%, transparent); color: color-mix(in srgb, var(--success) 85%, var(--text)); font-size: .8rem; font-weight: 600; }
  .history h3 { margin: 0 0 .55rem; font-size: .95rem; }
  .error-text { color: var(--danger); }
  .weekdays { display: flex; flex-wrap: wrap; gap: 0.5rem; border: 0; padding: 0; margin: 0 0 1rem; }
  .weekdays legend { width: 100%; margin-bottom: .4rem; }
  .weekdays label { display: inline-flex; align-items: center; gap: 0.4rem; min-height: 38px; padding: .4rem .75rem; border: 1px solid var(--border); border-radius: 999px; background: var(--surface); cursor: pointer; font-size: .88rem; }
  .weekdays label:has(input:checked) { border-color: var(--primary); background: color-mix(in srgb, var(--primary) 12%, var(--surface)); color: var(--primary-strong); font-weight: 650; }
  @media (prefers-reduced-motion: reduce) { .bar span { transition: none; } }
  @media (max-width: 560px) {
    .toolbar { align-items: stretch; flex-direction: column; }
    .filters { width: 100%; flex-wrap: nowrap; overflow-x: auto; scrollbar-width: none; }
    .filters button { flex: none; white-space: nowrap; }
    .habit-head { flex-direction: column; }
  }
</style>
