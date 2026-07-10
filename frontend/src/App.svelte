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
  let showOnboardPrompt = false;
  let conflictingProjectName = '';

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

  async function createProject(onboardingMode?: string) {
    if (!projectName.trim()) return;
    status = onboardingMode === 'brownfield' ? 'Onboarding existing project...' : 'Creating isolated project workspace...';
    const response = await fetch(`${API_BASE}/api/projects`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name: projectName, onboardingMode: onboardingMode || 'greenfield' })
    });
    if (response.ok) {
      const project = await response.json();
      projectName = '';
      showOnboardPrompt = false;
      await loadProjects();
      await loadDashboard(project.id);
      status = onboardingMode === 'brownfield' ? 'Project onboarded. Analyzing Stack and Architecture.' : 'Project created. Seven Jules accounts attached.';
    } else if (response.status === 409) {
      const err = await response.json();
      if (err.error === 'name_conflict') {
        conflictingProjectName = projectName;
        showOnboardPrompt = true;
        status = 'Name conflict detected on GitHub.';
      } else {
        status = 'Error: ' + err.message;
      }
    } else {
      const err = await response.json();
      status = 'Error: ' + (err?.error || 'Failed to create project');
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
      <button onclick={() => createProject()}>Create</button>
    </div>
  </section>

  <!-- Project Selector Strip -->
  {#if activeView !== 'admin'}
    <section class="project-strip">
      {#each projects.filter(p => ['active', 'waiting', 'accepted', 'analyzing'].includes(p.status)) as project}
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

  {#if showOnboardPrompt}
    <div class="modal-backdrop">
      <div class="modal-content">
        <h3>Repository Onboarding</h3>
        <p>Репозиторий с именем <strong>{conflictingProjectName}</strong> уже существует на GitHub. Это существующий репозиторий, который нужно проанализировать?</p>
        <div class="modal-actions">
          <button class="btn btn-primary" onclick={() => createProject('brownfield')}>Да, анализировать</button>
          <button class="btn btn-secondary" onclick={() => { showOnboardPrompt = false; projectName = ''; status = 'Ready'; }}>Нет, использовать другое имя</button>
        </div>
      </div>
    </div>
  {/if}

  <p class="status">{status}</p>
</main>

<style>
  .modal-backdrop {
    position: fixed;
    top: 0;
    left: 0;
    width: 100vw;
    height: 100vh;
    background: rgba(15, 23, 42, 0.6);
    display: flex;
    align-items: center;
    justify-content: center;
    z-index: 9999;
  }
  .modal-content {
    background: white;
    border-radius: 12px;
    padding: 24px;
    max-width: 480px;
    width: 100%;
    box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 10px 10px -5px rgba(0, 0, 0, 0.04);
    border: 1px solid #e2e8f0;
  }
  .modal-content h3 {
    margin-top: 0;
    font-size: 1.25rem;
    font-weight: 700;
    color: #0f172a;
    margin-bottom: 12px;
  }
  .modal-content p {
    color: #475569;
    font-size: 0.95rem;
    line-height: 1.5;
    margin-bottom: 24px;
  }
  .modal-actions {
    display: flex;
    justify-content: flex-end;
    gap: 12px;
  }
  .modal-actions button {
    padding: 8px 16px;
    border-radius: 6px;
    font-weight: 600;
    cursor: pointer;
    border: none;
  }
  .modal-actions .btn-primary {
    background: #1e293b;
    color: white;
  }
  .modal-actions .btn-secondary {
    background: #f1f5f9;
    color: #475569;
  }
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
