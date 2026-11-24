<script lang="ts">
  import Topbar from '$lib/components/Topbar.svelte';
  import { enhance } from '$app/forms';
  let { data, form } = $props();
  const p = data.profile;
</script>

<Topbar title="Profile & settings" />
{#if form?.error}<div class="error-banner">{form.error}</div>{/if}
{#if form?.saved}<div class="card ok">Profile saved.</div>{/if}

<form method="POST" use:enhance class="card">
  <div class="field"><label class="label">Email</label><input class="input" value={p.email} disabled /></div>
  <div class="field"><label class="label">Roles</label>
    <div>{#each p.roles as r}<span class="badge">{r}</span> {/each}</div>
  </div>
  <div class="field"><label class="label" for="displayName">Display name</label><input class="input" id="displayName" name="displayName" value={p.displayName ?? ''} /></div>
  <div class="field"><label class="label" for="timezone">Timezone</label><input class="input" id="timezone" name="timezone" value={p.timezone ?? 'UTC'} /></div>
  <div class="field"><label class="label" for="avatarUrl">Avatar URL</label><input class="input" id="avatarUrl" name="avatarUrl" value={p.avatarUrl ?? ''} /></div>
  <button class="btn btn-primary" type="submit">Save</button>
</form>

<p class="muted" style="margin-top:0.75rem">Tenant: <code>{p.tenantId}</code></p>

<style>.ok { border-color: var(--success); color: var(--success); margin-bottom: 1rem; }</style>
