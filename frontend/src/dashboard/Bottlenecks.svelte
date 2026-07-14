<script lang="ts">
  import type { Bottleneck } from '../lib/types';
  export let bottlenecks: Bottleneck[] = [];
</script>

<div class="p-4 bg-white shadow rounded-lg">
  <h2 class="text-xl font-bold mb-4 text-red-600">Bottlenecks</h2>
  {#if bottlenecks.length === 0}
    <p class="text-gray-500">No bottlenecks detected.</p>
  {:else}
    <ul>
      {#each bottlenecks as bottleneck}
        <li class="mb-4 p-3 border-l-4 border-red-500 bg-red-50">
          <div class="font-bold">{bottleneck.type === 'no_free_jules_slot' ? 'No Free Jules Slot' : 'Agent Reliability Issue'}</div>
          <p class="text-sm">{bottleneck.reason}</p>
          {#if bottleneck.tag}
             <div class="text-xs text-gray-600 mt-1">Tag: {bottleneck.tag} | Queued: {bottleneck.queuedCount}</div>
          {/if}
          {#if bottleneck.accountId}
             <div class="text-xs text-gray-600 mt-1">Account ID: {bottleneck.accountId} | Expired (24h): {bottleneck.expiredCount24h}</div>
          {/if}
        </li>
      {/each}
    </ul>
  {/if}
</div>
