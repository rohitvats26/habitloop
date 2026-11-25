<script lang="ts">
  import Topbar from '$lib/components/Topbar.svelte';
</script>

<Topbar title="Welcome to HabitLoop" subtitle="A few simple steps to get your routine going." />

<div class="grid cards">
  <a class="card step" href="/habits">
    <span class="step-number">01</span>
    <div><h2>Create a habit</h2><p class="muted">Set a schedule and start tracking completions and streaks.</p></div>
    <span class="step-link">Explore habits <span aria-hidden="true">→</span></span>
  </a>
  <a class="card step" href="/challenges">
    <span class="step-number">02</span>
    <div><h2>Join a challenge</h2><p class="muted">Share a goal with your community and stay motivated.</p></div>
    <span class="step-link">Browse challenges <span aria-hidden="true">→</span></span>
  </a>
  <a class="card step" href="/integrations">
    <span class="step-number">03</span>
    <div><h2>Connect a wearable</h2><p class="muted">Sync activity automatically from a connected partner.</p></div>
    <span class="step-link">View integrations <span aria-hidden="true">→</span></span>
  </a>
  <a class="card step" href="/recommendations">
    <span class="step-number">04</span>
    <div><h2>Get recommendations</h2><p class="muted">Find ideas for better habit timing and frequency.</p></div>
    <span class="step-link">See recommendations <span aria-hidden="true">→</span></span>
  </a>
</div>

<style>
  .cards { grid-template-columns: repeat(auto-fit, minmax(min(100%, 270px), 1fr)); margin-top: 0.5rem; }
  .step { display: grid; min-height: 220px; grid-template-rows: auto 1fr auto; gap: 1rem; padding: 1.4rem; }
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
  .step h2 { margin: 0 0 .45rem; }
  .step p { margin: 0; font-size: .9rem; }
  .step-link { display: flex; justify-content: space-between; color: var(--primary); font-size: .82rem; font-weight: 700; }
</style>
