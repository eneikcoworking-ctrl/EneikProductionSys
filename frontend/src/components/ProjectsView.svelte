<script lang="ts">
  import { onMount } from 'svelte';
  import { projectsApi } from '../lib/api/projectsApi';
  import type { Project } from '../lib/types';
  import ProjectCreateForm from './ProjectCreateForm.svelte';
  import ProjectActiveCard from './ProjectActiveCard.svelte';
  import ProjectHistoryList from './ProjectHistoryList.svelte';

  let projects: Project[] = [];
  let loading = true;

  onMount(async () => {
    try {
      projects = await projectsApi.getProjects();
    } catch (e) {
      console.error('Failed to fetch projects', e);
    } finally {
      loading = false;
    }
  });

  $: activeProject = projects.find((p) => p.status === 'active');

  function handleCreated(newProject: Project) {
    projects = [newProject, ...projects];
  }

  function handleAccepted(updatedProject: Project) {
    projects = projects.map((p) => (p.id === updatedProject.id ? updatedProject : p));
  }
</script>

<div class="max-w-4xl mx-auto space-y-8">
  {#if loading}
    <div class="text-center py-10">
      <p class="text-gray-500 animate-pulse">Loading projects...</p>
    </div>
  {:else}
    {#if !activeProject}
      <ProjectCreateForm onCreated={handleCreated} />
    {:else}
      <ProjectActiveCard project={activeProject} onAccepted={handleAccepted} />
    {/if}

    <ProjectHistoryList {projects} />
  {/if}
</div>
