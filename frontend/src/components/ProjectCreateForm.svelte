<script lang="ts">
  import { projectsApi } from '../lib/api/projectsApi';
  import type { Project } from '../lib/types';

  export let onCreated: (project: Project) => void;

  let name = '';
  let loading = false;
  let error = '';

  async function handleSubmit() {
    if (!name.trim()) return;
    loading = true;
    error = '';
    try {
      const project = await projectsApi.createProject(name);
      onCreated(project);
    } catch (e: any) {
      error = e.message || 'Failed to create project';
    } finally {
      loading = false;
    }
  }
</script>

<div class="p-6 bg-white shadow rounded-lg max-w-md mx-auto">
  <h2 class="text-xl font-bold mb-4">Create Project</h2>
  <form on:submit|preventDefault={handleSubmit} class="space-y-4">
    <div>
      <label for="project-name" class="block text-sm font-medium text-gray-700 mb-1">
        Project Name
      </label>
      <input
        id="project-name"
        type="text"
        bind:value={name}
        placeholder="Enter project name..."
        class="w-full p-2 border border-gray-300 rounded focus:ring-accent focus:border-accent"
        disabled={loading}
      />
      {#if error}
        <p class="mt-1 text-sm text-red-600">{error}</p>
      {/if}
    </div>
    <button
      type="submit"
      disabled={!name.trim() || loading}
      class="w-full py-2 px-4 bg-accent text-white font-semibold rounded hover:opacity-90 disabled:opacity-50 disabled:cursor-not-allowed transition-opacity"
    >
      {loading ? 'Creating...' : 'Create Project'}
    </button>
  </form>
</div>

<style>
  .bg-accent {
    background-color: var(--accent);
  }
</style>
