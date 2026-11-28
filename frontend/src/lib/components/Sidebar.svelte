<script lang="ts">
  import { page } from '$app/stores';
  import { afterNavigate } from '$app/navigation';
  import { tick } from 'svelte';
  import { pendingInvitations, refreshPendingInvitations } from '$lib/stores/invitations.svelte';
  import ThemeToggle from './ThemeToggle.svelte';
  import type { AuthUser } from '$lib/types';

  let { user }: { user?: AuthUser | null } = $props();
  let nav = $state<HTMLElement | null>(null);
  let canScrollStart = $state(false);
  let canScrollEnd = $state(false);

  function updateScrollHints() {
    if (!nav) return;
    canScrollStart = nav.scrollLeft > 4;
    canScrollEnd = nav.scrollLeft + nav.clientWidth < nav.scrollWidth - 4;
  }

  function scrollActiveIntoView() {
    nav?.querySelector<HTMLElement>('a[aria-current="page"]')
      ?.scrollIntoView({ block: 'nearest', inline: 'center', behavior: 'instant' });
    updateScrollHints();
  }

  afterNavigate(() => {
    void refreshPendingInvitations();
  });

  // Re-centre after the route changes or the invitation badge changes the strip's width.
  $effect(() => {
    void $page.url.pathname;
    void pendingInvitations.count;
    void tick().then(scrollActiveIntoView);
  });

  const links = [
    { href: '/dashboard', label: 'Dashboard', icon: 'dashboard' },
    { href: '/habits', label: 'Habits', icon: 'habits' },
    { href: '/activity', label: 'Activity', icon: 'activity' },
    { href: '/challenges', label: 'Challenges', icon: 'challenges' },
    { href: '/invitations', label: 'Invitations', icon: 'invitations' },
    { href: '/integrations', label: 'Integrations', icon: 'integrations' },
    { href: '/analytics', label: 'Analytics', icon: 'analytics' },
    { href: '/recommendations', label: 'Recommendations', icon: 'recommendations' },
    { href: '/achievements', label: 'Achievements', icon: 'achievements' },
    { href: '/profile', label: 'Profile', icon: 'profile' }
  ];

  const isAdmin = $derived(user?.roles.includes('TENANT_ADMIN') ?? false);
</script>

<svelte:window onresize={updateScrollHints} />

