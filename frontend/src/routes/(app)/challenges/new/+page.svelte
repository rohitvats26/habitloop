<script lang="ts">
  import Topbar from '$lib/components/Topbar.svelte';
  import { enhance } from '$app/forms';
  let { form } = $props();
  const today = new Date().toISOString().slice(0, 10);
</script>

<Topbar title="Create challenge" />
{#if form?.error}<div class="error-banner">{form.error}</div>{/if}

<form method="POST" use:enhance class="card">
  <div class="field"><label class="label" for="title">Title</label><input class="input" id="title" name="title" required /></div>
  <div class="field"><label class="label" for="description">Description</label><textarea class="input" id="description" name="description" rows="2"></textarea></div>
  <div class="row">
    <div class="field"><label class="label" for="visibility">Visibility</label>
      <select class="input" id="visibility" name="visibility"><option>PUBLIC</option><option>PRIVATE</option></select>
    </div>
    <div class="field"><label class="label" for="metric">Metric</label><input class="input" id="metric" name="metric" value="DAYS_COMPLETED" /></div>
    <div class="field"><label class="label" for="target">Target</label><input class="input" id="target" name="target" type="number" min="1" value="30" /></div>
  </div>
  <div class="row">
    <div class="field"><label class="label" for="startsOn">Starts</label><input class="input" id="startsOn" name="startsOn" type="date" value={today} /></div>
    <div class="field"><label class="label" for="endsOn">Ends</label><input class="input" id="endsOn" name="endsOn" type="date" /></div>
  </div>
  <button class="btn btn-primary" type="submit">Create</button>
</form>
