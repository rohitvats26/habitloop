<script lang="ts">
  import Topbar from '$lib/components/Topbar.svelte';
  import { enhance } from '$app/forms';
  let { data, form } = $props();
</script>

<Topbar title="Challenges" />
{#if form?.error}<div class="error-banner">{form.error}</div>{/if}

<div class="actions"><a class="btn btn-primary" href="/challenges/new">+ Create challenge</a></div>

<div class="grid feed">
  {#each data.feed.content as c (c.id)}
    <div class="card ch">
      <div class="head">
        <a href={`/challenges/${c.id}`}><strong>{c.title}</strong></a>
        <span class="badge">{c.visibility}</span>
        <span class="badge">{c.status}</span>
      </div>
      {#if c.description}<p class="muted">{c.description}</p>{/if}
      <div class="meta muted">Target {c.target} · {c.startsOn} → {c.endsOn}</div>
      <div class="ops">
        <a class="btn" href={`/challenges/${c.id}`}>View</a>
        {#if c.visibility === 'PUBLIC'}
          <form method="POST" action="?/join" use:enhance>
            <input type="hidden" name="challengeId" value={c.id} />
            <button class="btn btn-primary" type="submit">Join</button>
          </form>
        {/if}
      </div>
    </div>
  {:else}
    <p class="muted">No challenges yet. Create one to get started.</p>
  {/each}
</div>

<style>
  .actions { margin-bottom: 1rem; }
  .feed { grid-template-columns: repeat(auto-fit, minmax(280px, 1fr)); }
  .head { display: flex; align-items: center; gap: 0.5rem; }
  .meta { margin: 0.4rem 0 0.6rem; font-size: 0.85rem; }
  .ops { display: flex; gap: 0.5rem; }
</style>
