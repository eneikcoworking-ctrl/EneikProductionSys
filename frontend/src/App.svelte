<script lang="ts">
  import { onMount, onDestroy } from 'svelte';
  import type { ProjectDashboard, ProjectSummary } from './lib/types';
  import CommandDashboardV2 from './dashboard/CommandDashboardV2.svelte';
  import MetricsView from './dashboard/MetricsView.svelte';
  import AdminDashboard from './dashboard/AdminDashboard.svelte';
  import AiResourcesDashboard from './dashboard/AiResourcesDashboard.svelte';

  const API_BASE = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';

  let projects: ProjectSummary[] = [];
  let dashboard: ProjectDashboard | null = null;
  let projectName = '';
  let status = 'Ready';
  let bootLoading = true;
  let loadError = '';
  let activeView: 'dashboard' | 'metrics' | 'resources' | 'admin' = 'dashboard';
  let showOnboardPrompt = false;
  let conflictingProjectName = '';

  async function loadProjects() {
    loadError = '';
    try {
      const response = await fetch(`${API_BASE}/api/projects`);
      if (!response.ok) {
        throw new Error(`Projects API returned ${response.status}`);
      }
      projects = await response.json();
      const currentProjectStillExists = dashboard && projects.some(p => p.id === dashboard?.project.id);
      if ((!dashboard || !currentProjectStillExists) && projects.length > 0) {
        const active = projects.find(p => p.status === 'active');
        if (active) {
          await loadDashboard(active.id);
        } else {
          await loadDashboard(projects[0].id);
        }
      }
    } catch (e: any) {
      loadError = e?.message || 'Failed to load projects';
      status = loadError;
    } finally {
      bootLoading = false;
    }
  }

  async function loadDashboard(projectId: string) {
    loadError = '';
    const response = await fetch(`${API_BASE}/api/projects/${projectId}/dashboard`);
    if (!response.ok) {
      loadError = `Project dashboard API returned ${response.status}`;
      status = loadError;
      return;
    }
    dashboard = await response.json();
  }

  async function createProject(onboardingMode?: string) {
    if (!projectName.trim()) return;
    status = onboardingMode === 'brownfield' ? 'Onboarding existing project...' : 'Creating isolated project workspace...';
    try {
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
    } catch (e: any) {
      status = `Network error while creating project: ${e?.message || e}`;
    }
  }

  onMount(loadProjects);

  // Keep the header's project name/status in sync with CommandDashboardV2's own 10s poll cycle —
  // without this, dashboard.project here goes stale the moment the child dashboard picks up a change.
  let headerRefreshInterval: ReturnType<typeof setInterval> | undefined;
  onMount(() => {
    headerRefreshInterval = setInterval(() => {
      if (dashboard) {
        loadDashboard(dashboard.project.id);
      }
    }, 10000);
  });
  onDestroy(() => {
    if (headerRefreshInterval) clearInterval(headerRefreshInterval);
  });
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
      <button onclick={() => activeView = 'metrics'} class:active={activeView === 'metrics'}>Metrics</button>
      <button onclick={() => activeView = 'resources'} class:active={activeView === 'resources'}>AI Resources</button>
      <button onclick={() => activeView = 'admin'} class:active={activeView === 'admin'}>Token Admin</button>
    </div>

    <div class="create-project">
      <input bind:value={projectName} placeholder="New project name" aria-label="New project name" />
      <button onclick={() => createProject()}>Create</button>
    </div>
  </section>

  <!-- Project Selector (Only Active Project Shown Prominently) -->
  {#if activeView === 'dashboard' && dashboard}
    <section class="active-project-hero">
      <div class="active-project-card">
        <p class="eyebrow">Active Project In Production</p>
        <h2>{dashboard.project.name}</h2>
        <span class="badge {dashboard.project.status}">{dashboard.project.status}</span>
        <code class="path">{dashboard.project.repositoryName || 'no repo'}</code>
      </div>
    </section>
  {/if}

  <!-- View Content Slot -->
  {#if activeView === 'admin'}
    <AdminDashboard />
  {:else if activeView === 'resources'}
    <AiResourcesDashboard />
  {:else if dashboard}
    {#if activeView === 'dashboard'}
      <CommandDashboardV2 projectId={dashboard.project.id} />

      <!-- Collapsed non-active projects at the bottom of Dashboard view -->
      <section class="other-projects-section">
        <details class="collapsed-projects">
          <summary class="toggle-title">Other projects and archive ({projects.filter(p => p.id !== dashboard?.project.id).length})</summary>
          <div class="projects-details-grid">
            {#each projects.filter(p => p.id !== dashboard?.project.id) as project}
              <button class="project-details-item" onclick={() => loadDashboard(project.id)}>
                <strong>{project.name}</strong>
                <span class="badge {project.status}">{project.status}</span>
              </button>
            {:else}
              <p class="empty-state">No other saved projects.</p>
            {/each}
          </div>
        </details>
      </section>
    {:else if activeView === 'metrics'}
      <MetricsView projectId={dashboard.project.id} />
    {/if}
  {:else if bootLoading}
    <section class="empty loading-state" aria-live="polite">
      <div class="loader-spinner"></div>
      <h2>Loading project command center...</h2>
      <p>Fetching the active project and current production state.</p>
    </section>
  {:else if loadError}
    <section class="empty error-state">
      <h2>Frontend cannot reach the project API</h2>
      <p>{loadError}</p>
      <button type="button" onclick={loadProjects}>Retry</button>
    </section>
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
        <p>A repository named <strong>{conflictingProjectName}</strong> already exists on GitHub. Should Eneik onboard and analyze that existing repository?</p>
        <div class="modal-actions">
          <button class="btn btn-primary" onclick={() => createProject('brownfield')}>Yes, analyze it</button>
          <button class="btn btn-secondary" onclick={() => { showOnboardPrompt = false; projectName = ''; status = 'Ready'; }}>No, use another name</button>
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

  .active-project-hero {
    margin-bottom: var(--space-4);
  }
  .active-project-card {
    background: var(--surface);
    border: 1px solid var(--neutral-200);
    border-radius: 12px;
    padding: var(--space-6);
    box-shadow: 0 1px 3px rgba(0,0,0,0.02);
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: var(--space-4);
    min-width: 0;
  }
  .active-project-card h2 {
    font-size: 24px;
    margin: 0;
    color: var(--neutral-800);
  }
  .active-project-card .badge {
    text-transform: uppercase;
    font-weight: 700;
    font-size: 11px;
    padding: 4px 10px;
    border-radius: 12px;
  }
  .active-project-card .badge.active {
    background: #d1fae5;
    color: #065f46;
  }
  .active-project-card .badge.accepted { background: #dbeafe; color: #1e40af; }
  .active-project-card .badge.waiting { background: #fef3c7; color: #92400e; }
  .active-project-card .badge.frozen { background: #fee2e2; color: #b91c1c; }
  .active-project-card .badge.analyzing { background: #eff6ff; color: #1e40af; }
  .active-project-card .badge.archived { background: var(--neutral-200); color: var(--neutral-600); }

  .other-projects-section {
    margin-top: var(--space-8);
  }
  .collapsed-projects {
    background: var(--surface);
    border: 1px solid var(--neutral-200);
    border-radius: 8px;
    padding: var(--space-4);
  }
  .toggle-title {
    font-weight: 700;
    font-size: 15px;
    cursor: pointer;
    color: var(--neutral-600);
    outline: none;
  }
  .projects-details-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
    gap: var(--space-3);
    margin-top: var(--space-4);
  }
  .project-details-item {
    background: var(--neutral-50);
    border: 1px solid var(--neutral-200);
    border-radius: 6px;
    padding: var(--space-3);
    display: flex;
    justify-content: space-between;
    align-items: center;
    text-align: left;
    min-height: 48px;
    cursor: pointer;
    color: var(--neutral-800);
  }
  .project-details-item:hover {
    border-color: var(--primary);
  }
  .project-details-item .badge {
    font-size: 9px;
    padding: 2px 6px;
    border-radius: 4px;
    font-weight: 700;
    text-transform: uppercase;
  }
  .project-details-item .badge.accepted { background: #dbeafe; color: #1e40af; }
  .project-details-item .badge.waiting { background: #fef3c7; color: #92400e; }
  .project-details-item .badge.frozen { background: #fee2e2; color: #b91c1c; }
  .project-details-item .badge.analyzing { background: #eff6ff; color: #1e40af; }
  .project-details-item .badge.archived { background: var(--neutral-200); color: var(--neutral-600); }

  .active-project-card .path {
    max-width: 100%;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .loading-state {
    align-items: center;
    display: flex;
    flex-direction: column;
    gap: var(--space-3);
  }

  .loader-spinner {
    animation: spin 1s linear infinite;
    border: 4px solid var(--neutral-200);
    border-radius: 50%;
    border-top-color: var(--primary);
    height: 40px;
    width: 40px;
  }

  .error-state {
    border-color: #fecaca;
    color: var(--error);
  }

  .error-state p {
    margin: var(--space-3) 0;
  }

  @keyframes spin {
    to {
      transform: rotate(360deg);
    }
  }
</style>
