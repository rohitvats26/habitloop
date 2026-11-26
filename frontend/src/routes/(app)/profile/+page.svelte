<script lang="ts">
  import Topbar from '$lib/components/Topbar.svelte';
  import LoadingState from '$lib/components/LoadingState.svelte';
  import { onMount } from 'svelte';
  import { api, ApiError } from '$lib/api';
  import { copyToClipboard } from '$lib/clipboard';
  import { pushToast } from '$lib/stores/toast.svelte';
  import { INDIA_TIMEZONE } from '$lib/timezone';
  import type { TenantInfo, UserProfile } from '$lib/types';

  let p = $state<UserProfile | null>(null);
  let tenant = $state<TenantInfo | null>(null);
  let displayName = $state('');
  let avatarUrl = $state('');
  let error = $state('');
  let saved = $state(false);
  let loading = $state(true);
  let saving = $state(false);
  let avatarPreviewFailed = $state(false);

  async function load() {
    loading = true;
    error = '';
    try {
      const [profile, currentTenant] = await Promise.all([api.users.me(), api.tenants.me()]);
      p = profile;
      tenant = currentTenant;
      displayName = profile.displayName ?? '';
      avatarUrl = profile.avatarUrl ?? '';
      avatarPreviewFailed = false;
    } catch (e) {
      error = e instanceof ApiError ? e.message : 'Failed to load profile';
    } finally {
      loading = false;
    }
  }

  onMount(() => {
    void load();
  });

  async function save(e: SubmitEvent) {
    e.preventDefault();
    error = '';
    saved = false;
    saving = true;
    try {
      await api.users.updateMe({ displayName, timezone: INDIA_TIMEZONE, avatarUrl });
      saved = true;
    } catch (e) {
      error = e instanceof ApiError ? e.message : 'Unexpected error';
    } finally {
      saving = false;
    }
  }

  async function copyIdentifier(value: string, label: string) {
    try {
      await copyToClipboard(value);
      pushToast(`${label} copied to clipboard`, 'success');
    } catch (e) {
      pushToast(e instanceof Error ? e.message : `Could not copy ${label.toLowerCase()}.`, 'error');
    }
  }
</script>

<Topbar title="Profile & settings" subtitle="Keep your account details up to date." />
{#if error}<div class="error-banner" role="alert">{error} {#if !p}<button class="btn" onclick={load}>Retry</button>{/if}</div>{/if}
{#if saved}<div class="success-banner" role="status">Your profile settings have been saved.</div>{/if}

{#if loading}
  <LoadingState message="Loading your profile…" />
{:else if p && tenant}
  <form onsubmit={save} class="card profile-form">
    <h2>Personal details</h2>
    <p class="muted">These settings help personalize your HabitLoop experience.</p>
    <div class="field"><label class="label" for="email">Email</label><input class="input" id="email" value={p.email} disabled /></div>
    <div class="field"><label class="label" for="roles">Roles</label>
      <div id="roles">{#each p.roles as r}<span class="badge">{r}</span> {/each}</div>
    </div>
    <div class="field"><label class="label" for="displayName">Display name</label><input class="input" id="displayName" bind:value={displayName} maxlength="80" required /></div>
    <div class="field">
      <span class="label">Timezone</span>
      <span>India Standard Time (Asia/Kolkata)</span>
      <span class="field-hint">Habit dates and streaks use this timezone.</span>
    </div>
    <div class="field">
      <label class="label" for="avatarUrl">Avatar URL</label>
      <input
        class="input"
        id="avatarUrl"
        type="url"
        bind:value={avatarUrl}
        maxlength="512"
        oninput={() => (avatarPreviewFailed = false)}
      />
      {#if avatarUrl && !avatarPreviewFailed}
        <img class="avatar-preview" src={avatarUrl} alt={`${displayName || 'Profile'} avatar preview`} onerror={() => (avatarPreviewFailed = true)} />
      {:else if avatarUrl}
        <span class="field-hint">This image could not be loaded. Check the URL and try again.</span>
      {/if}
    </div>
    <button class="btn btn-primary" type="submit" disabled={saving} aria-busy={saving}>
      {#if saving}<span class="loading-spinner loading-spinner-small" aria-hidden="true"></span> Saving…
      {:else}Save changes{/if}
    </button>
  </form>

  <section class="card account-identifiers" aria-labelledby="identifiers-heading">
    <h2 id="identifiers-heading">Account identifiers</h2>
    <p class="muted">Use your user ID for challenge invitations and share the tenant code to invite someone to your workspace.</p>
    <dl>
      <div class="identifier-row">
        <div><dt>User ID</dt><dd><code>{p.id}</code><button class="btn copy" type="button" onclick={() => copyIdentifier(p!.id, 'User ID')}>Copy</button></dd></div>
      </div>
      <div class="identifier-row">
        <div><dt>Tenant code</dt><dd><code>{tenant.joinCode}</code><button class="btn copy" type="button" onclick={() => copyIdentifier(tenant!.joinCode, 'Tenant code')}>Copy</button></dd></div>
      </div>
      <div class="identifier-row">
        <div><dt>Tenant ID</dt><dd><code>{tenant.id}</code><button class="btn copy" type="button" onclick={() => copyIdentifier(tenant!.id, 'Tenant ID')}>Copy</button></dd></div>
      </div>
    </dl>
  </section>
{/if}

<style>
  .profile-form { max-width: 760px; border-top: 3px solid color-mix(in srgb,var(--primary) 55%,transparent); }
  .profile-form > p { margin: -.1rem 0 1.25rem; }
  .profile-form .btn-primary { margin-top: .25rem; }
  .account-identifiers { max-width: 760px; margin-top: 1rem; }
  .account-identifiers > p { margin: -.1rem 0 1.25rem; }
  .account-identifiers dl { display: grid; grid-template-columns: repeat(auto-fit,minmax(min(100%,290px),1fr)); gap: .65rem; margin: 0; }
  .identifier-row { min-width: 0; padding: .8rem; border: 1px solid var(--border); border-radius: 13px; background: color-mix(in srgb,var(--surface-2) 48%,var(--surface)); }
  .identifier-row dt { color: var(--text-muted); font-size: .75rem; font-weight: 700; letter-spacing: .03em; text-transform: uppercase; }
  .identifier-row dd { display: flex; align-items: center; flex-wrap: wrap; gap: .5rem; margin: .4rem 0 0; overflow-wrap: anywhere; }
  .identifier-row code { min-width: 0; flex: 1; padding: .4rem .5rem; border-radius: 8px; background: var(--surface); color: var(--text); font-size: .78rem; }
  .copy { min-height: 36px; padding: .4rem .65rem; font-size: .78rem; }
  .avatar-preview { display: block; width: 76px; height: 76px; margin-top: .6rem; border-radius: 50%; object-fit: cover; border: 3px solid color-mix(in srgb,var(--primary) 25%,var(--surface)); box-shadow: 0 4px 14px color-mix(in srgb,var(--primary) 12%,transparent); }
</style>
