<script lang="ts">
  import Topbar from '$lib/components/Topbar.svelte';
  let { data } = $props();
  const pct = (c: number) => Math.round(c * 100);
</script>

<Topbar title="Recommendations" />

<div class="grid list">
  {#each data.items as r}
    <div class="card rec">
      <div class="head"><span class="badge">{r.type}</span><span class="muted">{pct(r.confidence)}% confidence</span></div>
      <h2>{r.title}</h2>
      <p class="muted">{r.message}</p>
      {#if r.action?.label}
        <button class="btn btn-primary">{r.action.label}</button>
      {/if}
    </div>
  {:else}
    <p class="muted">No recommendations yet. They appear once you have some activity history.</p>
  {/each}
</div>

<style>
  .list { grid-template-columns: repeat(auto-fit, minmax(280px, 1fr)); }
  .rec .head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 0.4rem; }
  .rec h2 { font-size: 1.05rem; }
</style>
