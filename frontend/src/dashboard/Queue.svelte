<script lang="ts">
  import type { QueueData } from '../lib/types';
  export let queue: QueueData = { byTag: [], totalQueued: 0 };
</script>

<div class="p-4 bg-white shadow rounded-lg">
  <h2 class="text-xl font-bold mb-4">Queue (Total: {queue.totalQueued})</h2>
  <table class="min-w-full">
    <thead>
      <tr class="border-b">
        <th class="text-left py-2">Tag</th>
        <th class="text-left py-2">Count</th>
        <th class="text-left py-2">Oldest Waiting</th>
      </tr>
    </thead>
    <tbody>
      {#each queue.byTag as item}
        <tr class="border-b">
          <td class="py-2">{item.tag}</td>
          <td class="py-2">{item.count}</td>
          <td class="py-2">{item.oldestWaitingMinutes} min</td>
        </tr>
      {/each}
    </tbody>
  </table>
</div>
