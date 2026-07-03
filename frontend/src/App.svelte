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
  let wishText = '';
  let status = 'Ready';
  let activeView: 'main' | 'command' | 'client' | 'admin' = 'main';

  async function loadProjects() {
    const response = await fetch(`${API_BASE}/api/projects`);
    if (response.ok) {
      projects = await response.json();
      if (!dashboard && projects.length > 0) {
        await loadDashboard(projects[0].id);
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

  async function addWish() {
    if (!dashboard || !wishText.trim()) return;
    status = 'Adding client wishlist item...';
    const response = await fetch(`${API_BASE}/api/projects/${dashboard.project.id}/wishlist`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ text: wishText, type: 'client_wish' })
    });
    if (response.ok) {
      wishText = '';
      await loadDashboard(dashboard.project.id);
      status = 'Wishlist saved. It is not a task yet.';
    }
  }

  async function orchestrate() {
    if (!dashboard) return;
    status = 'Technical Lead is turning wishes into business-necessary tasks...';
    const response = await fetch(`${API_BASE}/api/projects/${dashboard.project.id}/orchestrate`, {
      method: 'POST'
    });
    if (response.ok) {
      const result = await response.json();
      await loadDashboard(dashboard.project.id);
      status = `${result.message} Created: ${result.createdTasks.length}`;
    }
  }

  async function acceptProject() {
    if (!dashboard) return;
    status = 'Accepting project and stopping new work...';
    const response = await fetch(`${API_BASE}/api/projects/${dashboard.project.id}/accept`, {
      method: 'POST'
    });
    if (response.ok) {
      await loadDashboard(dashboard.project.id);
      await loadProjects();
      status = 'Project accepted. New orchestration is stopped.';
    }
  }

  onMount(loadProjects);
</script>

<main class="shell">
  <section class="topbar">
    <div>
      <p class="eyebrow">Eneik Production System</p>
      <h1>Project Command Center</h1>
    </div>
    <div class="nav-links" style="margin-left: 20px; display: flex; gap: 10px;">
        <button on:click={() => activeView = 'main'} class:active={activeView === 'main'}>Main</button>
        <button on:click={() => activeView = 'command'} class:active={activeView === 'command'}>Command V2</button>
        <button on:click={() => activeView = 'client'} class:active={activeView === 'client'}>Delivery</button>
        <button on:click={() => activeView = 'admin'} class:active={activeView === 'admin'}>Admin</button>
    </div>
    <div class="create-project">
      <input bind:value={projectName} placeholder="New project name" aria-label="New project name" />
      <button on:click={createProject}>Create</button>
    </div>
  </section>

  <section class="project-strip">
    {#each projects as project}
      <button
        class:active={dashboard?.project.id === project.id}
        on:click={() => loadDashboard(project.id)}
      >
        <strong>{project.name}</strong>
        <span>{project.status}</span>
      </button>
    {/each}
  </section>

  {#if activeView === 'admin'}
    <AdminDashboard />
  {:else if dashboard}
    {#if activeView === 'main'}
        <section class="summary">
          <div>
            <p class="label">Active Project</p>
            <h2>{dashboard.project.name}</h2>
            <p>Factory: {dashboard.project.factoryStatus || 'pending'}</p>
            <p>{dashboard.project.githubRepositoryStatus || 'GitHub pending'}</p>
            <p>{dashboard.project.linearProjectStatus || 'Linear pending'}</p>
            {#if dashboard.project.workspacePath}
              <p>{dashboard.project.workspacePath}</p>
            {/if}
            <p>{dashboard.project.repositoryName} · {dashboard.project.linearProjectKey}</p>
          </div>
          <div class="metric">
            <span>{dashboard.agentCount}</span>
            <p>Jules attached</p>
          </div>
          <div class="metric">
            <span>{dashboard.openWishlistCount}</span>
            <p>open wishes</p>
          </div>
          <div class="metric">
            <span>{dashboard.queue.totalQueued}</span>
            <p>queued tasks</p>
          </div>
          <button class="accept" disabled={dashboard.project.status === 'accepted'} on:click={acceptProject}>
            Project accepted
          </button>
        </section>

        <section class="workspace">
          <div class="panel intake">
            <div class="panel-head">
              <h2>Client Wishlist</h2>
              <button on:click={orchestrate} disabled={dashboard.project.status === 'accepted'}>Orchestrate</button>
            </div>
            <textarea bind:value={wishText} placeholder="Write anything the client wants. This is not a task yet."></textarea>
            <button class="wide" on:click={addWish} disabled={dashboard.project.status === 'accepted'}>Add wish</button>
            <div class="feed">
              {#each dashboard.wishlist as item}
                <article>
                  <span>{item.status}</span>
                  <p>{item.text}</p>
                </article>
              {/each}
            </div>
          </div>

          <div class="panel agents">
            <h2>Jules Energy Pool</h2>
            <div class="agent-grid">
              {#each dashboard.agents as agent}
                <article>
                  <strong>{agent.name}</strong>
                  <span class={agent.status}>{agent.status}</span>
                  <p>{agent.currentRoleTag || 'no role selected'}</p>
                  <small>{agent.currentTaskDescription || 'waiting for business-necessary work'}</small>
                </article>
              {/each}
            </div>
          </div>
        </section>

        <section class="workspace lower">
          <div class="panel">
            <h2>Role Queue</h2>
            <div class="queue">
              {#each dashboard.queue.byTag as item}
                <article>
                  <strong>{item.tag}</strong>
                  <span>{item.count}</span>
                </article>
              {/each}
            </div>
          </div>

          <div class="panel">
            <h2>Project Tasks</h2>
            <div class="tasks">
              {#each dashboard.tasks as task}
                <article>
                  <span>{task.status}</span>
                  <strong>{task.tag}</strong>
                  <p>{task.description}</p>
                  <small>{task.julesDispatchStatus || 'Jules dispatch pending'}</small>
                </article>
              {/each}
            </div>
          </div>
        </section>
    {:else if activeView === 'command'}
        <section class="p-6">
            <CommandDashboardV2 projectId={dashboard.project.id} />
        </section>
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
    .nav-links button {
        background: #eee;
        border: none;
        padding: 5px 15px;
        cursor: pointer;
        border-radius: 4px;
    }
    .nav-links button.active {
        background: #333;
        color: white;
    }
</style>
