<script lang="ts">
  import { goto } from '$app/navigation';
  import { api, ApiError } from '$lib/api';
  import { setToken } from '$lib/auth.svelte';

  let mode = $state<'create' | 'join'>('create');
  let displayName = $state('');
  let email = $state('');
  let password = $state('');
  let timezone = $state('UTC');
  let tenantName = $state('');
  let joinCode = $state('');
  let error = $state('');
  let loading = $state(false);

  async function submit(e: SubmitEvent) {
    e.preventDefault();
    error = '';
    loading = true;
    const payload = {
      email,
      password,
      displayName,
      timezone,
      tenantName: mode === 'create' ? tenantName : undefined,
      joinCode: mode === 'join' ? joinCode : undefined
    };
    try {
      await api.auth.register(payload);
      const res = await api.auth.login({ email, password });
      setToken(res.accessToken);
      await goto('/onboarding');
    } catch (err) {
      error = err instanceof ApiError ? err.message : 'Unexpected error';
    } finally {
      loading = false;
    }
  }
</script>

<div class="auth">
  <form onsubmit={submit} class="card">
    <h1>Create your account</h1>
    {#if error}<div class="error-banner">{error}</div>{/if}

    <div class="toggle">
      <button type="button" class="btn" class:btn-primary={mode === 'create'} onclick={() => (mode = 'create')}>
        New organization
      </button>
      <button type="button" class="btn" class:btn-primary={mode === 'join'} onclick={() => (mode = 'join')}>
        Join with code
      </button>
    </div>

    <div class="field">
      <label class="label" for="displayName">Your name</label>
      <input class="input" id="displayName" bind:value={displayName} required />
    </div>
    <div class="field">
      <label class="label" for="email">Email</label>
      <input class="input" id="email" type="email" bind:value={email} required />
    </div>
    <div class="field">
      <label class="label" for="password">Password</label>
      <input class="input" id="password" type="password" minlength="8" bind:value={password} required />
    </div>
    <div class="field">
      <label class="label" for="timezone">Timezone</label>
      <input class="input" id="timezone" bind:value={timezone} />
    </div>

    {#if mode === 'create'}
      <div class="field">
        <label class="label" for="tenantName">Organization name</label>
        <input class="input" id="tenantName" bind:value={tenantName} placeholder="Acme Wellness" required />
      </div>
    {:else}
      <div class="field">
        <label class="label" for="joinCode">Tenant join code</label>
        <input class="input" id="joinCode" bind:value={joinCode} placeholder="ACME2026" required />
      </div>
    {/if}

    <button class="btn btn-primary" type="submit" disabled={loading}>{loading ? 'Creating…' : 'Create account'}</button>
    <p class="muted" style="margin-top:0.9rem">Already registered? <a href="/login">Log in</a></p>
  </form>
</div>

<style>
  .auth { min-height: 100vh; display: grid; place-items: center; padding: 1rem; }
  form { width: 420px; max-width: 100%; }
  .toggle { display: flex; gap: 0.5rem; margin-bottom: 1rem; }
  .toggle .btn { flex: 1; }
</style>
