<script lang="ts">
  let { title }: { title: string } = $props();
</script>

<header class="topbar">
  <h1>{title}</h1>
  <form method="POST" action="/logout">
    <button class="btn" type="submit">Log out</button>
  </form>
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
