<script lang="ts">
  import Topbar from '$lib/components/Topbar.svelte';
  import LoadingState from '$lib/components/LoadingState.svelte';
  import { onMount } from 'svelte';
  import { api, ApiError } from '$lib/api';
  import { copyToClipboard } from '$lib/clipboard';
  import { pushToast } from '$lib/stores/toast.svelte';
  import type { Connection } from '$lib/types';

  const partners = ['FITBIT', 'GARMIN', 'APPLE_HEALTH', 'GENERIC'];

  let connections = $state<Connection[]>([]);
  let created = $state<Connection | null>(null);
  let partner = $state('FITBIT');
  let error = $state('');
  let actionError = $state('');
  let loading = $state(true);
  let connecting = $state(false);
  let revokingIds = $state<string[]>([]);

  async function load() {
    loading = true;
    error = '';
    try {
      connections = (await api.integrations.list()) as Connection[];
    } catch (e) {
      error = e instanceof ApiError ? e.message : 'Failed to load connections';
    } finally {
      loading = false;
    }
  }

  onMount(() => {
    void load();
  });

  async function connect(e: SubmitEvent) {
    e.preventDefault();
    actionError = '';
    connecting = true;
    try {
      created = (await api.integrations.create({ partner })) as Connection;
      await load();
    } catch (e) {
      actionError = e instanceof ApiError ? e.message : 'Unexpected error';
    } finally {
      connecting = false;
    }
  }

  async function revoke(id: string) {
    if (revokingIds.includes(id)) return;
    if (!window.confirm('Revoke this integration? Incoming events using it will no longer be accepted.')) return;
    revokingIds = [...revokingIds, id];
    try {
      await api.integrations.revoke(id);
      pushToast('Connection revoked', 'info');
      await load();
    } catch (e) {
      pushToast(e instanceof ApiError ? e.message : 'Failed', 'error');
    } finally {
      revokingIds = revokingIds.filter((connectionId) => connectionId !== id);
    }
  }

  async function copyCredential(value: string, label: string) {
    try {
      await copyToClipboard(value);
      pushToast(`${label} copied to clipboard`, 'success');
    } catch (e) {
      pushToast(e instanceof Error ? e.message : `Could not copy ${label.toLowerCase()}.`, 'error');
    }
  }
</script>

<Topbar title="Integrations" subtitle="Connect partners and wearables to sync activity." />
{#if error}
  <div class="error-banner" role="alert">{error} <button class="btn" onclick={load}>Retry</button></div>
{/if}
{#if actionError}<div class="error-banner" role="alert">{actionError}</div>{/if}

{#if created}
  <div class="card secret">
    <h2>Connection created — copy these now (shown once)</h2>
    <div class="kv"><span class="muted">Partner</span><code>{created.partner}</code></div>
    <div class="kv"><span class="muted">API key</span><code>{created.apiKey}</code><button class="btn" type="button" onclick={() => copyCredential(created!.apiKey ?? '', 'API key')}>Copy</button></div>
    <div class="kv"><span class="muted">HMAC secret</span><code>{created.hmacSecret}</code><button class="btn" type="button" onclick={() => copyCredential(created!.hmacSecret ?? '', 'HMAC secret')}>Copy</button></div>
    <div class="kv"><span class="muted">Webhook URL</span><code>{created.webhookUrl}</code><button class="btn" type="button" onclick={() => copyCredential(created!.webhookUrl, 'Webhook URL')}>Copy</button></div>
    <p class="muted">Sign each webhook body with HMAC-SHA256 using the secret, sent as <code>X-HL-Signature</code>.</p>
  </div>
{/if}

<form onsubmit={connect} class="card connect">
  <div class="field"><label class="label" for="partner">Connect a partner / wearable</label>
    <select class="input" id="partner" bind:value={partner}>
      {#each partners as p}<option>{p}</option>{/each}
    </select>
  </div>
  <button class="btn btn-primary" type="submit" disabled={connecting}>{connecting ? 'Connecting…' : 'Connect'}</button>
</form>

{#if loading}
  <LoadingState message="Loading your connections…" />
{:else if !error}
  <div class="grid tiles">
    {#each connections as c (c.connectionId)}
      <div class="card tile">
        <hl-partner-tile
          partner={c.partner}
          status={c.status}
          connection-id={c.connectionId}
          onhlRevoke={(event: CustomEvent<string>) => revoke(event.detail)}
        ></hl-partner-tile>
        <div class="muted">Connected {c.connectedAt ? new Date(c.connectedAt).toLocaleDateString() : ''}</div>
        <div class="muted">Last event {c.lastEventAt ? new Date(c.lastEventAt).toLocaleString() : '—'}</div>
      </div>
    {:else}
      <div class="empty-state">
        <div class="empty-state-copy"><strong>No partners connected</strong><p>Choose a wearable above to start syncing activity.</p></div>
      </div>
    {/each}
  </div>
{/if}

<style>
  .connect { margin-bottom: 1rem; max-width: 420px; }
  .secret { border-color: var(--warning); margin-bottom: 1rem; }
  .secret code { word-break: break-all; }
  .kv { display: flex; align-items: center; gap: 0.75rem; padding: 0.3rem 0; }
  .kv .muted { width: 110px; flex-shrink: 0; }
  .kv code { flex: 1; min-width: 0; }
  .tiles { grid-template-columns: repeat(auto-fit, minmax(240px, 1fr)); }
  .tile { display: grid; gap: 0.5rem; }
</style>
