<script lang="ts">
  import { projectsApi } from '../lib/api/projectsApi';
  import type { Project } from '../lib/types';

  export let onCreated: (project: Project) => void;

  let name = '';
  let wishlist = '';
  let showWishlistModal = false;
  let loading = false;
  let error = '';

  function openWishlistModal() {
    if (!name.trim()) return;
    error = '';
    showWishlistModal = true;
  }

  function closeWishlistModal() {
    if (loading) return;
    showWishlistModal = false;
  }

  async function handleCreate() {
    if (!wishlist.trim()) return;
    loading = true;
    error = '';
    try {
      const project = await projectsApi.createProject(name, undefined, wishlist);
      showWishlistModal = false;
      wishlist = '';
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
  <form on:submit|preventDefault={openWishlistModal} class="space-y-4">
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
      />
    </div>
    <button
      type="submit"
      disabled={!name.trim()}
      class="w-full py-2 px-4 bg-accent text-white font-semibold rounded hover:opacity-90 disabled:opacity-50 disabled:cursor-not-allowed transition-opacity"
    >
      Create Project
    </button>
  </form>
</div>

{#if showWishlistModal}
  <div class="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50" role="dialog" aria-modal="true">
    <div class="bg-white shadow-xl rounded-lg max-w-lg w-full mx-4 p-6">
      <h3 class="text-lg font-bold mb-1">First wishlist item for "{name}"</h3>
      <p class="text-sm text-gray-500 mb-4">
        A project can't be created empty - describe the first thing you want built. You can add more later.
      </p>
      <textarea
        bind:value={wishlist}
        rows="8"
        placeholder="Paste the client brief or describe the first feature..."
        class="w-full p-2 border border-gray-300 rounded focus:ring-accent focus:border-accent"
        disabled={loading}
        autofocus
      ></textarea>
      {#if error}
        <p class="mt-2 text-sm text-red-600">{error}</p>
      {/if}
      <div class="mt-4 flex justify-end gap-2">
        <button
          type="button"
          on:click={closeWishlistModal}
          disabled={loading}
          class="py-2 px-4 rounded border border-gray-300 text-gray-700 hover:bg-gray-50 disabled:opacity-50"
        >
          Back
        </button>
        <button
          type="button"
          on:click={handleCreate}
          disabled={!wishlist.trim() || loading}
          class="py-2 px-4 bg-accent text-white font-semibold rounded hover:opacity-90 disabled:opacity-50 disabled:cursor-not-allowed transition-opacity"
        >
          {loading ? 'Creating...' : 'Create Project'}
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
