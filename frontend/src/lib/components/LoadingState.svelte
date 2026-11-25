<script lang="ts">
  let { message }: { message: string } = $props();
</script>

<div class="loading-state" role="status" aria-live="polite">
  <span class="loading-spinner" aria-hidden="true"></span>
  <span>{message}</span>
</div>
