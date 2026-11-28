<script lang="ts">
  import Topbar from '$lib/components/Topbar.svelte';
  import { onMount } from 'svelte';
  import { api } from '$lib/api';

  type Status = 'done' | 'todo' | 'unknown';
  let habitStatus = $state<Status>('unknown');
  let challengeStatus = $state<Status>('unknown');
  let integrationStatus = $state<Status>('unknown');

  onMount(async () => {
    const [habits, challenges, connections] = await Promise.allSettled([
      api.habits.list(),
      api.challenges.feed(),
      api.integrations.list()
    ]);
    if (habits.status === 'fulfilled') habitStatus = habits.value.totalElements > 0 ? 'done' : 'todo';
    if (challenges.status === 'fulfilled') challengeStatus = challenges.value.content.some((c) => c.joined) ? 'done' : 'todo';
    if (connections.status === 'fulfilled') {
      integrationStatus = connections.value.some((c) => c.status === 'CONNECTED') ? 'done' : 'todo';
    }
  });

  const steps = $derived([
    { href: '/habits', title: 'Create a habit', text: 'Set a schedule and start tracking completions and streaks.', link: 'Explore habits', status: habitStatus },
    { href: '/challenges', title: 'Join a challenge', text: 'Share a goal with your community and stay motivated.', link: 'Browse challenges', status: challengeStatus },
    { href: '/integrations', title: 'Connect a wearable', text: 'Sync activity automatically from a connected partner.', link: 'View integrations', status: integrationStatus },
    { href: '/recommendations', title: 'Get recommendations', text: 'Find ideas for better habit timing and frequency.', link: 'See recommendations', status: 'unknown' as Status }
  ]);
  const doneCount = $derived(steps.filter((step) => step.status === 'done').length);
</script>

<Topbar title="Welcome to HabitLoop" subtitle="A few simple steps to get your routine going." />

{#if doneCount > 0}
  <p class="muted progress" role="status">{doneCount} of 3 setup steps complete</p>
{/if}

<div class="grid cards">
  {#each steps as step, index (step.href)}
    <a class="card step" class:done={step.status === 'done'} href={step.href}>
      <span class="step-number" aria-hidden="true">{step.status === 'done' ? '✓' : String(index + 1).padStart(2, '0')}</span>
      <div>
        <h2>{step.title}{#if step.status === 'done'}<span class="sr-only"> (completed)</span>{/if}</h2>
        <p class="muted">{step.text}</p>
      </div>
      <span class="step-link">{step.status === 'done' ? 'Done · review' : step.link} <span aria-hidden="true">→</span></span>
    </a>
  {/each}
</div>

<style>
  .progress { margin: 0 0 .75rem; font-size: .88rem; }
  .cards { grid-template-columns: repeat(auto-fit, minmax(min(100%, 270px), 1fr)); margin-top: 0.5rem; }
  .step { display: grid; min-height: 220px; grid-template-rows: auto 1fr auto; gap: 1rem; padding: 1.4rem; }
  .step.done { border-color: color-mix(in srgb, var(--success) 40%, var(--border)); }
  .step-number {
    display: grid;
    width: 2.4rem;
    height: 2.4rem;
    place-items: center;
    border-radius: 12px;
    background: color-mix(in srgb, var(--primary) 12%, transparent);
    color: var(--primary);
    font-size: .78rem;
    font-weight: 750;
  }
  .step.done .step-number { background: color-mix(in srgb, var(--success) 14%, transparent); color: var(--success); font-size: 1rem; }
  .step h2 { margin: 0 0 .45rem; }
  .step p { margin: 0; font-size: .9rem; }
  .step-link { display: flex; justify-content: space-between; color: var(--primary); font-size: .82rem; font-weight: 700; }
  .sr-only { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); white-space: nowrap; }
</style>
