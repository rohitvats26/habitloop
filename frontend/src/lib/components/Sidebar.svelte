<script lang="ts">
  import { page } from '$app/stores';
  import type { AuthUser } from '$lib/types';

  let { user }: { user?: AuthUser } = $props();

  const links = [
    { href: '/dashboard', label: 'Dashboard' },
    { href: '/habits', label: 'Habits' },
    { href: '/activity', label: 'Activity' },
    { href: '/challenges', label: 'Challenges' },
    { href: '/invitations', label: 'Invitations' },
    { href: '/integrations', label: 'Integrations' },
    { href: '/analytics', label: 'Analytics' },
    { href: '/recommendations', label: 'Recommendations' },
    { href: '/profile', label: 'Profile' }
  ];

  const isAdmin = $derived(user?.roles.includes('TENANT_ADMIN') ?? false);
</script>

<aside class="sidebar">
  <div class="brand">⬡ HabitLoop</div>
  <nav>
    {#each links as link}
      <a href={link.href} class:active={$page.url.pathname.startsWith(link.href)}>{link.label}</a>
    {/each}
  </nav>
  {#if isAdmin}
    <div class="admin-badge badge">Tenant Admin</div>
  {/if}
</aside>

<style>
  .sidebar {
    width: 220px;
    flex-shrink: 0;
    background: var(--surface);
    border-right: 1px solid var(--border);
    padding: 1rem 0.75rem;
    display: flex;
    flex-direction: column;
    gap: 0.75rem;
    min-height: 100vh;
  }
  .brand { font-weight: 750; font-size: 1.1rem; padding: 0.5rem 0.6rem; }
  nav { display: flex; flex-direction: column; gap: 0.15rem; }
  nav a {
    padding: 0.5rem 0.65rem;
    border-radius: 9px;
    color: var(--text-muted);
    font-weight: 550;
  }
  nav a:hover { background: var(--surface-2); text-decoration: none; }
  nav a.active { background: color-mix(in srgb, var(--primary) 14%, transparent); color: var(--primary); }
  .admin-badge { align-self: flex-start; margin: 0 0.6rem; }
  @media (max-width: 760px) {
    .sidebar { width: 100%; min-height: auto; border-right: none; border-bottom: 1px solid var(--border); }
    nav { flex-direction: row; flex-wrap: wrap; }
  }
</style>
