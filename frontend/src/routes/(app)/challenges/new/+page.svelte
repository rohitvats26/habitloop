<script lang="ts">
  import Topbar from '$lib/components/Topbar.svelte';
  import { goto } from '$app/navigation';
  import { onMount } from 'svelte';
  import { api, ApiError } from '$lib/api';
  import { challengeDateError } from '$lib/challenges';
  import { userDateString } from '$lib/date';
  import { guardUnsavedChanges } from '$lib/unsaved';

  let title = $state('');
  let description = $state('');
  let visibility = $state('PUBLIC');
  const metric = 'DAYS_COMPLETED';
  let target = $state(30);
  let startsOn = $state('');
  let endsOn = $state('');
  let today = $state('');
  let error = $state('');
  let details = $state<string[]>([]);
  let loading = $state(false);
  let dateLoading = $state(true);
  let created = false;

  guardUnsavedChanges(() => !created && (
    title.trim() !== '' || description.trim() !== '' || endsOn !== '' || Number(target) !== 30
    || visibility !== 'PUBLIC' || (today !== '' && startsOn !== today)
  ));

  onMount(() => {
    void userDateString()
      .then((date) => {
        today = date;
        startsOn = date;
      })
      .catch((reason: unknown) => {
        error = reason instanceof Error ? reason.message : 'Unable to prepare dates using India Standard Time.';
      })
      .finally(() => {
        dateLoading = false;
      });
  });

  async function submit(e: SubmitEvent) {
    e.preventDefault();
    error = '';
    details = [];
    if (!title.trim()) {
      error = 'Enter a challenge title.';
      return;
    }
    const dateProblem = challengeDateError(startsOn, endsOn, today);
    if (dateProblem) {
      error = dateProblem;
      return;
    }
    loading = true;
    try {
      const challenge = await api.challenges.create({
        title: title.trim(),
        description,
        visibility,
        metric,
        target: Number(target),
        startsOn,
        endsOn
      });
      created = true;
      await goto(`/challenges/${challenge.id}`);
    } catch (e) {
      error = e instanceof ApiError ? e.message : 'Unexpected error';
      details = e instanceof ApiError ? e.details : [];
    } finally {
      loading = false;
    }
  }
</script>

<Topbar title="Create challenge" subtitle="Set a goal and invite your community to join." />
{#if error}
  <div class="error-banner" role="alert">
    {error}
    {#if details.length}<ul>{#each details as detail}<li>{detail}</li>{/each}</ul>{/if}
  </div>
{/if}

<form onsubmit={submit} class="card challenge-form">
  <h2>Challenge details</h2>
  <p class="muted">Choose a clear goal and a timeframe that helps everyone stay on track.</p>
  <div class="field"><label class="label" for="title">Title</label><input class="input" id="title" bind:value={title} maxlength="120" required placeholder="e.g. 30-day movement streak" /></div>
  <div class="field"><label class="label" for="description">Description <span class="muted">(optional)</span></label><textarea class="input" id="description" bind:value={description} maxlength="1000" rows="3" placeholder="What are you working toward together?"></textarea></div>
  <div class="row">
    <div class="field"><label class="label" for="visibility">Visibility</label>
      <select class="input" id="visibility" bind:value={visibility}><option>PUBLIC</option><option>PRIVATE</option></select>
    </div>
    <div class="field"><label class="label" for="target">Target activity count</label><input class="input" id="target" type="number" min="1" max="9999999999" step="1" bind:value={target} required />
      <span class="field-hint">Each recorded activity counts once; multiple activities can count on the same day.</span>
    </div>
  </div>
  <p class="field-hint">The target is how many activities each participant aims to record. The end date is the last day activities count, even if the target has not been reached. Both dates are inclusive.</p>
  <div class="row">
    <div class="field"><label class="label" for="startsOn">Starts</label><input class="input" id="startsOn" type="date" bind:value={startsOn} required /></div>
    <div class="field"><label class="label" for="endsOn">Ends</label><input class="input" id="endsOn" type="date" bind:value={endsOn} min={startsOn > today ? startsOn : today} required /></div>
  </div>
  <p class="date-note muted" role="status">
    {#if dateLoading}<span class="loading-spinner loading-spinner-small" aria-hidden="true"></span> Preparing dates…
    {:else}Dates use India Standard Time (IST).{/if}
  </p>
  <button class="btn btn-primary" type="submit" disabled={loading || dateLoading || !startsOn} aria-busy={loading}>
    {#if loading}<span class="loading-spinner loading-spinner-small" aria-hidden="true"></span> Creating…
    {:else}Create challenge{/if}
  </button>
</form>

<style>
  .challenge-form { max-width: 860px; border-top: 3px solid color-mix(in srgb,var(--primary) 55%,transparent); }
  .challenge-form > p:first-of-type { margin: -.1rem 0 1.25rem; }
  .challenge-form .row > .field { flex: 1; min-width: 180px; }
  .date-note { display: flex; align-items: center; gap: .55rem; margin: .25rem 0 1rem; font-size: .82rem; }
  .challenge-form h2 { font-size: 1.25rem; }
  .challenge-form textarea { min-height: 100px; }
  @media (max-width: 560px) { .challenge-form .btn { width: 100%; justify-content: center; } }
</style>
