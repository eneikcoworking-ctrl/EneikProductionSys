<script lang="ts">
  import { onMount, onDestroy } from 'svelte';
  import Agents from './dashboard/Agents.svelte';
  import Queue from './dashboard/Queue.svelte';
  import Bottlenecks from './dashboard/Bottlenecks.svelte';
  import Pipeline from './dashboard/Pipeline.svelte';
  import type { Agent, QueueData, Bottleneck, PipelineData } from './lib/types';

  let agents: Agent[] = [];
  let queue: QueueData = { byTag: [], totalQueued: 0 };
  let bottlenecks: Bottleneck[] = [];
  let pipeline: PipelineData = { queued: 0, claimed: 0, in_progress: 0, review: 0, done: 0, failed: 0 };
  let interval: number | undefined;

  const API_BASE = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';

  async function fetchData() {
    try {
      const [agentsRes, queueRes, bottlenecksRes, pipelineRes] = await Promise.all([
        fetch(`${API_BASE}/api/dashboard/agents`),
        fetch(`${API_BASE}/api/dashboard/queue`),
        fetch(`${API_BASE}/api/dashboard/bottlenecks`),
        fetch(`${API_BASE}/api/dashboard/pipeline`)
      ]);

      if (agentsRes.ok) agents = await agentsRes.json();
      if (queueRes.ok) queue = await queueRes.json();
      if (bottlenecksRes.ok) bottlenecks = await bottlenecksRes.json();
      if (pipelineRes.ok) pipeline = await pipelineRes.json();
    } catch (e) {
      console.error('Failed to fetch dashboard data', e);
    }
  }

  onMount(() => {
    fetchData();
    interval = window.setInterval(fetchData, 5000);
  });

  onDestroy(() => {
    if (interval) clearInterval(interval);
  });
</script>

<main class="min-h-screen bg-gray-100 p-8">
  <div class="max-w-7xl mx-auto space-y-8">
    <h1 class="text-3xl font-extrabold text-gray-900">Live Dashboard</h1>

    <Pipeline {pipeline} />

    <div class="grid grid-cols-1 lg:grid-cols-3 gap-8">
      <div class="lg:col-span-2 space-y-8">
        <Agents {agents} />
        <Queue {queue} />
      </div>
      <div>
        <Bottlenecks {bottlenecks} />
      </div>
    </div>
  </div>
</main>

<style>
  :global(body) {
    margin: 0;
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
  }
</style>
