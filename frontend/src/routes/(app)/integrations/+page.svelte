<script lang="ts">
  import Topbar from '$lib/components/Topbar.svelte';
  import { enhance } from '$app/forms';
  let { data, form } = $props();
  const partners = ['FITBIT', 'GARMIN', 'APPLE_HEALTH', 'GENERIC'];
</script>

<Topbar title="Integrations" />
{#if form?.error}<div class="error-banner">{form.error}</div>{/if}

{#if form?.created}
  <div class="card secret">
    <h2>Connection created — copy these now (shown once)</h2>
    <div class="kv"><span class="muted">Partner</span><code>{form.created.partner}</code></div>
    <div class="kv"><span class="muted">API key</span><code>{form.created.apiKey}</code></div>
    <div class="kv"><span class="muted">HMAC secret</span><code>{form.created.hmacSecret}</code></div>
    <div class="kv"><span class="muted">Webhook URL</span><code>{form.created.webhookUrl}</code></div>
    <p class="muted">Sign each webhook body with HMAC-SHA256 using the secret, sent as <code>X-HL-Signature</code>.</p>
  </div>
{/if}

<form method="POST" action="?/connect" use:enhance class="card connect">
  <div class="field"><label class="label" for="partner">Connect a partner / wearable</label>
    <select class="input" id="partner" name="partner">
      {#each partners as p}<option>{p}</option>{/each}
    </select>
  </div>
  <button class="btn btn-primary" type="submit">Connect</button>
</form>

<div class="grid tiles">
  {#each data.connections as c (c.connectionId)}
    <div class="card tile">
      <div class="head"><strong>{c.partner}</strong><span class="badge">{c.status}</span></div>
      <div class="muted">Connected {c.connectedAt ? new Date(c.connectedAt).toLocaleDateString() : ''}</div>
      <div class="muted">Last event {c.lastEventAt ? new Date(c.lastEventAt).toLocaleString() : '—'}</div>
      <form method="POST" action="?/revoke" use:enhance>
        <input type="hidden" name="id" value={c.connectionId} />
        <button class="btn btn-danger" type="submit">Revoke</button>
      </form>
    </div>
  {:else}
    <p class="muted">No connections yet.</p>
  {/each}
</div>

<style>
  .connect { margin-bottom: 1rem; max-width: 420px; }
  .secret { border-color: var(--warning); margin-bottom: 1rem; }
  .secret code { word-break: break-all; }
  .kv { display: flex; gap: 0.75rem; padding: 0.3rem 0; }
  .kv .muted { width: 110px; flex-shrink: 0; }
  .tiles { grid-template-columns: repeat(auto-fit, minmax(240px, 1fr)); }
  .head { display: flex; justify-content: space-between; align-items: center; }
  .tile form { margin-top: 0.6rem; }
</style>
