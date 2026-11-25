<script lang="ts">
  import { page } from '$app/stores';
  import type { AuthUser } from '$lib/types';

  let { user }: { user?: AuthUser | null } = $props();

  const links = [
    { href: '/dashboard', label: 'Dashboard', icon: 'dashboard' },
    { href: '/habits', label: 'Habits', icon: 'habits' },
    { href: '/activity', label: 'Activity', icon: 'activity' },
    { href: '/challenges', label: 'Challenges', icon: 'challenges' },
    { href: '/invitations', label: 'Invitations', icon: 'invitations' },
    { href: '/integrations', label: 'Integrations', icon: 'integrations' },
    { href: '/analytics', label: 'Analytics', icon: 'analytics' },
    { href: '/recommendations', label: 'Recommendations', icon: 'recommendations' },
    { href: '/profile', label: 'Profile', icon: 'profile' }
  ];

  const isAdmin = $derived(user?.roles.includes('TENANT_ADMIN') ?? false);
</script>

<aside class="sidebar">
  <a class="brand" href="/dashboard">
    <span class="brand-mark">H</span>
    <span>HabitLoop<small>HABIT & WELLNESS</small></span>
  </a>
  <div class="nav-caption">YOUR SPACE</div>
  <nav aria-label="Main navigation">
    {#each links as link}
      <a
        href={link.href}
        class:active={$page.url.pathname.startsWith(link.href)}
        aria-current={$page.url.pathname.startsWith(link.href) ? 'page' : undefined}
      >
        <span class="nav-icon" aria-hidden="true">
          <svg viewBox="0 0 24 24">
            {#if link.icon === 'dashboard'}
              <rect x="3.5" y="3.5" width="7" height="7" rx="1.5" /><rect x="13.5" y="3.5" width="7" height="7" rx="1.5" />
              <rect x="3.5" y="13.5" width="7" height="7" rx="1.5" /><rect x="13.5" y="13.5" width="7" height="7" rx="1.5" />
            {:else if link.icon === 'habits'}
              <rect x="4" y="5" width="16" height="16" rx="2" /><path d="M8 3.5v3M16 3.5v3M4 10h16M8 14l2.2 2.2L16 11.5" />
            {:else if link.icon === 'activity'}
              <path d="M3 12h4l2.2-6 4.1 12 2.1-6H21" />
            {:else if link.icon === 'challenges'}
              <path d="M8 4h8v4a4 4 0 0 1-8 0V4ZM8 6H5v2a4 4 0 0 0 4 4M16 6h3v2a4 4 0 0 1-4 4M12 12v5m-4 3h8m-7 0v-3h6v3" />
            {:else if link.icon === 'invitations'}
              <rect x="3" y="5" width="18" height="14" rx="2" /><path d="m4 7 8 6 8-6" />
            {:else if link.icon === 'integrations'}
              <path d="M9 15 7.5 16.5a4 4 0 0 1-5.7-5.7l4-4a4 4 0 0 1 5.7 0M15 9l1.5-1.5a4 4 0 0 1 5.7 5.7l-4 4a4 4 0 0 1-5.7 0M8 12h8" />
            {:else if link.icon === 'analytics'}
              <path d="M4 20V10m5 10V4m5 16v-7m5 7V7" />
            {:else if link.icon === 'recommendations'}
              <path d="m12 3 1.5 5.5L19 10l-5.5 1.5L12 17l-1.5-5.5L5 10l5.5-1.5L12 3ZM19 15l.8 2.2L22 18l-2.2.8L19 21l-.8-2.2L16 18l2.2-.8L19 15Z" />
            {:else}
              <circle cx="12" cy="8" r="3.5" /><path d="M5 20a7 7 0 0 1 14 0" />
            {/if}
          </svg>
        </span>
        <span>{link.label}</span>
      </a>
    {/each}
  </nav>
  <div class="sidebar-footer">
    {#if isAdmin}<div class="admin-badge badge"><span class="admin-dot"></span>Workspace admin</div>{/if}
    <span>Small steps. Steady progress.</span>
  </div>
</aside>

<style>
  .sidebar {
    position: sticky;
    top: 0;
    display: flex;
    width: 254px;
    height: 100vh;
    flex-shrink: 0;
    background: var(--surface);
    border-right: 1px solid color-mix(in srgb, var(--border) 75%, transparent);
    padding: 1.5rem 1rem 1.25rem;
    flex-direction: column;
    gap: 1.35rem;
  }
  .brand {
    display: flex;
    align-items: center;
    gap: .75rem;
    padding: .25rem .55rem;
    color: var(--text);
    font-weight: 750;
    font-size: 1.08rem;
    line-height: 1.1;
  }
  .brand small { display: block; margin-top: .32rem; color: var(--text-muted); font-size: .58rem; letter-spacing: .12em; }
  .brand .brand-mark {
    border-color: color-mix(in srgb, var(--primary) 26%, transparent);
    background: color-mix(in srgb, var(--primary) 13%, var(--surface));
    color: var(--primary);
  }
  .nav-caption { padding: .25rem .65rem 0; color: var(--text-muted); font-size: .67rem; font-weight: 750; letter-spacing: .13em; }
  nav { display: flex; flex-direction: column; gap: .3rem; }
  nav a {
    display: flex;
    align-items: center;
    gap: .75rem;
    min-height: 42px;
    padding: .6rem .7rem;
    border: 1px solid transparent;
    border-radius: 11px;
    color: var(--text-muted);
    font-weight: 550;
    transition: color .15s ease, background .15s ease, border-color .15s ease, transform .15s ease;
  }
  nav a:hover { background: var(--surface-2); color: var(--text); text-decoration: none; transform: translateX(2px); }
  nav a.active {
    border-color: color-mix(in srgb, var(--primary) 15%, transparent);
    background: color-mix(in srgb, var(--primary) 11%, var(--surface));
    color: var(--primary);
  }
  .nav-icon { display: grid; width: 1.25rem; height: 1.25rem; flex: none; place-items: center; }
  .nav-icon svg { width: 1.15rem; height: 1.15rem; fill: none; stroke: currentColor; stroke-width: 1.65; stroke-linecap: round; stroke-linejoin: round; }
  nav a.active .nav-icon { filter: drop-shadow(0 2px 4px color-mix(in srgb, var(--primary) 28%, transparent)); }
  .sidebar-footer { display: grid; gap: .9rem; margin-top: auto; padding: 1rem .6rem .2rem; border-top: 1px solid var(--border); color: var(--text-muted); font-size: .75rem; }
  .admin-badge { display: inline-flex; width: fit-content; align-items: center; gap: .45rem; }
  .admin-dot { width: 6px; height: 6px; border-radius: 50%; background: var(--primary); }
  @media (max-width: 760px) {
    .sidebar { position: static; width: 100%; height: auto; gap: .65rem; padding: .85rem 1rem; border-right: none; border-bottom: 1px solid var(--border); }
    .brand { padding: 0 .25rem; }
    .nav-caption, .sidebar-footer { display: none; }
    nav { flex-direction: row; flex-wrap: nowrap; overflow-x: auto; padding: .15rem 0 .3rem; scrollbar-width: thin; }
    nav a { min-height: 40px; flex: none; padding: .45rem .7rem; font-size: .83rem; }
    nav a:hover { transform: translateY(-1px); }
  }
  @media (prefers-reduced-motion: reduce) { nav a { transition: none; } }
</style>
