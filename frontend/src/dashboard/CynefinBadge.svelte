<script lang="ts">
  // Mirrors the server's task.cynefinDomain vocabulary (clear/complicated/complex/chaotic).
  export let domain: string | null | undefined;

  $: normalized = (domain || '').toLowerCase();
</script>

{#if normalized}
  <span class="cynefin-badge cynefin-{normalized}">{normalized}</span>
{/if}

<style>
  .cynefin-badge {
    border-radius: var(--radius);
    display: inline-flex;
    font-family: var(--font-mono);
    font-size: 10px;
    font-weight: 700;
    letter-spacing: 0.06em;
    padding: 2px 7px;
    text-transform: uppercase;
    white-space: nowrap;
  }
  .cynefin-clear { background: var(--surface-sunken); color: var(--primary); border: 1px solid var(--primary); }
  .cynefin-complicated { background: var(--warning-bg); color: var(--warning); border: 1px solid var(--warning); }
  .cynefin-complex { background: #F1E9FC; color: var(--accent); border: 1px solid var(--accent); }
  .cynefin-chaotic { background: var(--error-bg); color: var(--error); border: 1px solid var(--error); }
</style>
