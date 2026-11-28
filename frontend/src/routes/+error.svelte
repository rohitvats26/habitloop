<script lang="ts">
  import { page } from '$app/stores';
  import { isAuthenticated } from '$lib/auth.svelte';

  const notFound = $derived($page.status === 404);
  const home = $derived(isAuthenticated() ? '/dashboard' : '/login');
</script>

<svelte:head><title>{notFound ? 'Page not found' : 'Something went wrong'} · HabitLoop</title></svelte:head>

<main class="error-page" id="main-content">
  <section class="card error-card" aria-labelledby="error-heading">
    <span class="eyebrow">Error {$page.status}</span>
    <h1 id="error-heading">{notFound ? "We couldn't find that page" : 'Something went wrong'}</h1>
    <p class="muted">
      {notFound
        ? 'The link may be out of date, or the page may have moved.'
        : 'An unexpected problem stopped this page from loading. Please try again.'}
    </p>
    <div class="actions">
      <a class="btn btn-primary" href={home}>{home === '/dashboard' ? 'Go to dashboard' : 'Go to log in'}</a>
      {#if !notFound}<button class="btn" type="button" onclick={() => location.reload()}>Try again</button>{/if}
    </div>
  </section>
</main>

<style>
  .error-page { display: grid; min-height: 100vh; place-items: center; padding: 1.5rem; }
  .error-card { display: grid; max-width: 520px; gap: .6rem; text-align: left; }
  .error-card h1 { margin: 0; font-size: clamp(1.5rem, 3vw, 2rem); }
  .error-card p { margin: 0 0 .5rem; }
  .actions { display: flex; flex-wrap: wrap; gap: .5rem; }
</style>
