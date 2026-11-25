<script lang="ts">
  import { goto } from '$app/navigation';
  import { api, ApiError } from '$lib/api';
  import { setToken } from '$lib/auth.svelte';
  import { INDIA_TIMEZONE } from '$lib/timezone';

  let mode = $state<'create' | 'join'>('create');
  let displayName = $state('');
  let email = $state('');
  let password = $state('');
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
      timezone: INDIA_TIMEZONE,
      tenantName: mode === 'create' ? tenantName : undefined,
      joinCode: mode === 'join' ? joinCode : undefined
    };
    try {
      await api.auth.register(payload);
      const res = await api.auth.login({ email, password });
      setToken(res.accessToken);
      await goto('/onboarding');
    } catch (err) {
      error = err instanceof ApiError ? err.message : "We couldn't create your account. Please try again.";
    } finally {
      loading = false;
    }
  }
</script>

<main class="auth-page">
  <div class="auth-layout">
    <section class="auth-story">
      <div class="auth-brand"><span class="brand-mark">H</span>HabitLoop</div>
      <div class="auth-story-copy">
        <span class="eyebrow">Start where you are</span>
        <h2>Small steps can change your day.</h2>
        <p>Create a space for your routines, find your rhythm, and make progress feel achievable.</p>
        <div class="momentum-card" aria-hidden="true">
          <div class="momentum-head"><strong>Consistency over perfection</strong><span>One day at a time</span></div>
          <div class="momentum-bars">
            <span style="height: 38%"></span><span style="height: 64%"></span><span style="height: 48%"></span>
            <span style="height: 80%"></span><span style="height: 56%"></span><span style="height: 92%"></span>
            <span style="height: 72%"></span>
          </div>
          <div class="momentum-days"><span>M</span><span>T</span><span>W</span><span>T</span><span>F</span><span>S</span><span>S</span></div>
        </div>
      </div>
      <div class="auth-story-foot">A healthier routine starts with one small choice.</div>
    </section>

    <section class="auth-panel">
      <form onsubmit={submit} class="card auth-card">
        <span class="eyebrow">Get started</span>
        <h1>Create your account</h1>
        <p class="muted">A few details, then you're on your way.</p>
        {#if error}<div class="error-banner" role="alert">{error}</div>{/if}

        <div class="toggle">
          <button type="button" class="btn" class:btn-primary={mode === 'create'} aria-pressed={mode === 'create'} onclick={() => (mode = 'create')}>
            New organization
          </button>
          <button type="button" class="btn" class:btn-primary={mode === 'join'} aria-pressed={mode === 'join'} onclick={() => (mode = 'join')}>
            Join with code
          </button>
        </div>

        <div class="field">
          <label class="label" for="displayName">Your name</label>
          <input class="input" id="displayName" autocomplete="name" bind:value={displayName} maxlength="80" required />
        </div>
        <div class="field">
          <label class="label" for="email">Email</label>
          <input class="input" id="email" type="email" autocomplete="email" bind:value={email} maxlength="255" required />
        </div>
        <div class="field">
          <label class="label" for="password">Password</label>
          <input class="input" id="password" type="password" autocomplete="new-password" minlength="8" maxlength="72" bind:value={password} required />
        </div>
        {#if mode === 'create'}
          <div class="field">
            <label class="label" for="tenantName">Organization name</label>
            <input class="input" id="tenantName" bind:value={tenantName} maxlength="120" placeholder="Acme Wellness" required />
          </div>
        {:else}
          <div class="field">
            <label class="label" for="joinCode">Tenant join code</label>
            <input class="input" id="joinCode" bind:value={joinCode} maxlength="16" placeholder="ACME2026" required />
          </div>
        {/if}

        <button class="btn btn-primary" type="submit" disabled={loading} aria-busy={loading}>
          {#if loading}<span class="loading-spinner loading-spinner-small" aria-hidden="true"></span> Creating account…{:else}Create account{/if}
        </button>
        <p class="muted auth-switch">Already registered? <a href="/login">Log in</a></p>
      </form>
    </section>
  </div>
</main>

<style>
  .toggle { display: flex; gap: 0.5rem; margin-bottom: 1.25rem; }
  .toggle .btn { flex: 1; justify-content: center; }
  @media (max-width: 820px) {
    .auth-panel { align-items: start; }
    .auth-card { padding-top: 0.25rem; }
  }
</style>
