<script lang="ts">
  import Topbar from '$lib/components/Topbar.svelte';
  import ThemeToggle from '$lib/components/ThemeToggle.svelte';
  import LoadingState from '$lib/components/LoadingState.svelte';
  import { onMount } from 'svelte';
  import { api, ApiError } from '$lib/api';
  import { copyToClipboard } from '$lib/clipboard';
  import { pushToast } from '$lib/stores/toast.svelte';
  import { INDIA_TIMEZONE } from '$lib/timezone';
  import { guardUnsavedChanges } from '$lib/unsaved';
  import type { TenantInfo, UserProfile } from '$lib/types';

  let p = $state<UserProfile | null>(null);
  let tenant = $state<TenantInfo | null>(null);
  let displayName = $state('');
  let avatarUrl = $state('');
  let loadError = $state('');
  let saveError = $state('');
  let saveDetails = $state<string[]>([]);
  let saved = $state(false);
  let loading = $state(true);
  let saving = $state(false);
  let avatarPreviewFailed = $state(false);

  guardUnsavedChanges(() => p !== null && !saving
    && (displayName !== (p.displayName ?? '') || avatarUrl !== (p.avatarUrl ?? '')));

  async function load() {
    loading = true;
    loadError = '';
    try {
      const [profile, currentTenant] = await Promise.all([api.users.me(), api.tenants.me()]);
      p = profile;
      tenant = currentTenant;
      displayName = profile.displayName ?? '';
      avatarUrl = profile.avatarUrl ?? '';
      avatarPreviewFailed = false;
    } catch (e) {
      loadError = e instanceof ApiError ? e.message : 'Failed to load profile';
    } finally {
      loading = false;
    }
  }

  onMount(() => {
    void load();
  });

  async function save(e: SubmitEvent) {
    e.preventDefault();
    saveError = '';
    saveDetails = [];
    saved = false;
    if (!displayName.trim()) {
      saveError = 'Enter a display name.';
      return;
    }
    saving = true;
    try {
      const updated = await api.users.updateMe({ displayName: displayName.trim(), timezone: INDIA_TIMEZONE, avatarUrl });
      p = updated;
      displayName = updated.displayName ?? '';
      avatarUrl = updated.avatarUrl ?? '';
      saved = true;
    } catch (e) {
      saveError = e instanceof ApiError ? e.message : 'Unexpected error';
      saveDetails = e instanceof ApiError ? e.details : [];
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
{#if loadError}<div class="error-banner" role="alert">Could not load your profile: {loadError} <button class="btn" onclick={load}>Retry</button></div>{/if}
{#if saved}<div class="success-banner" role="status">Your profile settings have been saved.</div>{/if}

{#if loading}
  <LoadingState message="Loading your profile…" />
{:else if p && tenant}
  <section class="card profile-hero accent-top">
    {#if p.avatarUrl && !avatarPreviewFailed}
      <img class="hero-avatar" src={p.avatarUrl} alt="" onerror={() => (avatarPreviewFailed = true)} />
    {:else}
      <span class="hero-avatar initials" aria-hidden="true">{(p.displayName || p.email).trim().charAt(0).toUpperCase()}</span>
    {/if}
    <div class="hero-copy">
      <h2>{p.displayName || 'Set your display name'}</h2>
      <p class="muted">{p.email} · {tenant.name}</p>
      <div class="roles">{#if p.roles.includes('TENANT_ADMIN')}<span class="pill tone-violet">Workspace admin</span>{:else}<span class="pill tone-primary">Member</span>{/if}</div>
    </div>
  </section>

  <div class="profile-grid">
  <form onsubmit={save} class="card profile-form">
    <h2>Personal details</h2>
    <p class="muted">These settings help personalize your HabitLoop experience.</p>
    {#if saveError}
      <div class="error-banner" role="alert">
        Your changes were not saved: {saveError}
        {#if saveDetails.length}<ul>{#each saveDetails as detail}<li>{detail}</li>{/each}</ul>{/if}
      </div>
    {/if}
    <div class="field"><label class="label" for="email">Email</label><input class="input" id="email" value={p.email} disabled /></div>
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

  <div class="side-stack">
  <section class="card" aria-labelledby="appearance-heading">
    <h2 id="appearance-heading">Appearance</h2>
    <p class="muted">Choose light or dark, or follow your device setting. Saved on this device.</p>
    <ThemeToggle variant="surface" />
  </section>

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
  </div>
  </div>
{/if}

<style>
  .profile-hero { display: flex; align-items: center; gap: 1.25rem; margin-bottom: 1.25rem; padding: 1.6rem; }
  .hero-avatar { width: 84px; height: 84px; flex: none; border-radius: 50%; object-fit: cover; box-shadow: 0 0 0 4px var(--surface), 0 0 0 6px color-mix(in srgb, var(--primary) 40%, transparent); }
  .hero-avatar.initials { display: grid; place-items: center; background: var(--grad-violet); color: #fff; font-size: 2rem; font-weight: 800; }
  .hero-copy h2 { margin: 0; font-size: 1.45rem; }
  .hero-copy p { margin: .15rem 0 .6rem; overflow-wrap: anywhere; }
  .roles { display: flex; flex-wrap: wrap; gap: .4rem; }
  .profile-grid { display: grid; grid-template-columns: minmax(0, 1.25fr) minmax(0, 1fr); gap: 1.25rem; align-items: start; }
  .side-stack { display: grid; gap: 1.25rem; }
  .side-stack .card > p, .profile-form > p { margin: -.1rem 0 1.1rem; }
  .profile-form .btn-primary { margin-top: .25rem; }
  .account-identifiers dl { display: grid; gap: .65rem; margin: 0; }
  @media (max-width: 960px) { .profile-grid { grid-template-columns: 1fr; } }
  @media (max-width: 520px) { .profile-hero { flex-direction: column; align-items: flex-start; } }
  .identifier-row { min-width: 0; padding: .8rem; border: 1px solid var(--border); border-radius: 13px; background: color-mix(in srgb,var(--surface-2) 48%,var(--surface)); }
  .identifier-row dt { color: var(--text-muted); font-size: .75rem; font-weight: 700; letter-spacing: .03em; text-transform: uppercase; }
  .identifier-row dd { display: flex; align-items: center; flex-wrap: wrap; gap: .5rem; margin: .4rem 0 0; overflow-wrap: anywhere; }
  .identifier-row code { min-width: 0; flex: 1; padding: .4rem .5rem; border-radius: 8px; background: var(--surface); color: var(--text); font-size: .78rem; }
  .copy { min-height: 36px; padding: .4rem .65rem; font-size: .78rem; }
  .avatar-preview { display: block; width: 76px; height: 76px; margin-top: .6rem; border-radius: 50%; object-fit: cover; border: 3px solid color-mix(in srgb,var(--primary) 25%,var(--surface)); box-shadow: 0 4px 14px color-mix(in srgb,var(--primary) 12%,transparent); }
</style>
