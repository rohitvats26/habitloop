<script lang="ts">
  import { enhance } from '$app/forms';
  let { form } = $props();
  let mode = $state<'create' | 'join'>('create');
</script>

<div class="auth">
  <form method="POST" use:enhance class="card">
    <h1>Create your account</h1>
    {#if form?.error}<div class="error-banner">{form.error}</div>{/if}

    <div class="toggle">
      <button type="button" class="btn" class:btn-primary={mode === 'create'} onclick={() => (mode = 'create')}>
        New organization
      </button>
      <button type="button" class="btn" class:btn-primary={mode === 'join'} onclick={() => (mode = 'join')}>
        Join with code
      </button>
    </div>
    <input type="hidden" name="mode" value={mode} />

    <div class="field">
      <label class="label" for="displayName">Your name</label>
      <input class="input" id="displayName" name="displayName" value={form?.displayName ?? ''} required />
    </div>
    <div class="field">
      <label class="label" for="email">Email</label>
      <input class="input" id="email" name="email" type="email" value={form?.email ?? ''} required />
    </div>
    <div class="field">
      <label class="label" for="password">Password</label>
      <input class="input" id="password" name="password" type="password" minlength="8" required />
    </div>
    <div class="field">
      <label class="label" for="timezone">Timezone</label>
      <input class="input" id="timezone" name="timezone" value="UTC" />
    </div>

    {#if mode === 'create'}
      <div class="field">
        <label class="label" for="tenantName">Organization name</label>
        <input class="input" id="tenantName" name="tenantName" placeholder="Acme Wellness" required />
      </div>
    {:else}
      <div class="field">
        <label class="label" for="joinCode">Tenant join code</label>
        <input class="input" id="joinCode" name="joinCode" placeholder="ACME2026" required />
      </div>
    {/if}

    <button class="btn btn-primary" type="submit">Create account</button>
    <p class="muted" style="margin-top:0.9rem">Already registered? <a href="/login">Log in</a></p>
  </form>
</div>

<style>
  .auth { min-height: 100vh; display: grid; place-items: center; padding: 1rem; }
  form { width: 420px; max-width: 100%; }
  .toggle { display: flex; gap: 0.5rem; margin-bottom: 1rem; }
  .toggle .btn { flex: 1; }
</style>
