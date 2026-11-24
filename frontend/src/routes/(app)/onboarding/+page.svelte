<script lang="ts">
  import Topbar from '$lib/components/Topbar.svelte';
</script>

<Topbar title="Welcome to HabitLoop 🎉" />

<div class="grid cards">
  <a class="card step" href="/habits">
    <h2>1 · Create a habit</h2>
    <p class="muted">Set a schedule and start tracking daily completions and streaks.</p>
  </a>
  <a class="card step" href="/challenges">
    <h2>2 · Join a challenge</h2>
    <p class="muted">Compete with your community in public or private challenges.</p>
  </a>
  <a class="card step" href="/integrations">
    <h2>3 · Connect a wearable</h2>
    <p class="muted">Let your device log activity automatically via secure webhooks.</p>
  </a>
  <a class="card step" href="/recommendations">
    <h2>4 · Get recommendations</h2>
    <p class="muted">See personalized suggestions for better habit timing and frequency.</p>
  </a>
</div>

<style>
  .cards { grid-template-columns: repeat(auto-fit, minmax(240px, 1fr)); margin-top: 0.5rem; }
  .step { display: block; }
  .step:hover { text-decoration: none; filter: brightness(0.99); }
</style>