<aside class="sidebar">
  <a class="brand" href="/dashboard">
    <span class="brand-mark">H</span>
    <span>HabitLoop<small>HABIT & WELLNESS</small></span>
  </a>
  <div class="nav-caption">YOUR SPACE</div>
  <div class="nav-scroll" class:fade-start={canScrollStart} class:fade-end={canScrollEnd}>
  <nav aria-label="Main navigation" bind:this={nav} onscroll={updateScrollHints}>
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
            {:else if link.icon === 'achievements'}
              <path d="M8 4h8v4a4 4 0 0 1-8 0V4ZM8 6H5v2a4 4 0 0 0 4 4m7-6h3v2a4 4 0 0 1-4 4m-3 1v4m-4 3h8m-7 0v-3h6v3" />
            {:else}
              <circle cx="12" cy="8" r="3.5" /><path d="M5 20a7 7 0 0 1 14 0" />
            {/if}
          </svg>
        </span>
        <span>{link.label}</span>
        {#if link.href === '/invitations' && pendingInvitations.count}
          <span class="nav-count" aria-label={`${pendingInvitations.count} pending`}>
            {pendingInvitations.count > 99 ? '99+' : pendingInvitations.count}
          </span>
        {/if}
      </a>
    {/each}
  </nav>
  </div>
  <div class="sidebar-footer">
    <div class="member">
      <span class="avatar" aria-hidden="true">{isAdmin ? 'A' : 'M'}</span>
      <span><strong>{isAdmin ? 'Workspace admin' : 'Member'}</strong><small>India Standard Time</small></span>
    </div>
    <ThemeToggle />
  </div>
</aside>

<style>
  .sidebar {
    position: sticky;
    top: 0;
    display: flex;
    width: 260px;
    height: 100vh;
    flex-shrink: 0;
    flex-direction: column;
    gap: 1.2rem;
    padding: 1.4rem .9rem 1rem;
    background: var(--sidebar-bg);
    color: var(--sidebar-text);
    box-shadow: inset -1px 0 0 rgba(255, 255, 255, 0.06);
    overflow-y: auto;
  }
  .sidebar::before {
    position: absolute;
    inset: 0 0 auto;
    height: 220px;
    background: radial-gradient(18rem 10rem at 20% 0%, rgba(94, 234, 212, 0.18), transparent 70%);
    content: '';
    pointer-events: none;
  }
  .brand {
    position: relative;
    display: flex;
    align-items: center;
    gap: .75rem;
    padding: .2rem .55rem;
    color: #fff;
    font-weight: 780;
    font-size: 1.12rem;
    letter-spacing: -.02em;
    line-height: 1.1;
  }
  .brand:hover { color: #fff; }
  .brand small { display: block; margin-top: .3rem; color: var(--sidebar-muted); font-size: .58rem; font-weight: 700; letter-spacing: .16em; }
  .brand .brand-mark {
    width: 2.4rem;
    height: 2.4rem;
    border: 0;
    border-radius: 12px;
    background: var(--grad-brand);
    color: #052e2b;
    font-weight: 850;
    box-shadow: 0 8px 22px rgba(20, 184, 166, 0.35);
  }
  .nav-caption { padding: .2rem .7rem 0; color: var(--sidebar-muted); font-size: .65rem; font-weight: 750; letter-spacing: .16em; }
  nav { display: flex; flex-direction: column; gap: .2rem; }
  nav a {
    position: relative;
    display: flex;
    align-items: center;
    gap: .75rem;
    min-height: 42px;
    padding: .55rem .75rem;
    border-radius: 12px;
    color: var(--sidebar-text);
    font-weight: 560;
    font-size: .92rem;
    transition: color .15s ease, background-color .15s ease, transform .15s ease;
  }
  nav a:hover { background: rgba(255, 255, 255, 0.06); color: #fff; transform: translateX(2px); }
  nav a.active {
    background: linear-gradient(90deg, rgba(94, 234, 212, 0.2), rgba(94, 234, 212, 0.06));
    color: #fff;
    font-weight: 650;
  }
  nav a.active::before {
    position: absolute;
    top: 22%;
    bottom: 22%;
    left: -.9rem;
    width: 4px;
    border-radius: 0 4px 4px 0;
    background: #5eead4;
    box-shadow: 0 0 12px rgba(94, 234, 212, 0.7);
    content: '';
  }
  nav a:focus-visible { outline: 2px solid #5eead4; outline-offset: 2px; }
  .nav-icon { display: grid; width: 1.25rem; height: 1.25rem; flex: none; place-items: center; color: var(--sidebar-muted); }
  nav a:hover .nav-icon, nav a.active .nav-icon { color: #5eead4; }
  .nav-icon svg { width: 1.15rem; height: 1.15rem; fill: none; stroke: currentColor; stroke-width: 1.7; stroke-linecap: round; stroke-linejoin: round; }
  .nav-count { min-width: 1.4rem; margin-left: auto; padding: .08rem .42rem; border-radius: 999px; background: var(--grad-warm); color: #2b1300; font-size: .7rem; font-weight: 800; text-align: center; font-variant-numeric: tabular-nums; }
  .sidebar-footer { display: grid; gap: .8rem; margin-top: auto; padding: .9rem .4rem .1rem; border-top: 1px solid rgba(255, 255, 255, 0.08); }
  .member { display: flex; align-items: center; gap: .65rem; }
  .member strong { display: block; color: #fff; font-size: .83rem; }
  .member small { color: var(--sidebar-muted); font-size: .72rem; }
  .avatar { display: grid; width: 2.1rem; height: 2.1rem; place-items: center; border-radius: 50%; background: var(--grad-violet); color: #fff; font-size: .82rem; font-weight: 800; }
  @media (max-width: 760px) {
    .sidebar { position: sticky; z-index: 20; width: 100%; height: auto; gap: .55rem; padding: .75rem .85rem .5rem; overflow: visible; box-shadow: 0 6px 20px rgba(0, 0, 0, 0.18); }
    .sidebar::before { height: 100%; }
    .brand { padding: 0 .2rem; font-size: 1rem; }
    .brand .brand-mark { width: 2rem; height: 2rem; }
    .nav-caption, .sidebar-footer { display: none; }
    nav { flex-direction: row; flex-wrap: nowrap; overflow-x: auto; padding: .1rem 0 .35rem; scrollbar-width: none; }
    nav::-webkit-scrollbar { display: none; }
    nav a { min-height: 38px; flex: none; padding: .4rem .75rem; font-size: .83rem; }
    nav a:hover { transform: none; }
    nav a.active::before { top: auto; right: 22%; bottom: -.2rem; left: 22%; width: auto; height: 3px; border-radius: 3px; }
    .nav-scroll { position: relative; }
    .nav-scroll.fade-start { -webkit-mask-image: linear-gradient(90deg, transparent, #000 2rem); mask-image: linear-gradient(90deg, transparent, #000 2rem); }
    .nav-scroll.fade-end { -webkit-mask-image: linear-gradient(90deg, #000 calc(100% - 2rem), transparent); mask-image: linear-gradient(90deg, #000 calc(100% - 2rem), transparent); }
    .nav-scroll.fade-start.fade-end { -webkit-mask-image: linear-gradient(90deg, transparent, #000 2rem, #000 calc(100% - 2rem), transparent); mask-image: linear-gradient(90deg, transparent, #000 2rem, #000 calc(100% - 2rem), transparent); }
    .nav-count { margin-left: .1rem; }
  }
  @media (prefers-reduced-motion: reduce) { nav a { transition: none; } }
</style>