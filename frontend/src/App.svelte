<script lang="ts">
  import { onMount } from 'svelte';
  import type { ProjectDashboard, ProjectSummary } from './lib/types';
  import CommandDashboardV2 from './dashboard/CommandDashboardV2.svelte';
  import ClientDeliveryView from './client/ClientDeliveryView.svelte';
  import AdminDashboard from './dashboard/AdminDashboard.svelte';

  const API_BASE = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';

  let projects: ProjectSummary[] = [];
  let dashboard: ProjectDashboard | null = null;
  let projectName = '';
  let status = 'Ready';
  let activeView: 'dashboard' | 'client' | 'admin' = 'dashboard';

  async function loadProjects() {
    const response = await fetch(`${API_BASE}/api/projects`);
    if (response.ok) {
      projects = await response.json();
      if (!dashboard && projects.length > 0) {
        const active = projects.find(p => p.status === 'active');
        if (active) {
            await loadDashboard(active.id);
        } else {
            await loadDashboard(projects[0].id);
        }
      }
    }
  }

  async function loadDashboard(projectId: string) {
    const response = await fetch(`${API_BASE}/api/projects/${projectId}/dashboard`);
    if (response.ok) {
      dashboard = await response.json();
    }
  }

  async function createProject() {
    if (!projectName.trim()) return;
    status = 'Creating isolated project workspace...';
    const response = await fetch(`${API_BASE}/api/projects`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name: projectName })
    });
    if (response.ok) {
      const project = await response.json();
      projectName = '';
      await loadProjects();
      await loadDashboard(project.id);
      status = 'Project created. Seven Jules accounts attached.';
    }
  }

  onMount(loadProjects);
</script>

<main class="shell">
  <!-- Top Navigation Bar -->
  <section class="topbar">
    <div class="brand">
      <p class="eyebrow">Eneik Production System</p>
      <h1>Project Command Center</h1>
    </div>
    
    <div class="nav-links">
      <button onclick={() => activeView = 'dashboard'} class:active={activeView === 'dashboard'}>Dashboard</button>
      <button onclick={() => activeView = 'client'} class:active={activeView === 'client'}>Delivery</button>
      <button onclick={() => activeView = 'admin'} class:active={activeView === 'admin'}>Admin</button>
    </div>

    <div class="create-project">
      <input bind:value={projectName} placeholder="New project name" aria-label="New project name" />
      <button onclick={createProject}>Create</button>
    </div>
  </section>

  <!-- Project Selector Strip -->
  {#if activeView !== 'admin'}
    <section class="project-strip">
      {#each projects.filter(p => ['active', 'waiting', 'accepted'].includes(p.status)) as project}
        <button
          class:active={dashboard?.project.id === project.id}
          onclick={() => loadDashboard(project.id)}
        >
          <strong>{project.name}</strong>
          <span class="badge {project.status}">{project.status}</span>
        </button>
      {/each}
    </section>

    <!-- Archive & Frozen filters -->
    <section class="project-filters">
      {#if projects.some(p => p.status === 'frozen')}
        <div class="filter-group">
          <p class="label">Frozen Projects</p>
          <select onchange={(e) => loadDashboard(e.currentTarget.value)}>
            <option value="" disabled selected={!projects.filter(p => p.status === 'frozen').some(p => p.id === dashboard?.project.id)}>
              Select from frozen...
            </option>
            {#each projects.filter(p => p.status === 'frozen') as project}
              <option value={project.id} selected={dashboard?.project.id === project.id}>
                {project.name}
              </option>
            {/each}
          </select>
        </div>
      {/if}

      {#if projects.some(p => p.status === 'archived')}
        <div class="filter-group">
          <p class="label">Archive</p>
          <select onchange={(e) => loadDashboard(e.currentTarget.value)}>
            <option value="" disabled selected={!projects.filter(p => p.status === 'archived').some(p => p.id === dashboard?.project.id)}>
              Select from archive...
            </option>
            {#each projects.filter(p => p.status === 'archived') as project}
              <option value={project.id} selected={dashboard?.project.id === project.id}>
                {project.name}
              </option>
            {/each}
          </select>
        </div>
      {/if}
    </section>
  {/if}

  <!-- View Content Slot -->
  {#if activeView === 'admin'}
    <AdminDashboard />
  {:else if dashboard}
    {#if activeView === 'dashboard'}
      <CommandDashboardV2 projectId={dashboard.project.id} />
    {:else if activeView === 'client'}
      <section class="p-6">
        <ClientDeliveryView projectId={dashboard.project.id} />
      </section>
    {/if}
  {:else}
    <section class="empty">
      <h2>Create a project to start</h2>
      <p>The system will isolate the project, attach seven Jules accounts, and wait for client wishlist input.</p>
    </section>
  {/if}

  <p class="status">{status}</p>
</main>

<style>
  .project-filters {
    display: flex;
    gap: var(--space-6);
    margin-bottom: var(--space-6);
    padding: 0 4px;
  }
  .filter-group {
    display: flex;
    flex-direction: column;
    gap: var(--space-1);
  }
  .filter-group select {
    padding: 8px 12px;
    border-radius: 6px;
    border: 1px solid var(--neutral-300);
    background: white;
    min-width: 240px;
    outline: none;
  }

  .nav-links {
    display: flex;
    gap: var(--space-2);
  }
  .nav-links button {
    background: var(--neutral-100);
    color: var(--neutral-700);
    border: none;
    padding: 6px 16px;
    cursor: pointer;
    border-radius: 8px;
    font-weight: 600;
    transition: all 0.2s;
  }
  .nav-links button.active {
    background: var(--neutral-800);
    color: white;
  }
  .nav-links button:hover:not(.active) {
    background: var(--neutral-200);
  }

  .project-strip button .badge {
    font-size: 9px;
    padding: 2px 6px;
    border-radius: 4px;
    font-weight: 700;
    text-transform: uppercase;
  }
  .project-strip button .badge.active {
    background: #d1fae5;
    color: #065f46;
  }
  .project-strip button .badge.accepted {
    background: #dbeafe;
    color: #1e40af;
  }
  .project-strip button .badge.waiting {
    background: #fef3c7;
    color: #92400e;
  }
</style>
