<script lang="ts">
  import Sidebar from '$lib/components/Sidebar.svelte';
  let { data, children } = $props();
</script>

<div class="shell">
  <a class="skip-link" href="#main-content">Skip to main content</a>
  <Sidebar user={data.user} />
  <main class="main" id="main-content" tabindex="-1">
    {@render children()}
  </main>
</div>

<style>
  .shell { display: flex; align-items: stretch; min-height: 100vh; }
  .main { flex: 1; width: min(100%, 1480px); min-width: 0; margin: 0 auto; padding: 2rem clamp(1.25rem, 3vw, 3rem); }
  @media (max-width: 760px) {
    .shell { flex-direction: column; }
    .main { width: 100%; padding: 1.25rem 1rem; }
  }
</style>
