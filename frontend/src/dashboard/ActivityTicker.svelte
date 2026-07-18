<script lang="ts">
  // Renders scoped log lines (SYSTEM or PROJECT — see LogScope/LogScopeBuffer server-side) as a
  // clean, scannable data table. Lines come from ScopedBufferAppender in the exact format
  // "<ISO-8601 Instant> <LEVEL> <logger.ClassName> - <message>".
  export let lines: string[] = [];
  export let emptyText = 'No recent activity recorded yet.';

  const LINE_PATTERN = /^(\S+)\s+(\w+)\s+(\S+)\s+-\s+(.*)$/s;

  function parse(line: string) {
    const match = LINE_PATTERN.exec(line);
    if (!match) {
      return { timestamp: '', level: 'INFO', detail: line };
    }
    const [, timestamp, level, , message] = match;
    return { timestamp: formatTimestamp(timestamp), level, detail: message };
  }

  function formatTimestamp(iso: string): string {
    const date = new Date(iso);
    if (Number.isNaN(date.getTime())) return iso;
    return date.toLocaleString(undefined, {
      month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit', second: '2-digit'
    });
  }

  $: rows = lines.slice(-60).reverse().map(parse);
</script>

<div class="activity-table-wrap">
  {#if rows.length === 0}
    <p class="empty-line">{emptyText}</p>
  {:else}
    <table class="activity-table">
      <thead>
        <tr>
          <th>Timestamp</th>
          <th>Level</th>
          <th>Detail</th>
        </tr>
      </thead>
      <tbody>
        {#each rows as row}
          <tr>
            <td class="ts">{row.timestamp}</td>
            <td><span class="level level-{row.level.toLowerCase()}">{row.level}</span></td>
            <td class="detail">{row.detail}</td>
          </tr>
        {/each}
      </tbody>
    </table>
  {/if}
</div>

<style>
  .activity-table-wrap {
    max-height: 340px;
    overflow-y: auto;
  }
  .activity-table {
    border-collapse: collapse;
    width: 100%;
  }
  .activity-table th {
    background: var(--surface);
    color: var(--neutral-500);
    font-size: 11px;
    font-weight: 700;
    letter-spacing: 0.04em;
    padding: 6px 10px;
    position: sticky;
    text-align: left;
    text-transform: uppercase;
    top: 0;
  }
  .activity-table td {
    border-top: 1px solid var(--neutral-200);
    color: var(--neutral-800);
    font-size: 13px;
    padding: 7px 10px;
    vertical-align: top;
  }
  .ts {
    color: var(--neutral-500);
    font-variant-numeric: tabular-nums;
    white-space: nowrap;
  }
  .detail {
    word-break: break-word;
  }
  .level {
    border-radius: var(--radius);
    display: inline-block;
    font-size: 10px;
    font-weight: 700;
    letter-spacing: 0.04em;
    padding: 2px 6px;
  }
  .level-error { background: var(--error-bg); color: var(--error); }
  .level-warn { background: var(--warning-bg); color: var(--warning); }
  .level-info { background: var(--surface-sunken); color: var(--secondary); }
  .empty-line {
    color: var(--neutral-500);
    font-size: 13px;
    margin: 0;
    padding: var(--space-2) 0;
  }
</style>
