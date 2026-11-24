<script lang="ts">
  import '../app.css';
  import { onMount } from 'svelte';
  import Toasts from '$lib/components/Toasts.svelte';

  let { children } = $props();

  onMount(async () => {
    // Register the Stencil custom elements on the client. If the library hasn't been built yet,
    // the hl-* elements simply render nothing and the pages still work.
    try {
      const mod: any = await import('@habitloop/components/loader');
      mod.defineCustomElements?.();
    } catch {
      /* Stencil component library not built — run `npm i && npm run build` in components-stencil */
    }
  });
</script>

<Toasts />
{@render children()}
