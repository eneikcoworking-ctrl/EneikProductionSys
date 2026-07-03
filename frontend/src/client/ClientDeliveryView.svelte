<script lang="ts">
  import { onMount } from 'svelte';

  export let projectId: string;
  let deliveryData: any = null;
  let loading = true;
  let error: string | null = null;

  async function fetchDelivery() {
    try {
      const baseUrl = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';
      const response = await fetch(`${baseUrl}/api/projects/${projectId}/client-delivery`);
      if (!response.ok) throw new Error('Failed to fetch delivery data');
      deliveryData = await response.json();
    } catch (e: any) {
      error = e.message;
    } finally {
      loading = false;
    }
  }

  async function acceptProject() {
    if (!confirm('Are you sure you want to accept this project? This will stop further generation.')) return;
    try {
      const baseUrl = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';
      const response = await fetch(`${baseUrl}/api/projects/${projectId}/accept`, { method: 'POST' });
      if (!response.ok) throw new Error('Failed to accept project');
      alert('Project accepted successfully!');
      window.location.reload();
    } catch (e: any) {
      alert('Error: ' + e.message);
    }
  }

  onMount(fetchDelivery);
</script>

<div class="space-y-8 max-w-4xl mx-auto p-6">
  <div class="flex justify-between items-center">
    <h1 class="text-3xl font-bold text-gray-900">Client Delivery View</h1>
    <button
      on:click={acceptProject}
      class="px-6 py-2 bg-green-600 text-white rounded-full font-bold hover:bg-green-700 transition"
    >
      Accept Project
    </button>
  </div>

  {#if loading}
    <div class="text-center py-12">Loading delivery artifacts...</div>
  {:else if error}
    <div class="p-4 bg-red-100 text-red-700 rounded-lg">{error}</div>
  {:else if deliveryData}
    <!-- Summary Section -->
    <section class="bg-blue-50 p-6 rounded-xl border border-blue-100">
      <h2 class="text-xl font-bold mb-2 text-blue-800">Quality Summary</h2>
      <p class="text-blue-700">{deliveryData.testSummary}</p>
    </section>

    <div class="grid grid-cols-1 md:grid-cols-2 gap-8">
      <!-- Requested -->
      <section>
        <h2 class="text-xl font-bold mb-4 flex items-center">
          <span class="mr-2">📋</span> Requested (Client Wishlist)
        </h2>
        <ul class="space-y-3">
          {#each deliveryData.requested as item}
            <li class="p-3 bg-white shadow-sm rounded-lg border border-gray-100">
              {item.content || item.text}
            </li>
          {:else}
            <li class="text-gray-500">No client requests found.</li>
          {/each}
        </ul>
      </section>

      <!-- Delivered -->
      <section>
        <h2 class="text-xl font-bold mb-4 flex items-center">
          <span class="mr-2">✅</span> Delivered Features
        </h2>
        <ul class="space-y-3">
          {#each deliveryData.delivered as task}
            <li class="p-3 bg-white shadow-sm rounded-lg border border-green-100">
              <div class="font-medium">{task.description}</div>
              {#if task.payload && task.payload.definitionOfDone}
                <div class="text-xs text-gray-500 mt-1">DoD: {task.payload.definitionOfDone}</div>
              {/if}
            </li>
          {:else}
            <li class="text-gray-500">No completed tasks yet.</li>
          {/each}
        </ul>
      </section>
    </div>

    <!-- Links and Screenshots -->
    <div class="grid grid-cols-1 md:grid-cols-2 gap-8">
      <section>
        <h2 class="text-xl font-bold mb-4">Pull Requests</h2>
        <ul class="space-y-2">
          {#each deliveryData.prLinks as link}
            <li>
              <a href={link} target="_blank" class="text-blue-600 hover:underline break-all">{link}</a>
            </li>
          {:else}
            <li class="text-gray-500">No PR links available.</li>
          {/each}
        </ul>
      </section>

      <section>
        <h2 class="text-xl font-bold mb-4">Screenshots</h2>
        <div class="grid grid-cols-2 gap-4">
          {#each deliveryData.screenshots as url}
            <a href={url} target="_blank" class="block border rounded overflow-hidden hover:opacity-75 transition">
              <img src={url} alt="Screenshot" class="w-full h-32 object-cover" />
            </a>
          {:else}
            <p class="text-gray-500 col-span-2">No screenshots available.</p>
          {/each}
        </div>
      </section>
    </div>
  {/if}
</div>
