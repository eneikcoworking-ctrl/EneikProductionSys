<script lang="ts">
  import { onMount, onDestroy } from 'svelte';
  import type { ProjectDashboard, ProjectSummary } from './lib/types';
  import ProductTree from './dashboard/ProductTree.svelte';
  import MetricsView from './dashboard/MetricsView.svelte';
  import AdminDashboard from './dashboard/AdminDashboard.svelte';
  import AiResourcesDashboard from './dashboard/AiResourcesDashboard.svelte';
  // 2026-08-10: TocSentinelView/SixSigmaKaizenPanel retired in favor of EngineForge (Кузница) - same
  // real data, three strictly isolated rooms (Factory/Delivery/Product) instead of two flat jargon
  // tables. See frontend/src/dashboard/forge/ for the room components.
  import EngineForge from './dashboard/forge/EngineForge.svelte';
  import ToastContainer from './components/ToastContainer.svelte';
  import { API_BASE } from './lib/api';

  let projects: ProjectSummary[] = [];
  let dashboard: ProjectDashboard | null = null;
  let projectName = '';
  let status = 'Ready';
  let bootLoading = true;
  let loadError = '';
  // 'dashboard' is the primary living-tree view (default). The other five are internal diagnostics,
  // gated behind the engineering-mode toggle - not co-equal primary navigation (2026-08-02 redesign:
  // the operator does not want internal plumbing competing for attention with real product progress).
  let activeView: 'dashboard' | 'forge' | 'metrics' | 'resources' | 'admin' = 'dashboard';
  let engineeringOpen = false;
  let showOnboardPrompt = false;
  let conflictingProjectName = '';
  let showWishlistPrompt = false;
  let pendingOnboardingMode = 'greenfield';
  let initialWishlist = '';



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

  function promptForWishlist(onboardingMode: string) {
    if (!projectName.trim()) return;
    pendingOnboardingMode = onboardingMode;
    showOnboardPrompt = false;
    showWishlistPrompt = true;
  }

  function cancelWishlistPrompt() {
    showWishlistPrompt = false;
    initialWishlist = '';
  }

  async function createProject() {
    if (!projectName.trim() || !initialWishlist.trim()) return;
    const onboardingMode = pendingOnboardingMode;
    status = onboardingMode === 'brownfield' ? 'Onboarding existing project...' : 'Creating isolated project workspace...';
    try {
      const response = await fetch(`${API_BASE}/api/projects`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name: projectName, onboardingMode, initialWishlist })
      });
      if (response.ok) {
        const project = await response.json();
        projectName = '';
        initialWishlist = '';
        showOnboardPrompt = false;
        showWishlistPrompt = false;
        await loadProjects();
        await loadDashboard(project.id);
        status = onboardingMode === 'brownfield' ? 'Project onboarded. Analyzing Stack and Architecture.' : 'Project created. Seven Jules accounts attached.';
      } else if (response.status === 409) {
        const err = await response.json();
        if (err.error === 'name_conflict') {
          conflictingProjectName = projectName;
          showWishlistPrompt = false;
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

  // Keep the header's project name/status in sync with ProductTree's own 10s poll cycle —
  // without this, dashboard.project here goes stale the moment the child view picks up a change.
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
      <p class="eyebrow">Autonomous Software Factory</p>
      <h1>Eneik Management System</h1>
    </div>
    
    <div class="nav-links" role="tablist" aria-label="Main Navigation">
      <button
        role="tab"
        aria-selected={activeView === 'dashboard' && !engineeringOpen}
        onclick={() => { activeView = 'dashboard'; engineeringOpen = false; }}
        class:active={activeView === 'dashboard' && !engineeringOpen}
      >Project</button>
      <button
        class="engineering-toggle"
        aria-pressed={engineeringOpen}
        aria-label="Engineering mode"
        title="Engineering mode: raw data for diagnostics"
        onclick={() => (engineeringOpen = !engineeringOpen)}
        class:active={engineeringOpen}
      >⚙</button>
    </div>

    <div class="create-project">
      <input bind:value={projectName} placeholder="New project name" aria-label="New project name" />
      <button onclick={() => promptForWishlist('greenfield')}>Create</button>
    </div>
  </section>

  {#if engineeringOpen}
    <section class="engineering-bar">
      <div class="nav-links secondary" role="tablist" aria-label="Engineering Navigation">
        <button role="tab" aria-selected={activeView === 'forge'} onclick={() => activeView = 'forge'} class:active={activeView === 'forge'}>The Forge</button>
        <button role="tab" aria-selected={activeView === 'metrics'} onclick={() => activeView = 'metrics'} class:active={activeView === 'metrics'}>Metrics</button>
        <button role="tab" aria-selected={activeView === 'resources'} onclick={() => activeView = 'resources'} class:active={activeView === 'resources'}>Resources &amp; Tokens</button>
        <button role="tab" aria-selected={activeView === 'admin'} onclick={() => activeView = 'admin'} class:active={activeView === 'admin'}>System</button>
      </div>
    </section>
  {/if}

  <!-- View Content Slot -->
  {#if activeView === 'admin'}
    <AdminDashboard />
  {:else if activeView === 'resources'}
    <AiResourcesDashboard />
  {:else if activeView === 'forge' && dashboard}
    <EngineForge projectId={dashboard.project.id} />
  {:else if activeView === 'metrics' && dashboard}
    <MetricsView projectId={dashboard.project.id} />
  {:else if dashboard}
    <ProductTree projectId={dashboard.project.id} />

    <!-- Collapsed non-active projects at the bottom of the tree view -->
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
          <button class="btn btn-primary" onclick={() => promptForWishlist('brownfield')}>Yes, analyze it</button>
          <button class="btn btn-secondary" onclick={() => { showOnboardPrompt = false; projectName = ''; status = 'Ready'; }}>No, use another name</button>
        </div>
      </div>
    </div>
  {/if}

  {#if showWishlistPrompt}
    <div class="modal-backdrop">
      <div class="modal-content wishlist-modal">
        <h3>First wishlist item for "{projectName}"</h3>
        <p>A project can't be created empty - describe the first thing you want built. You can add more later.</p>
        <textarea
          bind:value={initialWishlist}
          rows="8"
          placeholder="Paste the client brief or describe the first feature..."
          class="wishlist-textarea"
          autofocus
        ></textarea>
        <div class="modal-actions">
          <button class="btn btn-primary" disabled={!initialWishlist.trim()} onclick={createProject}>Create Project</button>
          <button class="btn btn-secondary" onclick={cancelWishlistPrompt}>Back</button>
        </div>
      </div>
    </div>
  {/if}

  <p class="status">{status}</p>
</main>

<ToastContainer />

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
  .wishlist-modal {
    max-width: 640px;
  }
  .wishlist-textarea {
    width: 100%;
    box-sizing: border-box;
    padding: 10px 12px;
    border: 1px solid #cbd5e1;
    border-radius: 8px;
    font: inherit;
    resize: vertical;
    margin-bottom: 24px;
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

  .engineering-toggle {
    font-size: 16px;
    padding: 6px 12px;
  }

  .engineering-bar {
    margin-top: var(--space-2);
  }

  .nav-links.secondary {
    background: var(--surface);
    border: 1px solid var(--neutral-200);
    border-radius: var(--radius);
    padding: var(--space-2);
  }

  .nav-links.secondary button {
    background: transparent;
    color: var(--neutral-600);
    font-size: 13px;
  }

  .nav-links.secondary button.active {
    background: var(--neutral-700);
    color: white;
  }

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
