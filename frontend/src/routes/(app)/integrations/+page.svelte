<script lang="ts">
  import Topbar from '$lib/components/Topbar.svelte';
  import { onMount } from 'svelte';
  import { api, ApiError } from '$lib/api';
  import { pushToast } from '$lib/stores/toast.svelte';
  import type { Connection } from '$lib/types';

  const partners = ['FITBIT', 'GARMIN', 'APPLE_HEALTH', 'GENERIC'];

  let connections = $state<Connection[]>([]);
  let created = $state<Connection | null>(null);
  let partner = $state('FITBIT');
  let error = $state('');

  async function load() {
    try {
      connections = (await api.integrations.list()) as Connection[];
    } catch (e) {
      error = e instanceof ApiError ? e.message : 'Failed to load connections';
    }
  }

  onMount(load);

  async function connect(e: SubmitEvent) {
    e.preventDefault();
    error = '';
    try {
      created = (await api.integrations.create({ partner })) as Connection;
      await load();
    } catch (e) {
      error = e instanceof ApiError ? e.message : 'Unexpected error';
    }
  }

  async function revoke(id: string) {
    try {
      await api.integrations.revoke(id);
      pushToast('Connection revoked', 'info');
      await load();
    } catch (e) {
      pushToast(e instanceof ApiError ? e.message : 'Failed', 'error');
    }
  }
</script>

<Topbar title="Integrations" />
{#if error}<div class="error-banner">{error}</div>{/if}

{#if created}
  <div class="card secret">
    <h2>Connection created — copy these now (shown once)</h2>
    <div class="kv"><span class="muted">Partner</span><code>{created.partner}</code></div>
    <div class="kv"><span class="muted">API key</span><code>{created.apiKey}</code></div>
    <div class="kv"><span class="muted">HMAC secret</span><code>{created.hmacSecret}</code></div>
    <div class="kv"><span class="muted">Webhook URL</span><code>{created.webhookUrl}</code></div>
    <p class="muted">Sign each webhook body with HMAC-SHA256 using the secret, sent as <code>X-HL-Signature</code>.</p>
  </div>
{/if}

<form onsubmit={connect} class="card connect">
  <div class="field"><label class="label" for="partner">Connect a partner / wearable</label>
    <select class="input" id="partner" bind:value={partner}>
      {#each partners as p}<option>{p}</option>{/each}
    </select>
  </div>
  <button class="btn btn-primary" type="submit">Connect</button>
</form>

<div class="grid tiles">
  {#each connections as c (c.connectionId)}
    <div class="card tile">
      <div class="head"><strong>{c.partner}</strong><span class="badge">{c.status}</span></div>
      <div class="muted">Connected {c.connectedAt ? new Date(c.connectedAt).toLocaleDateString() : ''}</div>
      <div class="muted">Last event {c.lastEventAt ? new Date(c.lastEventAt).toLocaleString() : '—'}</div>
      <button class="btn btn-danger" onclick={() => revoke(c.connectionId)}>Revoke</button>
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
  .tile button { margin-top: 0.6rem; }
</style>
