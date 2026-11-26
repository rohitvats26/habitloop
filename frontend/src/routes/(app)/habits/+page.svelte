<script lang="ts">
  import Topbar from '$lib/components/Topbar.svelte';
  import LoadingState from '$lib/components/LoadingState.svelte';
  import { onMount } from 'svelte';
  import { api, ApiError } from '$lib/api';
  import { userDateString } from '$lib/date';
  import { pushToast } from '$lib/stores/toast.svelte';
  import type { ActivityLog, Habit, Page } from '$lib/types';

  let habits = $state<Habit[]>([]);
  let error = $state('');
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

  async function reload() {
    loading = true;
    error = '';
    try {
      const h = (await api.habits.list()) as Page<Habit>;
      habits = h.content ?? [];
    } catch (e) {
      error = e instanceof ApiError ? e.message : 'Failed to load habits';
    } finally {
      loading = false;
    }
  }

  onMount(reload);

  async function saveHabit(e: SubmitEvent) {
    e.preventDefault();
    error = '';
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
        await api.habits.update(editingHabitId, body);
        pushToast('Habit updated', 'success');
      } else {
        await api.habits.create(body);
        pushToast('Habit created', 'success');
      }
      resetForm();
      showForm = false;
      await reload();
    } catch (e) {
      error = e instanceof ApiError ? e.message : 'Unexpected error';
    } finally {
      saving = false;
    }
  }

  function edit(habit: Habit) {
    editingHabitId = habit.id;
    name = habit.name;
    description = habit.description ?? '';
    frequency = habit.frequency;
    targetPerPeriod = typeof habit.schedule?.perWeek === 'number'
      ? habit.schedule.perWeek
      : habit.targetPerPeriod;
    targetTime = String(habit.schedule?.targetTime ?? '');
    selectedDays = Array.isArray(habit.schedule?.daysOfWeek)
      ? habit.schedule.daysOfWeek.filter((day): day is number => typeof day === 'number')
      : [];
    error = '';
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
    if (completingHabitIds.includes(id)) return;
    completingHabitIds = [...completingHabitIds, id];
    try {
      await api.habits.complete(id, { date: await userDateString() });
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
    completionHistory = [];
    historyError = '';
    historyLoading = true;
    const request = ++historyRequest;
    try {
      const page = (await api.habits.completions(id)) as Page<ActivityLog>;
      if (request === historyRequest) completionHistory = page.content ?? [];
    } catch (e) {
      if (request === historyRequest) {
        historyError = e instanceof ApiError ? e.message : 'Failed to load completion history.';
      }
    } finally {
      if (request === historyRequest) historyLoading = false;
    }
  }

  function formatCompletionDate(value: string) {
    return new Date(value).toLocaleString('en-IN', { timeZone: 'Asia/Kolkata' });
  }
</script>

<Topbar title="Habits" subtitle="Create routines, set targets, and track your streaks." />

{#if error}<div class="error-banner" role="alert">{error}</div>{/if}

<div class="actions">
  <button
    class="btn btn-primary"
    onclick={() => {
      if (showForm) {
        resetForm();
        error = '';
      }
      showForm = !showForm;
    }}
  >
    {showForm ? 'Cancel' : '+ New habit'}
  </button>
</div>

{#if showForm}
  <form onsubmit={saveHabit} class="card create">
    <h2>{editingHabitId ? 'Edit habit' : 'Create habit'}</h2>
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
        <legend class="label">Active weekdays <span class="field-hint">Unselected days are rest days.</span></legend>
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
{:else if !error}
  <div class="grid list">
    {#each habits as h (h.id)}
      <div class="card habit">
        <div class="info">
          <div class="head"><strong>{h.name}</strong> <span class="badge">{h.frequency}</span>{#if !h.active}<span class="badge">inactive</span>{/if}</div>
          {#if h.description}<p class="muted">{h.description}</p>{/if}
          <div class="streak">
            <hl-progress-ring value={h.streak?.current ?? 0} max={Math.max(h.streak?.longest ?? 0, 7)} label={`${h.streak?.current ?? 0}`}></hl-progress-ring>
            <span class="muted">current {h.streak?.current ?? 0} · best {h.streak?.longest ?? 0}</span>
          </div>
          <div class="schedule-progress" aria-label="Weekly schedule consistency">
            {#if h.active && h.weeklyProgress.expected > 0}
              <span>This week: {h.weeklyProgress.completed}/{h.weeklyProgress.expected} expected · {h.weeklyProgress.consistencyPercent}% consistency</span>
              <progress max="100" value={h.weeklyProgress.consistencyPercent} aria-label={`${h.weeklyProgress.consistencyPercent}% weekly consistency`}></progress>
            {:else if !h.active}
              <span>Paused{h.pausedUntil ? ` until ${h.pausedUntil}` : ''}</span>
            {:else}
              <span>No scheduled days yet this week</span>
            {/if}
          </div>
        </div>
        <div class="ops">
          <button class="btn" onclick={() => toggleHistory(h.id)}>
            {historyHabitId === h.id ? 'Hide history' : 'History'}
          </button>
          <button class="btn" onclick={() => edit(h)}>Edit</button>
          <button class="btn" onclick={() => togglePause(h)} disabled={pausingHabitIds.includes(h.id)} aria-busy={pausingHabitIds.includes(h.id)}>
            {pausingHabitIds.includes(h.id) ? 'Updating…' : h.active ? 'Pause 7 days' : 'Resume'}
          </button>
          <button class="btn btn-primary" onclick={() => complete(h.id)} disabled={completingHabitIds.includes(h.id)} aria-busy={completingHabitIds.includes(h.id)}>
            {completingHabitIds.includes(h.id) ? 'Saving…' : 'Mark done today'}
          </button>
          <button class="btn btn-danger" onclick={() => remove(h.id)} disabled={deletingHabitIds.includes(h.id)} aria-busy={deletingHabitIds.includes(h.id)}>
            {deletingHabitIds.includes(h.id) ? 'Removing…' : 'Delete'}
          </button>
        </div>
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
            {:else}
              <p class="muted">No completions recorded yet.</p>
            {/if}
          </section>
        {/if}
      </div>
    {:else}
      <div class="empty-state">
        <div class="empty-state-copy"><strong>Your habit list is ready</strong><p>Create a habit above to start tracking your progress.</p></div>
      </div>
    {/each}
  </div>
{/if}

<style>
  .actions { margin-bottom: 1rem; }
  .create { max-width: 860px; margin-bottom: 1.1rem; border-top: 3px solid color-mix(in srgb,var(--primary) 62%,transparent); }
  .create h2 { font-size: 1.25rem; }
  .grow { flex: 1; }
  .list { grid-template-columns: 1fr; }
  .habit { display: flex; justify-content: space-between; align-items: center; gap: 1.1rem; flex-wrap: wrap; border-left: 3px solid color-mix(in srgb,var(--primary) 54%,var(--border)); }
  .info { min-width: min(100%,260px); flex: 1; }
  .head { display: flex; align-items: center; flex-wrap: wrap; gap: 0.5rem; }
  .head strong { font-size: 1.08rem; letter-spacing: -.015em; }
  .streak { display: flex; align-items: center; gap: 0.75rem; margin-top: 0.6rem; }
  .schedule-progress { display: grid; gap: .35rem; max-width: 440px; margin-top: .65rem; color: var(--text-muted); font-size: .82rem; }
  .schedule-progress progress { width: 100%; height: 7px; accent-color: var(--primary); }
  .ops { display: flex; flex-wrap: wrap; gap: 0.5rem; }
  .history { flex-basis: 100%; border-top: 1px solid var(--border); padding: .85rem .25rem .1rem; }
  .history ul { display: grid; grid-template-columns: repeat(auto-fit,minmax(180px,1fr)); gap: .4rem; list-style: none; padding: 0; }
  .history li { padding: .45rem .6rem; border-radius: 9px; background: var(--surface-2); color: var(--text-muted); font-size: .82rem; }
  .history h3 { margin: 0 0 .4rem; font-size: 1rem; }
  .history ul { margin: 0; padding-left: 1.2rem; }
  .error-text { color: var(--danger); }
  .weekdays { display: flex; flex-wrap: wrap; gap: 0.65rem; border: 0; padding: 0; margin: 0 0 1rem; }
  .weekdays legend { width: 100%; }
  .weekdays label { display: inline-flex; align-items: center; gap: 0.4rem; min-height: 38px; padding: .4rem .65rem; border: 1px solid var(--border); border-radius: 10px; background: var(--surface); }
  .weekdays label:has(input:checked) { border-color: color-mix(in srgb,var(--primary) 40%,var(--border)); background: color-mix(in srgb,var(--primary) 8%,var(--surface)); }
  @media (max-width: 560px) {
    .ops { width: 100%; }
    .ops .btn { flex: 1 1 calc(50% - .5rem); justify-content: center; }
  }
</style>
