<script lang="ts">
  import { goto } from '$app/navigation';
  import { api, ApiError } from '$lib/api';
  import { setToken } from '$lib/auth.svelte';

  let email = $state('');
  let password = $state('');
  let tenantCode = $state('');
  let error = $state('');
  let loading = $state(false);

  async function submit(e: SubmitEvent) {
    e.preventDefault();
    error = '';
    loading = true;
    try {
      const res = await api.auth.login({ email, password, tenantCode: tenantCode || undefined });
      setToken(res.accessToken);
      await goto('/dashboard');
    } catch (err) {
      error = err instanceof ApiError ? err.message : 'Unexpected error';
    } finally {
      loading = false;
    }
  }
</script>

<div class="auth">
  <form onsubmit={submit} class="card">
    <h1>Welcome back</h1>
    <p class="muted">Log in to your HabitLoop account.</p>
    {#if error}<div class="error-banner">{error}</div>{/if}
    <div class="field">
      <label class="label" for="email">Email</label>
      <input class="input" id="email" type="email" bind:value={email} required />
    </div>
    <div class="field">
      <label class="label" for="password">Password</label>
      <input class="input" id="password" type="password" bind:value={password} required />
    </div>
    <div class="field">
      <label class="label" for="tenantCode">Tenant code (optional)</label>
      <input class="input" id="tenantCode" bind:value={tenantCode} placeholder="Only if your email is in multiple tenants" />
    </div>
    <button class="btn btn-primary" type="submit" disabled={loading}>{loading ? 'Logging in…' : 'Log in'}</button>
    <p class="muted" style="margin-top:0.9rem">No account? <a href="/register">Create one</a></p>
  </form>
</div>

<style>
  .auth { min-height: 100vh; display: grid; place-items: center; padding: 1rem; }
  form { width: 380px; max-width: 100%; }
</style>
