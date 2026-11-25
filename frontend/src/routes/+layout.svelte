<script lang="ts">
  import '../app.css';
  import { onMount } from 'svelte';
  import Toasts from '$lib/components/Toasts.svelte';

  let { children } = $props();
  let componentError = $state('');

  onMount(() => {
    void registerComponents();
  });

  async function registerComponents() {
    try {
      const { defineCustomElements } = await import('@habitloop/components/loader');
      await defineCustomElements();
    } catch (error) {
      console.error('Failed to register HabitLoop custom elements.', error);
      componentError = "Some parts of the page couldn't load. Refresh the page to try again.";
    }
  }
</script>

<Toasts />
{#if componentError}
  <div class="error-banner" role="alert">{componentError}</div>
{/if}
{@render children()}
