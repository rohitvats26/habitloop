<script lang="ts">
  import Topbar from '$lib/components/Topbar.svelte';
  import LoadingState from '$lib/components/LoadingState.svelte';
  import { onMount } from 'svelte';
  import { api, ApiError } from '$lib/api';
  import { copyToClipboard } from '$lib/clipboard';
  import { formatIstDate, formatIstDateTime } from '$lib/date';
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
      connections = await api.integrations.list();
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
      created = await api.integrations.create({ partner });
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
  <div class="card secret accent-top" style="--stripe: var(--grad-warm)" role="region" aria-label="New connection credentials">
    <h2>Connection created — copy these now (shown once)</h2>
    <div class="kv"><span class="muted">Partner</span><code>{created.partner}</code></div>
    <div class="kv"><span class="muted">API key</span><code>{created.apiKey}</code><button class="btn" type="button" onclick={() => copyCredential(created!.apiKey ?? '', 'API key')}>Copy</button></div>
    <div class="kv"><span class="muted">HMAC secret</span><code>{created.hmacSecret}</code><button class="btn" type="button" onclick={() => copyCredential(created!.hmacSecret ?? '', 'HMAC secret')}>Copy</button></div>
    <div class="kv"><span class="muted">Webhook URL</span><code>{created.webhookUrl}</code><button class="btn" type="button" onclick={() => copyCredential(created!.webhookUrl, 'Webhook URL')}>Copy</button></div>
    <p class="muted">Sign each webhook body with HMAC-SHA256 using the secret, sent as <code>X-HL-Signature</code>.</p>
    <button class="btn btn-primary" type="button" onclick={() => (created = null)}>I've saved these — hide credentials</button>
  </div>
{/if}

<form onsubmit={connect} class="card connect accent-top" aria-labelledby="connect-heading">
  <div class="connect-copy">
    <span class="icon-chip" aria-hidden="true"><svg viewBox="0 0 24 24"><path d="M9 15 7.5 16.5a4 4 0 0 1-5.7-5.7l4-4a4 4 0 0 1 5.7 0M15 9l1.5-1.5a4 4 0 0 1 5.7 5.7l-4 4a4 4 0 0 1-5.7 0M8 12h8" /></svg></span>
    <div>
      <h2 id="connect-heading">Connect a partner or wearable</h2>
      <p class="muted">Activity syncs automatically through a signed webhook. You'll get credentials once.</p>
    </div>
  </div>
  <div class="connect-row">
    <label class="sr-only" for="partner">Partner</label>
    <select class="input" id="partner" bind:value={partner}>
      {#each partners as p}<option value={p}>{p.replace('_', ' ')}</option>{/each}
    </select>
    <button class="btn btn-primary" type="submit" disabled={connecting}>{connecting ? 'Connecting…' : 'Connect'}</button>
  </div>
</form>

{#if loading}
  <LoadingState message="Loading your connections…" />
{:else if !error}
  <div class="grid tiles">
    {#each connections as c (c.connectionId)}
      <article class="tile">
        <hl-partner-tile
          partner={c.partner}
          status={c.status}
          connection-id={c.connectionId}
          onhlRevoke={(event: CustomEvent<string>) => revoke(event.detail)}
        ></hl-partner-tile>
        <div class="tile-meta">
          <span>Connected <strong>{c.connectedAt ? formatIstDate(c.connectedAt) : '—'}</strong></span>
          <span>Last event (IST) <strong>{c.lastEventAt ? formatIstDateTime(c.lastEventAt) : '—'}</strong></span>
        </div>
      </article>
    {:else}
      <div class="empty-state">
        <div class="empty-state-copy"><strong>No partners connected</strong><p>Choose a wearable above to start syncing activity.</p></div>
      </div>
    {/each}
  </div>
{/if}

<style>
  .connect { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 1rem; margin-bottom: 1.25rem; padding-top: 1.6rem; }
  .connect-copy { display: flex; align-items: center; gap: .9rem; min-width: min(100%, 320px); flex: 1; }
  .connect-copy h2 { margin: 0; font-size: 1.1rem; }
  .connect-copy p { margin: .15rem 0 0; font-size: .88rem; }
  .connect-row { display: flex; gap: .5rem; min-width: min(100%, 320px); }
  .connect-row .input { min-width: 180px; }
  .secret { margin-bottom: 1.25rem; padding-top: 1.6rem; background: color-mix(in srgb, var(--accent) 5%, var(--surface)); }
  .secret code { padding: .35rem .5rem; border-radius: 8px; background: var(--surface-2); font-size: .82rem; }
  .secret code { word-break: break-all; }
  .kv { display: flex; align-items: center; gap: 0.75rem; padding: 0.55rem 0; border-top: 1px solid var(--border); }
  .kv .muted { width: 110px; flex-shrink: 0; }
  .kv code { flex: 1; min-width: 0; }
  .tiles { grid-template-columns: repeat(auto-fill, minmax(min(100%, 260px), 1fr)); gap: 1.1rem; }
  .tile { display: grid; align-content: start; gap: .55rem; }
  .tile-meta { display: grid; gap: .3rem; padding: .25rem .25rem 0; color: var(--text-muted); font-size: .75rem; }
  .tile-meta span { display: flex; justify-content: space-between; gap: .5rem; }
  .tile-meta strong { color: var(--text); font-weight: 600; text-align: right; }
  @media (max-width: 560px) {
    .kv { align-items: flex-start; flex-wrap: wrap; }
    .kv .muted { width: 100%; }
    .kv code { flex-basis: calc(100% - 70px); }
  }
</style>
