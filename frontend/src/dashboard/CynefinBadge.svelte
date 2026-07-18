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
    border-radius: var(--radius-pill);
    color: #fff;
    display: inline-flex;
    font-size: 10px;
    font-weight: 700;
    letter-spacing: 0.04em;
    padding: 3px 9px;
    text-transform: uppercase;
    white-space: nowrap;
  }
  /* Stitch's own explicit Cynefin mapping (design_v4): Clear=emerald, Complicated=amber,
     Complex=rose, Chaotic=violet - copied from the generated HTML, not guessed. */
  .cynefin-clear { background: var(--success); }
  .cynefin-complicated { background: var(--warning); }
  .cynefin-complex { background: var(--cynefin-complex); }
  .cynefin-chaotic { background: var(--cynefin-chaotic); }
</style>
