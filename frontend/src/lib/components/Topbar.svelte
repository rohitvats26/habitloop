<script lang="ts">
  import { goto } from '$app/navigation';
  import { logout } from '$lib/auth.svelte';

  let { title }: { title: string } = $props();

  async function doLogout() {
    logout();
    await goto('/login');
  }
</script>

<header class="topbar">
  <h1>{title}</h1>
  <button class="btn" type="button" onclick={doLogout}>Log out</button>
</header>

<style>
  .topbar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 1.25rem;
  }
  .topbar h1 { margin: 0; }
</style>
