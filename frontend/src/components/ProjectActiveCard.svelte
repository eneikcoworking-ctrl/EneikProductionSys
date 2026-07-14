<script lang="ts">
  import { onMount, onDestroy } from 'svelte';
  import { projectsApi } from '../lib/api/projectsApi';
  import type { Project } from '../lib/types';

  export let project: Project;
  export let onAccepted: (project: Project) => void;

  let interval: number;
  let showConfirm = false;
  let accepting = false;
  let acceptConfirmationText = '';

  $: acceptConfirmationMatches = acceptConfirmationText.trim() === project.name;

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
    if (!acceptConfirmationMatches) return;
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
      acceptConfirmationText = '';
    }
  }

  function openConfirm() {
    acceptConfirmationText = '';
    showConfirm = true;
  }

  function closeConfirm() {
    if (accepting) return;
    showConfirm = false;
    acceptConfirmationText = '';
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
    <div class="legacy-final-zone">
      <div>
        <h3>Final Delivery Acceptance</h3>
        <p>Use only after the project is ready to close. Acceptance stops further generation.</p>
      </div>
      <button onclick={openConfirm} class="legacy-final-button">
        Open Final Acceptance
      </button>
    </div>
  {/if}
</div>

{#if showConfirm}
  <div class="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
    <div class="bg-white p-6 rounded-lg max-w-sm w-full shadow-xl">
      <h3 class="text-lg font-bold mb-2">Accept and Close Project</h3>
      <p class="text-gray-600 mb-6">
        This is the final project action. Type the project name to confirm:
      </p>
      <code class="confirm-target">{project.name}</code>
      <input
        class="confirm-input"
        type="text"
        bind:value={acceptConfirmationText}
        placeholder={project.name}
        disabled={accepting}
      />
      <div class="flex gap-4">
        <button
          onclick={closeConfirm}
          class="flex-1 py-2 border border-gray-300 rounded font-medium hover:bg-gray-50"
          disabled={accepting}
        >
          Cancel
        </button>
        <button
          onclick={handleAccept}
          class="flex-1 py-2 bg-red-600 text-white rounded font-medium hover:bg-red-700"
          disabled={accepting || !acceptConfirmationMatches}
        >
          {accepting ? 'Accepting...' : 'Accept and Close'}
        </button>
      </div>
    </div>
  </div>
{/if}

<style>
  .legacy-final-zone {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    border: 1px solid #fecaca;
    border-radius: 8px;
    background: #fff7f7;
    padding: 12px;
  }
  .legacy-final-zone h3 {
    margin: 0 0 4px;
    color: #991b1b;
    font-size: 13px;
    font-weight: 800;
    text-transform: uppercase;
  }
  .legacy-final-zone p {
    margin: 0;
    color: #475569;
    font-size: 12px;
    line-height: 1.4;
  }
  .legacy-final-button {
    border: 1px solid #dc2626;
    border-radius: 8px;
    color: #b91c1c;
    font-weight: 700;
    min-height: 36px;
    padding: 0 14px;
    white-space: nowrap;
  }
  .confirm-target {
    display: inline-block;
    max-width: 100%;
    overflow: hidden;
    text-overflow: ellipsis;
    background: #f1f5f9;
    border-radius: 4px;
    color: #0f172a;
    font-size: 12px;
    margin-bottom: 8px;
    padding: 2px 6px;
    white-space: nowrap;
  }
  .confirm-input {
    width: 100%;
    height: 40px;
    border: 1px solid #cbd5e1;
    border-radius: 8px;
    margin-bottom: 16px;
    padding: 0 12px;
  }
  .confirm-input:focus {
    border-color: #dc2626;
    box-shadow: 0 0 0 3px rgba(220, 38, 38, 0.12);
    outline: none;
  }
</style>
