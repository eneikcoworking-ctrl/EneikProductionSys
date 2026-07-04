<script lang="ts">
  import type { Project } from '../lib/types';
  import { slide } from 'svelte/transition';

  export let projects: Project[] = [];

  let isOpen = false;

  $: history = projects
    .filter((p) => p.status === 'accepted')
    .sort((a, b) => new Date(b.acceptedAt || 0).getTime() - new Date(a.acceptedAt || 0).getTime());
</script>

<div class="mt-8 border-t border-gray-200 pt-4">
  <button
    onclick={() => (isOpen = !isOpen)}
    class="flex items-center gap-2 text-lg font-bold text-gray-700 hover:text-gray-900 transition-colors"
  >
    <span>История проектов</span>
    <span class="transform transition-transform {isOpen ? 'rotate-180' : ''}">▾</span>
  </button>

  {#if isOpen}
    <div transition:slide class="mt-4 space-y-2">
      {#if history.length === 0}
        <p class="text-gray-500 italic">Нет завершенных проектов</p>
      {:else}
        <div class="bg-white shadow overflow-hidden rounded-md">
          <ul class="divide-y divide-gray-200">
            {#each history as project}
              <li class="px-6 py-4 flex justify-between items-center hover:bg-gray-50 transition-colors">
                <div class="font-medium text-gray-900">{project.name}</div>
                <div class="text-sm text-gray-500">
                  Принят: {project.acceptedAt ? new Date(project.acceptedAt).toLocaleDateString() : 'Н/Д'}
                </div>
              </li>
            {/each}
          </ul>
        </div>
      {/if}
    </div>
  {/if}
</div>
