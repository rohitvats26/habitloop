<script lang="ts">
  import { goto } from '$app/navigation';
  import { api, ApiError } from '$lib/api';
  import { setToken } from '$lib/auth.svelte';

  let { data } = $props();

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
      setToken(res.accessToken, res.refreshToken);
      await goto('/dashboard');
    } catch (err) {
      error = err instanceof ApiError ? err.message : "We couldn't sign you in. Please try again.";
    } finally {
      loading = false;
    }
  }
</script>

<svelte:head><title>Log in · HabitLoop</title></svelte:head>

<main class="auth-page">
  <div class="auth-layout">
    <section class="auth-story">
      <div class="auth-brand"><span class="brand-mark">H</span>HabitLoop</div>
      <div class="auth-story-copy">
        <span class="eyebrow">A little progress, every day</span>
        <h2>Make room for better habits.</h2>
        <p>Build a routine that fits your life, celebrate your momentum, and keep moving forward.</p>
        <div class="momentum-card" aria-hidden="true">
          <div class="momentum-head"><strong>Your week, at a glance</strong><span>Mon — Sun</span></div>
          <div class="momentum-bars">
            <span style="height: 32%"></span><span style="height: 52%"></span><span style="height: 44%"></span>
            <span style="height: 76%"></span><span style="height: 60%"></span><span style="height: 88%"></span>
            <span style="height: 70%"></span>
          </div>
          <div class="momentum-days"><span>M</span><span>T</span><span>W</span><span>T</span><span>F</span><span>S</span><span>S</span></div>
        </div>
      </div>
      <div class="auth-story-foot">Progress is personal. Keep showing up for yourself.</div>
    </section>

    <section class="auth-panel">
      <form onsubmit={submit} class="card auth-card">
        <span class="eyebrow">Your journey continues</span>
        <h1>Welcome back</h1>
        <p class="muted">Log in to pick up where you left off.</p>
        {#if error}<div class="error-banner" role="alert">{error}</div>
        {:else if data.registered}<div class="success-banner" role="status">Your account was created. Log in to continue.</div>
        {:else if data.expired}<div class="error-banner" role="status">Your session has ended. Please log in again.</div>{/if}
        <div class="field">
          <label class="label" for="email">Email</label>
          <input class="input" id="email" type="email" autocomplete="username" bind:value={email} maxlength="255" required />
        </div>
        <div class="field">
          <label class="label" for="password">Password</label>
          <input class="input" id="password" type="password" autocomplete="current-password" bind:value={password} maxlength="72" required />
        </div>
        <div class="field">
          <label class="label" for="tenantCode">Tenant code <span class="muted">(optional)</span></label>
          <input class="input" id="tenantCode" bind:value={tenantCode} maxlength="16" placeholder="Only needed for multiple workspaces" />
        </div>
        <button class="btn btn-primary" type="submit" disabled={loading} aria-busy={loading}>
          {#if loading}<span class="loading-spinner loading-spinner-small" aria-hidden="true"></span> Signing in…{:else}Log in{/if}
        </button>
        <p class="muted auth-switch">New to HabitLoop? <a href="/register">Create an account</a></p>
      </form>
    </section>
  </div>
</main>
