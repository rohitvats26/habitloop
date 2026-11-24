<script lang="ts">
  import Topbar from '$lib/components/Topbar.svelte';
  import { onMount } from 'svelte';
  import { api, ApiError } from '$lib/api';

  interface UserResponse {
    id: string;
    tenantId: string;
    email: string;
    displayName?: string;
    timezone?: string;
    avatarUrl?: string;
    roles: string[];
  }

  let p = $state<UserResponse | null>(null);
  let displayName = $state('');
  let timezone = $state('UTC');
  let avatarUrl = $state('');
  let error = $state('');
  let saved = $state(false);

  onMount(async () => {
    try {
      p = (await api.users.me()) as UserResponse;
      displayName = p.displayName ?? '';
      timezone = p.timezone ?? 'UTC';
      avatarUrl = p.avatarUrl ?? '';
    } catch (e) {
      error = e instanceof ApiError ? e.message : 'Failed to load profile';
    }
  });

  async function save(e: SubmitEvent) {
    e.preventDefault();
    error = '';
    saved = false;
    try {
      await api.users.updateMe({ displayName, timezone, avatarUrl });
      saved = true;
    } catch (e) {
      error = e instanceof ApiError ? e.message : 'Unexpected error';
    }
  }
</script>

<Topbar title="Profile & settings" />
{#if error}<div class="error-banner">{error}</div>{/if}
{#if saved}<div class="card ok">Profile saved.</div>{/if}

{#if p}
  <form onsubmit={save} class="card">
    <div class="field"><label class="label" for="email">Email</label><input class="input" id="email" value={p.email} disabled /></div>
    <div class="field"><label class="label" for="roles">Roles</label>
      <div id="roles">{#each p.roles as r}<span class="badge">{r}</span> {/each}</div>
    </div>
    <div class="field"><label class="label" for="displayName">Display name</label><input class="input" id="displayName" bind:value={displayName} /></div>
    <div class="field"><label class="label" for="timezone">Timezone</label><input class="input" id="timezone" bind:value={timezone} /></div>
    <div class="field"><label class="label" for="avatarUrl">Avatar URL</label><input class="input" id="avatarUrl" bind:value={avatarUrl} /></div>
    <button class="btn btn-primary" type="submit">Save</button>
  </form>

  <p class="muted" style="margin-top:0.75rem">Tenant: <code>{p.tenantId}</code></p>
{/if}

<style>.ok { border-color: var(--success); color: var(--success); margin-bottom: 1rem; }</style>
