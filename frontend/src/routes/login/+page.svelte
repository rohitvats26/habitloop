<script lang="ts">
  import { enhance } from '$app/forms';
  let { form } = $props();
</script>

<div class="auth">
  <form method="POST" use:enhance class="card">
    <h1>Welcome back</h1>
    <p class="muted">Log in to your HabitLoop account.</p>
    {#if form?.error}<div class="error-banner">{form.error}</div>{/if}
    <div class="field">
      <label class="label" for="email">Email</label>
      <input class="input" id="email" name="email" type="email" value={form?.email ?? ''} required />
    </div>
    <div class="field">
      <label class="label" for="password">Password</label>
      <input class="input" id="password" name="password" type="password" required />
    </div>
    <div class="field">
      <label class="label" for="tenantCode">Tenant code (optional)</label>
      <input class="input" id="tenantCode" name="tenantCode" placeholder="Only if your email is in multiple tenants" />
    </div>
    <button class="btn btn-primary" type="submit">Log in</button>
    <p class="muted" style="margin-top:0.9rem">No account? <a href="/register">Create one</a></p>
  </form>
</div>

<style>
  .auth { min-height: 100vh; display: grid; place-items: center; padding: 1rem; }
  form { width: 380px; max-width: 100%; }
</style>
