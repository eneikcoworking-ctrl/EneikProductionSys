<script lang="ts">
  // Renders scoped log lines (SYSTEM or PROJECT — see LogScope/LogScopeBuffer server-side) as a
  // control-room event feed. Lines are plain formatted strings from the backend, e.g.
  // "2026-07-18T00:12:03Z WARN c.e.p...  SYSTEM STALLED: ...".
  export let lines: string[] = [];
  export let emptyText = 'No recent activity recorded yet.';

  function levelOf(line: string): 'error' | 'warning' | 'info' {
    if (/\bERROR\b/.test(line)) return 'error';
    if (/\bWARN\b/.test(line)) return 'warning';
    return 'info';
  }
</script>

<div class="ticker">
  {#if lines.length === 0}
    <p class="empty-line">{emptyText}</p>
  {:else}
    {#each lines.slice(-60).reverse() as line}
      <div class="line line-{levelOf(line)}">{line}</div>
    {/each}
  {/if}
</div>

<style>
  .ticker {
    background: var(--neutral-900);
    border-radius: var(--radius);
    color: var(--neutral-200);
    display: flex;
    flex-direction: column;
    font-family: var(--font-mono);
    font-size: 11px;
    gap: 3px;
    max-height: 320px;
    overflow-y: auto;
    padding: var(--space-3);
  }
  .line {
    border-left: 3px solid transparent;
    padding: 2px 8px;
    white-space: pre-wrap;
    word-break: break-word;
  }
  .line-error {
    background: rgba(186, 26, 26, 0.25);
    border-left-color: var(--error);
    color: #ffb4ab;
  }
  .line-warning {
    background: rgba(180, 83, 9, 0.2);
    border-left-color: var(--warning);
    color: #ffd699;
  }
  .line-info {
    color: var(--neutral-300);
  }
  .empty-line {
    color: var(--neutral-400);
    font-family: var(--font-body);
    font-size: 13px;
    margin: 0;
  }
</style>
