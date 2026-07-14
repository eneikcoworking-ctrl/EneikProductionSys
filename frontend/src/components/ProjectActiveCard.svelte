<script lang="ts">
  import { onMount, onDestroy } from 'svelte';
  import { projectsApi } from '../lib/api/projectsApi';
  import type { Project } from '../lib/types';

  export let project: Project;
  export let onAccepted: (project: Project) => void;

  let interval: number;
  let showConfirm = false;
  let accepting = false;

  async function pollProject() {
    if (project.status === 'accepted') return;
    try {
      project = await projectsApi.getProject(project.id);
    } catch (e) {
      console.error('Failed to poll project', e);
    }
  }

  onMount(() => {
    interval = window.setInterval(pollProject, 7000);
  });

  onDestroy(() => {
    if (interval) clearInterval(interval);
  });

  async function handleAccept() {
    accepting = true;
    try {
      const updated = await projectsApi.acceptProject(project.id);
      project = updated;
      onAccepted(updated);
    } catch (e) {
      alert('Failed to accept project');
    } finally {
      accepting = false;
      showConfirm = false;
    }
  }
</script>

<div class="p-6 shadow rounded-lg transition-colors {project.status === 'accepted' ? 'bg-gray-100' : 'bg-white'}">
  <div class="flex justify-between items-start mb-6">
    <div>
      <h2 class="text-2xl font-bold {project.status === 'accepted' ? 'text-gray-500' : 'text-gray-900'}">
        {project.name}
      </h2>
      <p class="text-sm text-gray-500">
        Created: {new Date(project.createdAt).toLocaleString()}
      </p>
      <p class="mt-2 font-medium">
        Status:
        <span class="px-2 py-0.5 rounded text-sm {project.status === 'active' ? 'bg-green-100 text-green-800' : 'bg-gray-200 text-gray-600'}">
          {project.status === 'active' ? 'Active' : 'Completed'}
        </span>
      </p>
    </div>
    <div class="text-right">
      <p class="text-lg font-semibold">Connected accounts: {project.accountsCount || 0}</p>
    </div>
  </div>

  <div class="grid grid-cols-3 gap-4 mb-6">
    <div class="p-4 bg-blue-50 rounded text-center">
      <p class="text-sm text-blue-600 font-medium">Queued</p>
      <p class="text-2xl font-bold text-blue-900">{project.tasksQueued || 0}</p>
    </div>
    <div class="p-4 bg-yellow-50 rounded text-center">
      <p class="text-sm text-yellow-600 font-medium">In Progress</p>
      <p class="text-2xl font-bold text-yellow-900">{project.tasksInProgress || 0}</p>
    </div>
    <div class="p-4 bg-green-50 rounded text-center">
      <p class="text-sm text-green-600 font-medium">Done</p>
      <p class="text-2xl font-bold text-green-900">{project.tasksDone || 0}</p>
    </div>
  </div>

  {#if project.status === 'active'}
    <button
      onclick={() => (showConfirm = true)}
      class="w-full py-3 bg-accent text-white font-bold rounded hover:opacity-90 transition-opacity"
    >
      Accept Project
    </button>
  {/if}
</div>

{#if showConfirm}
  <div class="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
    <div class="bg-white p-6 rounded-lg max-w-sm w-full shadow-xl">
      <h3 class="text-lg font-bold mb-2">Confirmation</h3>
      <p class="text-gray-600 mb-6">
        Are you sure? After acceptance, the project will be closed.
      </p>
      <div class="flex gap-4">
        <button
          onclick={() => (showConfirm = false)}
          class="flex-1 py-2 border border-gray-300 rounded font-medium hover:bg-gray-50"
          disabled={accepting}
        >
          Cancel
        </button>
        <button
          onclick={handleAccept}
          class="flex-1 py-2 bg-red-600 text-white rounded font-medium hover:bg-red-700"
          disabled={accepting}
        >
          {accepting ? 'Accepting...' : 'Yes, accept'}
        </button>
      </div>
    </div>
  </div>
{/if}

<style>
  .bg-accent {
    background-color: var(--accent);
  }
</style>
