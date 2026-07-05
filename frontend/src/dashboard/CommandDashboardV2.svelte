<script lang="ts">
  import { onMount } from 'svelte';

  export let projectId: string;
  let dashboardData: any = null;
  let loading = true;
  let error: string | null = null;

  async function fetchDashboard() {
    try {
      const baseUrl = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';
      const response = await fetch(`${baseUrl}/api/projects/${projectId}/command-dashboard`);
      if (!response.ok) throw new Error('Failed to fetch dashboard');
      dashboardData = await response.json();
    } catch (e: any) {
      error = e.message;
    } finally {
      loading = false;
    }
  }

  onMount(fetchDashboard);
</script>

<div class="space-y-6">
  <h1 class="text-2xl font-bold">Command Dashboard V2</h1>

  {#if loading}
    <div class="animate-pulse flex space-x-4">
      <div class="flex-1 space-y-4 py-1">
        <div class="h-4 bg-gray-200 rounded w-3/4"></div>
        <div class="space-y-2">
          <div class="h-4 bg-gray-200 rounded"></div>
          <div class="h-4 bg-gray-200 rounded w-5/6"></div>
        </div>
      </div>
    </div>
  {:else if error}
    <div class="p-4 bg-red-100 text-red-700 rounded-lg">{error}</div>
  {:else if dashboardData}
    <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
      <!-- Acceptance Readiness -->
      <div class="p-4 bg-white shadow rounded-lg border-t-4 {dashboardData.acceptanceReadiness.uiColorToken}">
        <h2 class="text-xl font-bold mb-2">Acceptance Readiness: <span class="uppercase">{dashboardData.acceptanceReadiness.statusLabel}</span></h2>
        {#if dashboardData.acceptanceReadiness.unmetConditions && dashboardData.acceptanceReadiness.unmetConditions.length > 0}
          <ul class="list-disc ml-5 text-red-600">
            {#each dashboardData.acceptanceReadiness.unmetConditions as condition}
              <li>{condition}</li>
            {/each}
          </ul>
        {/if}
        <div class="mt-4 grid grid-cols-2 gap-2 text-sm">
          <div class={dashboardData.acceptanceReadiness.allTasksDone === false ? 'text-red-600' : (dashboardData.acceptanceReadiness.allTasksDone === null ? 'text-yellow-600' : 'text-green-600')}>
            Tasks: {dashboardData.acceptanceReadiness.allTasksDone ?? 'unknown'}
          </div>
          <div class={dashboardData.acceptanceReadiness.allQualityGatesPassed === false ? 'text-red-600' : (dashboardData.acceptanceReadiness.allQualityGatesPassed === null ? 'text-yellow-600' : 'text-green-600')}>
            Quality Gates: {dashboardData.acceptanceReadiness.allQualityGatesPassed ?? 'unknown'}
          </div>
          <div class={dashboardData.acceptanceReadiness.allPrsMerged === false ? 'text-red-600' : (dashboardData.acceptanceReadiness.allPrsMerged === null ? 'text-yellow-600' : 'text-green-600')}>
            PRs Merged: {dashboardData.acceptanceReadiness.allPrsMerged ?? 'unknown'}
          </div>
          <div class={dashboardData.acceptanceReadiness.githubAccessHealthy === false ? 'text-red-600' : (dashboardData.acceptanceReadiness.githubAccessHealthy === null ? 'text-yellow-600' : 'text-green-600')}>
            GitHub Healthy: {dashboardData.acceptanceReadiness.githubAccessHealthy ?? 'unknown'}
          </div>
        </div>
      </div>

      <!-- Data Sources Status -->
      <div class="p-4 bg-white shadow rounded-lg">
        <h2 class="text-xl font-bold mb-2">Data Sources</h2>
        <ul class="space-y-1">
          {#each Object.entries(dashboardData.dataSourcesStatus) as [source, status]}
            <li class="text-sm flex justify-between">
              <span class="font-medium">{source}:</span>
              <span class="text-yellow-600">{status}</span>
            </li>
          {/each}
        </ul>
      </div>
    </div>

    <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
      <!-- Tasks Section -->
      <div class="p-4 bg-white shadow rounded-lg">
        <h2 class="text-xl font-bold mb-4">Tasks</h2>
        {#if !dashboardData.tasks}
          <p class="text-yellow-600">{dashboardData.dataSourcesStatus.tasks || 'Not available'}</p>
        {:else if dashboardData.tasks.length === 0}
          <p class="text-gray-500">No tasks found.</p>
        {:else}
          <div class="overflow-x-auto">
            <table class="min-w-full text-xs">
              <thead>
                <tr class="border-b">
                  <th class="text-left py-2">Tag</th>
                  <th class="text-left py-2">Status</th>
                  <th class="text-left py-2">Quality Gate</th>
                </tr>
              </thead>
              <tbody>
                {#each dashboardData.tasks as task}
                  <tr class="border-b">
                    <td class="py-2">{task.tag}</td>
                    <td class="py-2">{task.status}</td>
                    <td class="py-2">{task.quality_gate_passed ? '✅' : '❌'}</td>
                  </tr>
                {/each}
              </tbody>
            </table>
          </div>
        {/if}
      </div>

      <!-- Wishlist Section -->
      <div class="p-4 bg-white shadow rounded-lg">
        <h2 class="text-xl font-bold mb-4">Wishlist</h2>
        {#if !dashboardData.wishlist}
          <p class="text-yellow-600">{dashboardData.dataSourcesStatus.wishlist || 'Not available'}</p>
        {:else if dashboardData.wishlist.length === 0}
          <p class="text-gray-500">No wishlist items.</p>
        {:else}
          <ul class="space-y-2 text-sm">
            {#each dashboardData.wishlist as item}
              <li class="p-2 bg-gray-50 rounded">
                <div class="font-medium">{item.content || item.text}</div>
                <div class="text-xs text-gray-500">Source: {item.source} | Status: {item.status}</div>
              </li>
            {/each}
          </ul>
        {/if}
      </div>
    </div>
  {/if}
</div>
