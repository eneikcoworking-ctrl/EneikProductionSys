<script lang="ts">
  import type { Agent } from '../lib/types';
  export let agents: Agent[] = [];
</script>

<div class="p-4 bg-white shadow rounded-lg">
  <h2 class="text-xl font-bold mb-4">Agents</h2>
  <table class="min-w-full">
    <thead>
      <tr class="border-b">
        <th class="text-left py-2">Name</th>
        <th class="text-left py-2">Status</th>
        <th class="text-left py-2">Current Role</th>
        <th class="text-left py-2">Task</th>
        <th class="text-left py-2">Last Heartbeat</th>
      </tr>
    </thead>
    <tbody>
      {#each agents as agent}
        <tr class="border-b">
          <td class="py-2">{agent.name}</td>
          <td class="py-2">
            <span class="px-2 py-1 rounded text-sm {agent.status === 'idle' ? 'bg-green-100 text-green-800' : 'bg-blue-100 text-blue-800'}">
              {agent.status}
            </span>
          </td>
          <td class="py-2">{agent.currentRoleTag || '-'}</td>
          <td class="py-2">{agent.currentTaskDescription || '-'}</td>
          <td class="py-2">{agent.lastHeartbeat ? new Date(agent.lastHeartbeat).toLocaleTimeString() : '-'}</td>
        </tr>
      {/each}
    </tbody>
  </table>
</div>
