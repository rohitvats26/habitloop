<script lang="ts">
  import Sidebar from '$lib/components/Sidebar.svelte';
  let { data, children } = $props();
</script>

<div class="shell">
  <Sidebar user={data.user} />
  <main class="main">
    {@render children()}
  </main>
</div>

<style>
  .shell { display: flex; align-items: stretch; min-height: 100vh; }
  .main { flex: 1; padding: 1.5rem 1.75rem; max-width: 1100px; }
  @media (max-width: 760px) {
    .shell { flex-direction: column; }
    .main { padding: 1rem; }
  }
</style>
