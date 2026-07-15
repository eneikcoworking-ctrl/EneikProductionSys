<script lang="ts">
  import { onMount } from 'svelte';

  export let projectId: string;

  const API_BASE = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';

  let dashboard: any = null;
  let commandDashboard: any = null;
  let loading = true;
  let error: string | null = null;
  let wishText = '';
  let statusMsg = 'Ready';
  let orchestrating = false;
  let orchestrateCooldownUntil = 0;
  let nowMs = Date.now();
  let loadedProjectId = '';
  let showAcceptConfirm = false;
  let acceptConfirmationText = '';
  let acceptingProject = false;

  const ORCHESTRATE_COOLDOWN_SECONDS = 300;

  $: orchestrateRemainingSeconds = Math.max(0, Math.ceil((orchestrateCooldownUntil - nowMs) / 1000));
  $: orchestrateBlocked = dashboard?.project?.status === 'accepted' || orchestrating || orchestrateRemainingSeconds > 0;
  $: acceptConfirmationTarget = dashboard?.project?.name ?? '';
  $: acceptConfirmationMatches = acceptConfirmationText.trim() === acceptConfirmationTarget;

  let onboardingFindings: any[] = [];
  let onboardingReport = '';

  function orchestrateCooldownKey() {
    return `eneik-orchestrate-cooldown:${projectId}`;
  }

  function loadOrchestrateCooldown() {
    if (!projectId) return;
    const raw = localStorage.getItem(orchestrateCooldownKey());
    const parsed = raw ? Number(raw) : 0;
    orchestrateCooldownUntil = Number.isFinite(parsed) ? parsed : 0;
    nowMs = Date.now();
    if (orchestrateCooldownUntil <= nowMs) {
      localStorage.removeItem(orchestrateCooldownKey());
    }
  }

  function startOrchestrateCooldown(seconds = ORCHESTRATE_COOLDOWN_SECONDS) {
    orchestrateCooldownUntil = Date.now() + Math.max(1, seconds) * 1000;
    localStorage.setItem(orchestrateCooldownKey(), String(orchestrateCooldownUntil));
    nowMs = Date.now();
  }

  function clearOrchestrateCooldown() {
    orchestrateCooldownUntil = 0;
    localStorage.removeItem(orchestrateCooldownKey());
    nowMs = Date.now();
  }

  async function fetchOnboardingData() {
    try {
      const [resFindings, resReport] = await Promise.all([
        fetch(`${API_BASE}/api/projects/${projectId}/onboarding-findings`),
        fetch(`${API_BASE}/api/projects/${projectId}/onboarding-report`)
      ]);
      if (resFindings.ok) {
        onboardingFindings = await resFindings.json();
      }
      if (resReport.ok) {
        const reportData = await resReport.json();
        onboardingReport = reportData.report;
      }
    } catch (e) {
      console.error("Failed to fetch onboarding data:", e);
    }
  }

  async function addFindingToWishlist(finding: any) {
    statusMsg = 'Adding finding to wishlist...';
    const response = await fetch(`${API_BASE}/api/wishlist`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        projectId: projectId,
        source: 'onboarding_finding',
        sourceRoleTag: finding.roleTag,
        content: `Onboarding Finding (${finding.severity}): ${finding.findingText} [File: ${finding.filePath}]`
      })
    });
    if (response.ok) {
      statusMsg = `Added ${finding.roleTag} finding to wishlist successfully.`;
      await fetchDashboard();
    } else {
      const err = await response.json();
      statusMsg = 'Failed to add finding to wishlist: ' + (err.message || 'Error');
    }
  }

  async function fetchDashboard() {
    try {
      // Fetch both APIs in parallel
      const [res1, res2] = await Promise.all([
        fetch(`${API_BASE}/api/projects/${projectId}/dashboard`),
        fetch(`${API_BASE}/api/projects/${projectId}/command-dashboard`)
      ]);

      if (!res1.ok || !res2.ok) {
        throw new Error('Failed to retrieve project dashboard data');
      }

      dashboard = await res1.json();
      commandDashboard = await res2.json();

      if (dashboard.project.status === 'analyzing') {
        await fetchOnboardingData();
      }
    } catch (e: any) {
      error = e.message;
    } finally {
      loading = false;
    }
  }

  async function addWish() {
    if (!wishText.trim()) return;
    statusMsg = 'Adding client wishlist item...';
    const response = await fetch(`${API_BASE}/api/projects/${projectId}/wishlist`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        projectId: projectId,
        source: 'client',
        content: wishText
      })
    });
    if (response.ok) {
      wishText = '';
      await fetchDashboard();
      statusMsg = 'Wishlist saved. It is not a task yet.';
    } else {
      statusMsg = 'Failed to add wish.';
    }
  }

  async function orchestrate() {
    if (orchestrateBlocked) return;
    orchestrating = true;
    startOrchestrateCooldown();
    statusMsg = 'Technical Lead is turning wishes into business-necessary tasks...';
    try {
      const response = await fetch(`${API_BASE}/api/projects/${projectId}/orchestrate`, {
        method: 'POST'
      });
      if (response.ok) {
        const result = await response.json();
        await fetchDashboard();
        statusMsg = `${result.message} Created: ${result.createdTasks?.length ?? 0}`;
      } else if (response.status === 429) {
        const err = await response.json();
        startOrchestrateCooldown(err.retryAfterSeconds ?? ORCHESTRATE_COOLDOWN_SECONDS);
        statusMsg = `Orchestrate cooldown: retry in ${err.retryAfterSeconds ?? ORCHESTRATE_COOLDOWN_SECONDS}s.`;
      } else {
        clearOrchestrateCooldown();
        statusMsg = 'Failed to orchestrate tasks.';
      }
    } catch (e) {
      clearOrchestrateCooldown();
      statusMsg = 'Failed to orchestrate tasks.';
    } finally {
      orchestrating = false;
    }
  }

  function openAcceptConfirmation() {
    acceptConfirmationText = '';
    showAcceptConfirm = true;
  }

  function closeAcceptConfirmation() {
    if (acceptingProject) return;
    showAcceptConfirm = false;
    acceptConfirmationText = '';
  }

  async function acceptProject() {
    if (!acceptConfirmationMatches || dashboard?.project?.status === 'accepted') return;
    acceptingProject = true;
    statusMsg = 'Accepting project and stopping new work...';
    try {
      const response = await fetch(`${API_BASE}/api/projects/${projectId}/accept`, {
        method: 'POST'
      });
      if (response.ok) {
        await fetchDashboard();
        statusMsg = 'Project accepted. New orchestration is stopped.';
        closeAcceptConfirmation();
      } else {
        statusMsg = 'Failed to accept project.';
      }
    } catch (e) {
      statusMsg = 'Failed to accept project.';
    } finally {
      acceptingProject = false;
    }
  }

  async function activateProject() {
    statusMsg = 'Activating project...';
    const response = await fetch(`${API_BASE}/api/projects/${projectId}/activate`, {
      method: 'POST'
    });
    if (response.ok) {
      await fetchDashboard();
      statusMsg = 'Project activated.';
    } else {
      statusMsg = 'Failed to activate project.';
    }
  }

  // Refetch when projectId changes
  $: if (projectId) {
    if (projectId !== loadedProjectId) {
      loadedProjectId = projectId;
      loading = true;
      error = null;
      loadOrchestrateCooldown();
      fetchDashboard();
    }
  }

  function getKanoCategory(tag: string): { label: string, colorClass: string } {
    const normalizedTag = (tag || '').toUpperCase();
    if (normalizedTag.includes('TAG-00') || normalizedTag.includes('TAG-05') || normalizedTag.includes('TAG-07')) {
      return { label: 'Must-Be', colorClass: 'kano-must' };
    }
    if (normalizedTag.includes('TAG-02') || normalizedTag.includes('TAG-06') || normalizedTag.includes('TAG-08')) {
      return { label: 'Linear', colorClass: 'kano-linear' };
    }
    if (normalizedTag.includes('TAG-03') || normalizedTag.includes('TAG-11')) {
      return { label: 'Attractive', colorClass: 'kano-attractive' };
    }
    return { label: 'Indifferent', colorClass: 'kano-indifferent' };
  }

  function percent(value: number | undefined | null): string {
    return `${Math.round((value ?? 0) * 100)}%`;
  }

  function score(value: number | undefined | null): string {
    return `${Math.round(value ?? 0)}`;
  }

  function width(value: number | undefined | null): string {
    const clamped = Math.max(0, Math.min(100, Math.round((value ?? 0) * 100)));
    return `${clamped}%`;
  }

  function scoreWidth(value: number | undefined | null): string {
    const clamped = Math.max(0, Math.min(100, Math.round(value ?? 0)));
    return `${clamped}%`;
  }

  function stanceLabel(stance: string): string {
    return (stance || 'unknown').replace(/_/g, ' ');
  }

  function pressureLabel(value: string): string {
    return (value || 'none').replace(/_/g, '-');
  }

  function compactNumber(value: number | undefined | null): string {
    const n = value ?? 0;
    if (n >= 1000000) return `${(n / 1000000).toFixed(1)}M`;
    if (n >= 1000) return `${Math.round(n / 100) / 10}k`;
    return `${Math.round(n)}`;
  }

  onMount(() => {
    loadOrchestrateCooldown();
    const timer = setInterval(() => {
      nowMs = Date.now();
      if (orchestrateCooldownUntil > 0 && orchestrateCooldownUntil <= nowMs) {
        clearOrchestrateCooldown();
      }
    }, 1000);
    if (projectId && projectId !== loadedProjectId) {
      loadedProjectId = projectId;
      fetchDashboard();
    }
    return () => clearInterval(timer);
  });
</script>

<div class="dashboard-root">
  {#if loading}
    <div class="loader-container">
      <div class="loader-spinner"></div>
      <p>Synchronizing project pipeline...</p>
    </div>
  {:else if error}
    <div class="banner error">
      <p>Warning: {error}</p>
    </div>
  {:else if dashboard && commandDashboard}
    <!-- Project Banner if frozen -->
    {#if dashboard.project.status === 'frozen'}
      <div class="banner warning">
        <p>Project <strong>{dashboard.project.name}</strong> is frozen. Orchestration and task compilation are paused.</p>
        <button onclick={activateProject}>Activate Project</button>
      </div>
    {/if}

    <!-- Header Section -->
    <header class="project-header">
      <div class="title-area">
        <div class="name-status">
          <h1>{dashboard.project.name}</h1>
          <span class="badge {dashboard.project.status}">{dashboard.project.status}</span>
        </div>
        <p class="meta-line">
          {dashboard.project.repositoryName} · Key: {dashboard.project.linearProjectKey} 
          {#if dashboard.project.workspacePath}
            · <code class="path">{dashboard.project.workspacePath}</code>
          {/if}
        </p>
      </div>
      <div class="actions-area">
        <button class="btn btn-secondary" onclick={orchestrate} disabled={orchestrateBlocked}>
          {#if orchestrateRemainingSeconds > 0}
            Orchestrate ({orchestrateRemainingSeconds}s)
          {:else if orchestrating}
            Orchestrating...
          {:else}
            Orchestrate
          {/if}
        </button>
      </div>
    </header>

    {#if dashboard.emsMetrics}
      <section class="ems-panel">
        <div class="ems-summary">
          <div>
            <span class="label-xs">EMS weighted progress</span>
            <strong>{percent(dashboard.emsMetrics.flowChart.weightedProgress)}</strong>
          </div>
          <div>
            <span class="label-xs">Graph coverage</span>
            <strong>{percent(dashboard.emsMetrics.graphHealth.graphCoverage)}</strong>
          </div>
          <div>
            <span class="label-xs">Dependency coverage</span>
            <strong>{percent(dashboard.emsMetrics.graphHealth.dependencyCoverage)}</strong>
          </div>
          <div>
            <span class="label-xs">Open defect work</span>
            <strong>{dashboard.emsMetrics.defectWork.openDefectWork}</strong>
          </div>
          <div>
            <span class="label-xs">DPMO</span>
            <strong>{compactNumber(dashboard.emsMetrics.defectWork.dpmo)}</strong>
          </div>
        </div>

        {#if dashboard.emsMetrics.roleDoctrineReadiness}
          <div class="role-council">
            <div class="card-header compact">
              <div>
                <h2>BARCAN Council Readiness</h2>
                <p class="ems-note">{dashboard.emsMetrics.roleDoctrineReadiness.interpretation}</p>
              </div>
              <span class="council-status {dashboard.emsMetrics.roleDoctrineReadiness.statusLabel}">
                {dashboard.emsMetrics.roleDoctrineReadiness.statusLabel}
              </span>
            </div>

            <div class="council-summary">
              <div>
                <span class="label-xs">Readiness</span>
                <strong>{score(dashboard.emsMetrics.roleDoctrineReadiness.readinessScore)}</strong>
              </div>
              <div>
                <span class="label-xs">Satisfied</span>
                <strong>{dashboard.emsMetrics.roleDoctrineReadiness.satisfied}</strong>
              </div>
              <div>
                <span class="label-xs">Almost</span>
                <strong>{dashboard.emsMetrics.roleDoctrineReadiness.almostSatisfied}</strong>
              </div>
              <div>
                <span class="label-xs">Objects</span>
                <strong>{dashboard.emsMetrics.roleDoctrineReadiness.objects}</strong>
              </div>
              <div>
                <span class="label-xs">Refuses</span>
                <strong>{dashboard.emsMetrics.roleDoctrineReadiness.refuses}</strong>
              </div>
              <div>
                <span class="label-xs">Unknown</span>
                <strong>{dashboard.emsMetrics.roleDoctrineReadiness.unknown}</strong>
              </div>
            </div>

            <div class="doctrine-grid">
              {#each dashboard.emsMetrics.roleDoctrineReadiness.roles as role}
                <article class="doctrine-card {role.stance}">
                  <div class="doctrine-head">
                    <div>
                      <span class="role-tag">{role.roleTag}</span>
                      <h3>{role.doctrineName}</h3>
                    </div>
                    <span class="stance-badge {role.stance}">{stanceLabel(role.stance)}</span>
                  </div>
                  <p class="doctrine-focus">{role.doctrineFocus}</p>
                  <div class="doctrine-score">
                    <div class="bar-track">
                      <div class="bar-fill doctrine {role.stance}" style={`width: ${scoreWidth(role.satisfactionScore)}`}></div>
                    </div>
                    <strong>{score(role.satisfactionScore)}</strong>
                  </div>
                  <div class="doctrine-meta">
                    <span>Kano: {pressureLabel(role.kanoPressure)}</span>
                    <span>Confidence: {percent(role.confidence)}</span>
                    <span>Source: {role.sourceWishlistPending}/{role.sourceWishlistTotal}</span>
                    <span>Owner: {role.ownerTasksDone}/{role.ownerTasksTotal}</span>
                  </div>
                  <p class="doctrine-objection">{role.topObjection}</p>
                </article>
              {/each}
            </div>
          </div>
        {/if}

        <div class="ems-grid">
          <div class="ems-box">
            <div class="card-header compact">
              <h2>Execution Flow</h2>
              <span class="indicator">{dashboard.emsMetrics.flowChart.totalTasks} tasks</span>
            </div>
            <div class="flow-chart">
              {#each dashboard.emsMetrics.flowChart.stages as stage}
                <div class="flow-row">
                  <div class="flow-label">
                    <strong>{stage.label}</strong>
                    <span>{stage.done}/{stage.total}</span>
                  </div>
                  <div class="bar-track">
                    <div class="bar-fill flow" style={`width: ${width(stage.weightedScore)}`}></div>
                  </div>
                  <span class="bar-value">{percent(stage.weightedScore)}</span>
                </div>
              {/each}
            </div>
          </div>

          <div class="ems-box">
            <div class="card-header compact">
              <h2>Role Execution Telemetry</h2>
              <span class="indicator">{dashboard.emsMetrics.roleKpis.length} owner roles</span>
            </div>
            <div class="role-kpi-list">
              {#each dashboard.emsMetrics.roleKpis as role}
                <div class="role-kpi-row">
                  <div class="flow-label">
                    <strong>{role.roleTag}</strong>
                    <span>{role.done}/{role.total} done / open {role.queued + role.active + role.blocked + role.failed} / D:{role.defectWork}</span>
                  </div>
                  <div class="bar-track">
                    <div class="bar-fill kpi {role.statusLabel}" style={`width: ${scoreWidth(role.kpiScore)}`}></div>
                  </div>
                  <span class="kpi-status {role.statusLabel}">{role.statusLabel}</span>
                </div>
              {/each}
            </div>
          </div>

          <div class="ems-box">
            <div class="card-header compact">
              <h2>Defects & Graph Health</h2>
              <span class="indicator">CP {dashboard.emsMetrics.graphHealth.criticalPathLength}</span>
            </div>
            <div class="health-grid">
              <div>
                <span class="label-xs">Blocked by dependency</span>
                <strong>{dashboard.emsMetrics.graphHealth.blockedByDependency}</strong>
              </div>
              <div>
                <span class="label-xs">Duplicate semantic keys</span>
                <strong>{dashboard.emsMetrics.graphHealth.duplicateSemanticKeys}</strong>
              </div>
              <div>
                <span class="label-xs">Retry load</span>
                <strong>{dashboard.emsMetrics.defectWork.retryLoad}</strong>
              </div>
              <div>
                <span class="label-xs">Defect pressure</span>
                <strong>{percent(dashboard.emsMetrics.defectWork.defectPressure)}</strong>
              </div>
            </div>
            <p class="ems-note">{dashboard.emsMetrics.graphHealth.interpretation}</p>
          </div>
        </div>
      </section>
    {/if}

    {#if dashboard.project.status === 'analyzing'}
      <!-- Onboarding Report View -->
      <div class="onboarding-container">
        <div class="onboarding-header-card">
          <div class="onboarding-meta">
            <h2 class="onboarding-title">Project Onboarding Audit</h2>
            <p class="onboarding-subtitle">This repository is in analysis mode. Review the stack profile and the architectural findings below.</p>
          </div>
          <button class="btn btn-success" onclick={activateProject}>
            Project analyzed, activate it
          </button>
        </div>

        <div class="onboarding-content-grid">
          <!-- Left pane: Markdown Report -->
          <div class="report-pane card">
            <h3>Audit Report Summary</h3>
            <div class="markdown-body">
              <pre class="report-pre">{onboardingReport || 'Analyzing codebase and generating report...'}</pre>
            </div>
          </div>

          <!-- Right pane: Audit Findings list with interactive wishlist adding -->
          <div class="findings-pane card">
            <h3>Architectural Violations ({onboardingFindings.length})</h3>
            <p class="section-desc">You can review the violations detected by the 12 system roles. Click 'Add to Wishlist' to queue a finding for fixing.</p>

            <div class="findings-list">
              {#if onboardingFindings.length === 0}
                <p class="empty-state">No architectural violations found. Ready to activate!</p>
              {:else}
                {#each onboardingFindings as finding}
                  <div class="finding-card {finding.severity}">
                    <div class="finding-header">
                      <span class="finding-badge {finding.severity}">{finding.severity}</span>
                      <span class="role-tag">{finding.roleTag}</span>
                    </div>
                    <p class="finding-text">{finding.findingText}</p>
                    {#if finding.filePath}
                      <code class="file-path">{finding.filePath}{finding.lineNumber ? ` : L${finding.lineNumber}` : ''}</code>
                    {/if}
                    <div class="finding-actions">
                      <button class="btn btn-secondary btn-sm" onclick={() => addFindingToWishlist(finding)}>
                        Add to Wishlist
                      </button>
                    </div>
                  </div>
                {/each}
              {/if}
            </div>
          </div>
        </div>
      </div>
    {:else}
      <!-- Grid Columns -->
      <div class="dashboard-grid">
      
      <!-- COLUMN 1: Intake & Wishlist -->
      <section class="grid-col intake-col">
        <div class="card">
          <div class="card-header">
            <h2>Client Wishlist</h2>
            <span class="indicator">{dashboard.openWishlistCount} open</span>
          </div>
          
          <div class="textarea-wrapper">
            <textarea 
              bind:value={wishText} 
              placeholder="What should the product do next? Describe the client wish..."
              disabled={dashboard.project.status === 'accepted'}
            ></textarea>
            <button 
              class="btn btn-primary wide" 
              onclick={addWish} 
              disabled={dashboard.project.status === 'accepted' || !wishText.trim()}
            >
              Add Wish
            </button>
          </div>

          <div class="feed scrollable">
            {#if dashboard.wishlist.length === 0}
              <p class="empty-state">No wishlist items. Submit one above.</p>
            {:else}
              {#each dashboard.wishlist as item}
                <article class="wish-item">
                  <div class="item-header">
                    <span class="badge-tag">{item.source || 'client'}</span>
                    <span class="badge-status {item.status}">{item.status}</span>
                  </div>
                  <p title={item.text || item.content}>{item.text || item.content}</p>
                </article>
              {/each}
            {/if}
          </div>
        </div>
      </section>

      <!-- COLUMN 2: Quality Gates & Delivery -->
      <section class="grid-col delivery-col">
        <!-- Acceptance Readiness -->
        <div class="card readiness-card {commandDashboard.acceptanceReadiness.uiColorToken}">
          <div class="card-header">
            <h2>Acceptance Readiness</h2>
            <span class="badge {commandDashboard.acceptanceReadiness.readiness}">
              {commandDashboard.acceptanceReadiness.statusLabel}
            </span>
          </div>

          {#if commandDashboard.acceptanceReadiness.kanoRecommendation}
            <div class="kano-box">
              <div class="kano-title">
                <span>Kano</span> Model Evaluation
              </div>
              <p>{commandDashboard.acceptanceReadiness.kanoRecommendation}</p>
            </div>
          {/if}

          {#if commandDashboard.acceptanceReadiness.unmetConditions && commandDashboard.acceptanceReadiness.unmetConditions.length > 0}
            <div class="unmet-box">
              <h3>Unmet Conditions:</h3>
              <ul>
                {#each commandDashboard.acceptanceReadiness.unmetConditions as condition}
                  <li>{condition}</li>
                {/each}
              </ul>
            </div>
          {/if}

          <div class="status-indicators">
            <div class="indicator-item {commandDashboard.acceptanceReadiness.allTasksDone ? 'pass' : 'fail'}">
              <span>{commandDashboard.acceptanceReadiness.allTasksDone ? '✓' : '✗'}</span> All Tasks Done
            </div>
            <div class="indicator-item {commandDashboard.acceptanceReadiness.allQualityGatesPassed ? 'pass' : 'fail'}">
              <span>{commandDashboard.acceptanceReadiness.allQualityGatesPassed ? '✓' : '✗'}</span> Quality Gates Passed
            </div>
            <div class="indicator-item {commandDashboard.acceptanceReadiness.allPrsMerged ? 'pass' : 'fail'}">
              <span>{commandDashboard.acceptanceReadiness.allPrsMerged ? '✓' : '✗'}</span> PRs Merged
            </div>
            <div class="indicator-item {commandDashboard.acceptanceReadiness.githubAccessHealthy ? 'pass' : 'fail'}">
              <span>{commandDashboard.acceptanceReadiness.githubAccessHealthy ? '✓' : '✗'}</span> GitHub Access
            </div>
          </div>

          <div class="final-acceptance-zone">
            <div>
              <h3>Final Delivery Acceptance</h3>
              <p>
                Use this only as the last project action. It marks the project as accepted,
                stops new orchestration, and closes production work.
              </p>
            </div>
            {#if dashboard.project.status === 'accepted'}
              <span class="badge accepted">Accepted</span>
            {:else}
              <button class="btn btn-danger-outline" onclick={openAcceptConfirmation}>
                Open Final Acceptance
              </button>
            {/if}
          </div>
        </div>

        <!-- Tasks List -->
        <div class="card">
          <div class="card-header">
            <h2>Project Tasks</h2>
            <span class="indicator">{dashboard.tasks.length} total</span>
          </div>

          <div class="tasks scrollable">
            {#if dashboard.tasks.length === 0}
              <p class="empty-state">No tasks created yet. Click Orchestrate to compile wishes.</p>
            {:else}
              {#each dashboard.tasks as task}
                <article class="task-item">
                  <div class="item-header">
                    <span class="role-tag">{task.tag}</span>
                    <span class="kano-badge {getKanoCategory(task.tag).colorClass}">{getKanoCategory(task.tag).label}</span>
                    <span class="badge-status {task.status}">{task.status}</span>
                  </div>
                  <p class="task-description" title={task.description}>{task.description}</p>
                  <div class="task-footer">
                    <span class="gate-badge {task.qualityGatePassed ? 'pass' : 'fail'}">
                      Gate: {task.qualityGatePassed ? 'Passed' : 'Pending'}
                    </span>
                    <span class="dispatch-status">{task.julesDispatchStatus || 'Pending dispatch'}</span>
                  </div>
                </article>
              {/each}
            {/if}
          </div>
        </div>
      </section>

      <!-- COLUMN 3: Team Pool & Queue -->
      <section class="grid-col team-col">
        <!-- Energy Pool -->
        <div class="card">
          <div class="card-header">
            <h2>Jules Energy Pool</h2>
            <span class="indicator">{dashboard.agentCount} Jules</span>
          </div>

          <div class="agent-list scrollable">
            {#each dashboard.agents as agent}
              <article class="agent-card">
                <div class="agent-header">
                  <strong>{agent.name}</strong>
                  <span class="badge-status {agent.status}">{agent.status}</span>
                </div>
                <div class="agent-body">
                  <p class="role"><span class="label-xs">Active Role:</span> {agent.currentRoleTag || 'None'}</p>
                  <p class="task-desc"><span class="label-xs">Task:</span> {agent.currentTaskDescription || 'Idle, waiting for constraints...'}</p>
                </div>
              </article>
            {/each}
          </div>
        </div>

        <!-- Role Queue -->
        <div class="card">
          <div class="card-header">
            <h2>Role Queue Bottlenecks</h2>
          </div>
          <div class="queue-list">
            {#if dashboard.queue.byTag.length === 0}
              <p class="empty-state">Queue is empty. Everything processed!</p>
            {:else}
              {#each dashboard.queue.byTag as item}
                <div class="queue-item">
                  <span class="role-tag">{item.tag}</span>
                  <span class="queue-count badge {item.count > 1 ? 'warning' : 'success'}">{item.count} tasks</span>
                </div>
              {/each}
            {/if}
          </div>
        </div>
      </section>

    </div>
    {/if}

    <!-- Status Bar -->
    <footer class="status-bar">
      <span class="status-dot"></span> {statusMsg}
    </footer>

    {#if showAcceptConfirm}
      <div class="modal-backdrop" role="presentation">
        <section class="accept-modal" role="dialog" aria-modal="true" aria-labelledby="accept-modal-title">
          <div class="modal-header">
            <div>
              <span class="label-xs danger-label">Final action</span>
              <h2 id="accept-modal-title">Accept and Close Project</h2>
            </div>
            <button class="modal-close" type="button" onclick={closeAcceptConfirmation} aria-label="Close final acceptance dialog" disabled={acceptingProject}>
              ×
            </button>
          </div>

          <div class="modal-warning">
            <strong>This action finishes the project.</strong>
            <p>After acceptance, Eneik stops new orchestration for this project. Use it only after tasks, quality gates, and PRs are truly complete.</p>
          </div>

          <label class="confirm-label" for="accept-confirm-input">
            Type the project name to confirm:
            <code>{acceptConfirmationTarget}</code>
          </label>
          <input
            id="accept-confirm-input"
            class="confirm-input"
            type="text"
            bind:value={acceptConfirmationText}
            placeholder={acceptConfirmationTarget}
            disabled={acceptingProject}
          />

          <div class="modal-actions">
            <button class="btn btn-secondary" type="button" onclick={closeAcceptConfirmation} disabled={acceptingProject}>
              Cancel
            </button>
            <button class="btn btn-danger" type="button" onclick={acceptProject} disabled={!acceptConfirmationMatches || acceptingProject}>
              {acceptingProject ? 'Accepting...' : 'Accept and Close Project'}
            </button>
          </div>
        </section>
      </div>
    {/if}
  {/if}
</div>

<style>
  .dashboard-root {
    display: flex;
    flex-direction: column;
    gap: var(--space-6);
    min-height: calc(100vh - 200px);
    font-family: inherit;
  }

  /* Loader styling */
  .loader-container {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    height: 300px;
    gap: var(--space-4);
    color: var(--neutral-500);
  }
  .loader-spinner {
    width: 48px;
    height: 48px;
    border: 4px solid var(--neutral-200);
    border-top: 4px solid var(--primary);
    border-radius: 50%;
    animation: spin 1s linear infinite;
  }
  @keyframes spin {
    0% { transform: rotate(0deg); }
    100% { transform: rotate(360deg); }
  }

  /* Header area */
  .project-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    background: var(--surface);
    border: 1px solid var(--neutral-200);
    border-radius: 12px;
    padding: var(--space-6);
    box-shadow: 0 1px 3px rgba(0,0,0,0.02);
  }
  .name-status {
    display: flex;
    align-items: center;
    gap: var(--space-3);
  }
  .name-status h1 {
    font-size: 24px;
    font-weight: 800;
    color: var(--neutral-800);
  }
  .meta-line {
    font-size: 13px;
    color: var(--neutral-500);
    margin-top: var(--space-1);
  }
  .path {
    background: var(--neutral-100);
    padding: 2px 6px;
    border-radius: 4px;
    font-family: monospace;
    font-size: 12px;
  }
  .actions-area {
    display: flex;
    gap: var(--space-3);
  }

  .ems-panel {
    display: flex;
    flex-direction: column;
    gap: var(--space-3);
  }
  .ems-summary {
    display: grid;
    grid-template-columns: repeat(5, minmax(0, 1fr));
    gap: var(--space-3);
    background: var(--surface);
    border: 1px solid var(--neutral-200);
    border-radius: 8px;
    padding: var(--space-3);
  }
  .ems-summary > div,
  .health-grid > div {
    display: flex;
    flex-direction: column;
    gap: 2px;
    min-width: 0;
  }
  .ems-summary strong,
  .health-grid strong {
    font-size: 20px;
    color: var(--neutral-800);
    line-height: 1.1;
  }
  .role-council {
    background: var(--surface);
    border: 1px solid var(--neutral-200);
    border-radius: 8px;
    padding: var(--space-3);
    min-width: 0;
  }
  .council-status {
    font-size: 10px;
    font-weight: 900;
    text-transform: uppercase;
    border-radius: 5px;
    padding: 4px 8px;
    white-space: nowrap;
  }
  .council-status.ready,
  .stance-badge.satisfied {
    background: #d1fae5;
    color: #065f46;
  }
  .council-status.incomplete,
  .stance-badge.unknown {
    background: #e0e7ff;
    color: #3730a3;
  }
  .council-status.contested,
  .stance-badge.objects,
  .stance-badge.almost_satisfied {
    background: #fef3c7;
    color: #92400e;
  }
  .council-status.blocked,
  .stance-badge.refuses {
    background: #fee2e2;
    color: #991b1b;
  }
  .council-summary {
    display: grid;
    grid-template-columns: repeat(6, minmax(0, 1fr));
    gap: var(--space-2);
    margin-bottom: var(--space-3);
  }
  .council-summary > div {
    background: var(--neutral-50);
    border: 1px solid var(--neutral-100);
    border-radius: 6px;
    padding: var(--space-2);
    min-width: 0;
  }
  .council-summary strong {
    display: block;
    font-size: 18px;
    line-height: 1.1;
    color: var(--neutral-800);
  }
  .doctrine-grid {
    display: grid;
    grid-template-columns: repeat(4, minmax(220px, 1fr));
    gap: var(--space-2);
  }
  .doctrine-card {
    border: 1px solid var(--neutral-200);
    border-left: 4px solid var(--neutral-300);
    border-radius: 8px;
    padding: var(--space-3);
    min-width: 0;
    background: #fff;
  }
  .doctrine-card.satisfied {
    border-left-color: #0d9488;
  }
  .doctrine-card.almost_satisfied {
    border-left-color: #f59e0b;
  }
  .doctrine-card.objects {
    border-left-color: #ea580c;
  }
  .doctrine-card.refuses {
    border-left-color: #dc2626;
  }
  .doctrine-card.unknown {
    border-left-color: #4f46e5;
  }
  .doctrine-head {
    display: flex;
    justify-content: space-between;
    gap: var(--space-2);
    align-items: flex-start;
    min-width: 0;
  }
  .doctrine-head h3 {
    font-size: 13px;
    font-weight: 800;
    color: var(--neutral-800);
    margin-top: 3px;
    overflow-wrap: anywhere;
  }
  .stance-badge {
    font-size: 9px;
    font-weight: 900;
    text-transform: uppercase;
    border-radius: 4px;
    padding: 2px 5px;
    white-space: nowrap;
  }
  .doctrine-focus {
    min-height: 34px;
    margin: var(--space-2) 0;
    color: var(--neutral-600);
    font-size: 11px;
    line-height: 1.35;
  }
  .doctrine-score {
    display: grid;
    grid-template-columns: 1fr auto;
    align-items: center;
    gap: var(--space-2);
    margin-bottom: var(--space-2);
  }
  .doctrine-score strong {
    font-size: 13px;
    color: var(--neutral-800);
  }
  .bar-fill.doctrine.satisfied {
    background: #0d9488;
  }
  .bar-fill.doctrine.almost_satisfied {
    background: #f59e0b;
  }
  .bar-fill.doctrine.objects {
    background: #ea580c;
  }
  .bar-fill.doctrine.refuses {
    background: #dc2626;
  }
  .bar-fill.doctrine.unknown {
    background: #4f46e5;
  }
  .doctrine-meta {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 3px 8px;
    color: var(--neutral-500);
    font-size: 10px;
  }
  .doctrine-meta span {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .doctrine-objection {
    margin: var(--space-2) 0 0;
    color: var(--neutral-700);
    font-size: 10px;
    line-height: 1.35;
  }
  .ems-grid {
    display: grid;
    grid-template-columns: 1fr 1.15fr 0.85fr;
    gap: var(--space-3);
  }
  .ems-box {
    background: var(--surface);
    border: 1px solid var(--neutral-200);
    border-radius: 8px;
    padding: var(--space-3);
    min-width: 0;
  }
  .card-header.compact {
    margin-bottom: var(--space-3);
    padding-bottom: var(--space-2);
  }
  .flow-chart,
  .role-kpi-list {
    display: flex;
    flex-direction: column;
    gap: var(--space-2);
    max-height: 180px;
    overflow-y: auto;
    padding-right: 2px;
  }
  .flow-row,
  .role-kpi-row {
    display: grid;
    grid-template-columns: minmax(96px, 1fr) 1.6fr minmax(44px, auto);
    align-items: center;
    gap: var(--space-2);
    min-height: 30px;
  }
  .flow-label {
    display: flex;
    flex-direction: column;
    gap: 1px;
    min-width: 0;
  }
  .flow-label strong {
    font-size: 11px;
    color: var(--neutral-700);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .flow-label span,
  .bar-value {
    font-size: 10px;
    color: var(--neutral-500);
  }
  .bar-track {
    height: 8px;
    background: var(--neutral-100);
    border-radius: 999px;
    overflow: hidden;
  }
  .bar-fill {
    height: 100%;
    border-radius: inherit;
    transition: width 0.2s ease;
  }
  .bar-fill.flow {
    background: #2563eb;
  }
  .bar-fill.kpi.on_target {
    background: #0d9488;
  }
  .bar-fill.kpi.watch {
    background: #f59e0b;
  }
  .bar-fill.kpi.idle {
    background: #94a3b8;
  }
  .bar-fill.kpi.attention,
  .bar-fill.kpi.behind {
    background: #dc2626;
  }
  .kpi-status {
    font-size: 9px;
    font-weight: 800;
    text-transform: uppercase;
    border-radius: 4px;
    padding: 2px 5px;
    text-align: center;
    white-space: nowrap;
  }
  .kpi-status.on_target {
    background: #d1fae5;
    color: #065f46;
  }
  .kpi-status.watch {
    background: #fef3c7;
    color: #92400e;
  }
  .kpi-status.idle {
    background: #f1f5f9;
    color: #475569;
  }
  .kpi-status.attention,
  .kpi-status.behind {
    background: #fee2e2;
    color: #991b1b;
  }
  .health-grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: var(--space-3);
  }
  .ems-note {
    margin: var(--space-3) 0 0;
    color: var(--neutral-500);
    font-size: 11px;
    line-height: 1.4;
  }

  /* Dashboard Grid Layout */
  .dashboard-grid {
    display: grid;
    grid-template-columns: minmax(300px, 0.95fr) minmax(360px, 1fr) minmax(300px, 0.95fr);
    gap: var(--space-6);
    flex: 1;
    align-items: start;
    min-height: 0; /* Important for inner scrollable components */
  }

  .grid-col {
    display: flex;
    flex-direction: column;
    gap: var(--space-6);
    min-height: 0;
  }

  /* Cards Styling */
  .card {
    background: var(--surface);
    border: 1px solid var(--neutral-200);
    border-radius: 12px;
    padding: var(--space-4);
    display: flex;
    flex-direction: column;
    box-shadow: 0 1px 3px rgba(0,0,0,0.02);
    min-height: 0;
  }
  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: var(--space-4);
    border-bottom: 1px solid var(--neutral-100);
    padding-bottom: var(--space-2);
  }
  .card-header h2 {
    font-size: 16px;
    font-weight: 700;
    color: var(--neutral-700);
  }
  .indicator {
    font-size: 12px;
    font-weight: 600;
    background: var(--neutral-100);
    color: var(--neutral-600);
    padding: 2px 8px;
    border-radius: 10px;
  }

  /* Acceptance Readiness card special border */
  .readiness-card {
    border-top: 4px solid var(--primary);
  }
  .readiness-card.border-warning {
    border-top-color: var(--warning);
  }
  .readiness-card.border-success {
    border-top-color: var(--success);
  }

  /* Scrollable containers */
  .scrollable {
    overflow-y: auto;
    flex: 0 1 auto;
    display: flex;
    flex-direction: column;
    gap: var(--space-3);
    padding-right: var(--space-1);
  }

  .feed.scrollable,
  .agent-list.scrollable {
    max-height: 280px;
  }

  .tasks.scrollable {
    max-height: 360px;
    min-height: 180px;
  }
  /* Custom scrollbar */
  .scrollable::-webkit-scrollbar {
    width: 6px;
  }
  .scrollable::-webkit-scrollbar-track {
    background: transparent;
  }
  .scrollable::-webkit-scrollbar-thumb {
    background: var(--neutral-200);
    border-radius: 3px;
  }
  .scrollable::-webkit-scrollbar-thumb:hover {
    background: var(--neutral-300);
  }

  /* Wishlist items */
  .textarea-wrapper {
    margin-bottom: var(--space-4);
  }
  .textarea-wrapper textarea {
    min-height: 80px;
    border: 1px solid var(--neutral-200);
    border-radius: 8px;
    padding: var(--space-3);
    font-size: 14px;
    outline: none;
    transition: border-color 0.2s;
  }
  .textarea-wrapper textarea:focus {
    border-color: var(--primary);
  }
  .wish-item {
    background: var(--neutral-50);
    border: 1px solid var(--neutral-100);
    border-radius: 8px;
    padding: var(--space-3);
    font-size: 13px;
    line-height: 1.5;
  }
  .wish-item p {
    color: var(--neutral-700);
    display: -webkit-box;
    -webkit-box-orient: vertical;
    -webkit-line-clamp: 7;
    line-clamp: 7;
    overflow: hidden;
  }
  .item-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    flex-wrap: wrap;
    gap: var(--space-1);
    margin-bottom: var(--space-2);
  }
  .badge-tag {
    font-size: 10px;
    font-weight: 700;
    text-transform: uppercase;
    color: var(--accent);
  }

  /* Kano model suggestion box */
  .kano-box {
    background: linear-gradient(135deg, #eff6ff 0%, #dbeafe 100%);
    border: 1px solid #bfdbfe;
    color: #1e3a8a;
    border-radius: 8px;
    padding: var(--space-3);
    margin-bottom: var(--space-4);
    max-height: 180px;
    overflow: auto;
  }
  .kano-title {
    font-weight: 700;
    font-size: 12px;
    display: flex;
    align-items: center;
    gap: var(--space-1);
    margin-bottom: 4px;
  }
  .kano-box p {
    font-size: 12px;
    line-height: 1.4;
  }

  /* Unmet conditions box */
  .unmet-box {
    background: var(--error-bg);
    border: 1px solid #fecaca;
    color: var(--error);
    border-radius: 8px;
    padding: var(--space-3);
    margin-bottom: var(--space-4);
  }
  .unmet-box h3 {
    font-size: 12px;
    font-weight: 700;
    margin: 0 0 4px;
  }
  .unmet-box ul {
    margin: 0;
    padding-left: var(--space-4);
    font-size: 11px;
  }

  /* Readiness checklists */
  .status-indicators {
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    gap: var(--space-2);
  }
  .indicator-item {
    display: flex;
    align-items: center;
    gap: var(--space-2);
    font-size: 12px;
    font-weight: 600;
    padding: 6px var(--space-2);
    border-radius: 6px;
    background: var(--neutral-50);
    border: 1px solid var(--neutral-100);
  }
  .indicator-item.pass {
    color: var(--success);
    border-color: #a7f3d0;
    background: #ecfdf5;
  }
  .indicator-item.fail {
    color: var(--neutral-500);
  }
  .final-acceptance-zone {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: var(--space-3);
    margin-top: var(--space-4);
    padding: var(--space-3);
    border: 1px solid #fecaca;
    border-radius: 8px;
    background: #fff7f7;
  }
  .final-acceptance-zone h3 {
    margin: 0 0 4px;
    color: #991b1b;
    font-size: 13px;
    font-weight: 800;
    text-transform: uppercase;
  }
  .final-acceptance-zone p {
    margin: 0;
    color: var(--neutral-600);
    font-size: 12px;
    line-height: 1.45;
  }

  /* Tasks items */
  .task-item {
    background: var(--surface);
    border: 1px solid var(--neutral-200);
    border-radius: 8px;
    padding: var(--space-3);
    box-shadow: 0 1px 2px rgba(0,0,0,0.01);
    min-width: 0;
  }
  .task-description {
    font-size: 13px;
    color: var(--neutral-700);
    margin: var(--space-2) 0;
    line-height: 1.4;
    display: -webkit-box;
    -webkit-box-orient: vertical;
    -webkit-line-clamp: 4;
    line-clamp: 4;
    overflow: hidden;
  }
  .task-footer {
    display: flex;
    justify-content: space-between;
    align-items: center;
    font-size: 11px;
    border-top: 1px solid var(--neutral-100);
    padding-top: var(--space-2);
    margin-top: var(--space-2);
    gap: var(--space-2);
    min-width: 0;
  }
  .gate-badge {
    padding: 1px 6px;
    border-radius: 4px;
    font-weight: 700;
    font-size: 10px;
    text-transform: uppercase;
  }
  .gate-badge.pass {
    background: #d1fae5;
    color: var(--success);
  }
  .gate-badge.fail {
    background: var(--neutral-100);
    color: var(--neutral-500);
  }

  .kano-badge {
    font-size: 10px;
    font-weight: 800;
    text-transform: uppercase;
    padding: 2px 6px;
    border-radius: 4px;
    margin: 0 var(--space-1);
    display: inline-block;
  }
  .kano-badge.kano-must {
    background: #fee2e2;
    color: #991b1b;
  }
  .kano-badge.kano-linear {
    background: #dbeafe;
    color: #1e40af;
  }
  .kano-badge.kano-attractive {
    background: #ecfdf5;
    color: #047857;
  }
  .kano-badge.kano-indifferent {
    background: #f1f5f9;
    color: #475569;
  }

  .dispatch-status {
    color: var(--neutral-500);
    font-style: italic;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  /* Agent list pool */
  .agent-card {
    background: var(--neutral-50);
    border: 1px solid var(--neutral-100);
    border-radius: 8px;
    padding: var(--space-3);
  }
  .agent-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: var(--space-2);
  }
  .agent-header strong {
    font-size: 13px;
    color: var(--neutral-800);
  }
  .agent-body {
    font-size: 12px;
    line-height: 1.4;
  }
  .agent-body .role {
    font-weight: 600;
    color: var(--neutral-700);
  }
  .agent-body .task-desc {
    color: var(--neutral-500);
    margin-top: 2px;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }
  .label-xs {
    font-size: 10px;
    text-transform: uppercase;
    color: var(--neutral-400);
    font-weight: 700;
  }

  /* Role Queue */
  .queue-list {
    display: flex;
    flex-wrap: wrap;
    gap: var(--space-2);
  }
  .queue-item {
    display: flex;
    align-items: center;
    gap: var(--space-1);
    background: var(--neutral-50);
    border: 1px solid var(--neutral-200);
    border-radius: 6px;
    padding: var(--space-1) var(--space-2);
  }
  .queue-count {
    font-size: 10px;
    padding: 1px 6px;
    border-radius: 4px;
  }

  /* Badges status generic */
  .badge {
    padding: 3px 8px;
    border-radius: 12px;
    font-size: 11px;
    font-weight: 700;
    text-transform: uppercase;
  }
  .badge.active {
    background: #d1fae5;
    color: #065f46;
  }
  .badge.accepted {
    background: #dbeafe;
    color: #1e40af;
  }
  .badge.frozen {
    background: #fef3c7;
    color: #92400e;
  }
  .badge.ready {
    background: #d1fae5;
    color: #065f46;
  }
  .badge.unknown {
    background: var(--neutral-100);
    color: var(--neutral-600);
  }

  .badge-status {
    padding: 1px 6px;
    border-radius: 4px;
    font-size: 10px;
    font-weight: 700;
    text-transform: uppercase;
  }
  .badge-status.idle {
    background: #e2e8f0;
    color: #475569;
  }
  .badge-status.busy {
    background: #fef3c7;
    color: #d97706;
  }
  .badge-status.stuck {
    background: #fee2e2;
    color: #dc2626;
  }
  .badge-status.pr_opened {
    background: #dbeafe;
    color: #2563eb;
  }
  .badge-status.done {
    background: #d1fae5;
    color: #10b981;
  }

  /* Global status bar */
  .status-bar {
    display: flex;
    align-items: center;
    gap: var(--space-2);
    font-size: 12px;
    color: var(--neutral-600);
    background: var(--neutral-100);
    border: 1px solid var(--neutral-200);
    padding: var(--space-2) var(--space-4);
    border-radius: 8px;
  }
  .status-dot {
    width: 8px;
    height: 8px;
    background: var(--success);
    border-radius: 50%;
  }

  /* General buttons */
  .btn {
    font-size: 13px;
    font-weight: 600;
    height: 36px;
    padding: 0 16px;
    border-radius: 8px;
    transition: all 0.2s;
  }
  .btn-primary {
    background: var(--primary);
    color: var(--surface);
  }
  .btn-primary:hover {
    background: #1e40af;
  }
  .btn-secondary {
    background: var(--neutral-100);
    color: var(--neutral-700);
    border: 1px solid var(--neutral-200);
  }
  .btn-secondary:hover {
    background: var(--neutral-200);
  }
  .btn-success {
    background: var(--success);
    color: var(--surface);
  }
  .btn-success:hover {
    background: #0d9488;
  }
  .btn-danger-outline {
    background: var(--surface);
    border: 1px solid #dc2626;
    color: #b91c1c;
    white-space: nowrap;
  }
  .btn-danger-outline:hover {
    background: #fee2e2;
  }
  .btn-danger {
    background: #dc2626;
    color: var(--surface);
  }
  .btn-danger:hover {
    background: #b91c1c;
  }
  .btn:disabled {
    cursor: not-allowed;
    opacity: 0.55;
  }
  .modal-backdrop {
    position: fixed;
    inset: 0;
    z-index: 80;
    display: flex;
    align-items: center;
    justify-content: center;
    padding: var(--space-4);
    background: rgba(15, 23, 42, 0.55);
  }
  .accept-modal {
    width: min(520px, 100%);
    background: var(--surface);
    border-radius: 12px;
    border: 1px solid var(--neutral-200);
    box-shadow: 0 24px 70px rgba(15, 23, 42, 0.28);
    padding: var(--space-5);
  }
  .modal-header {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: var(--space-3);
    margin-bottom: var(--space-4);
  }
  .modal-header h2 {
    margin: 2px 0 0;
    color: var(--neutral-800);
    font-size: 20px;
    font-weight: 800;
  }
  .danger-label {
    color: #b91c1c;
  }
  .modal-close {
    width: 32px;
    height: 32px;
    border-radius: 8px;
    background: var(--neutral-100);
    color: var(--neutral-600);
    font-size: 22px;
    line-height: 1;
  }
  .modal-warning {
    margin-bottom: var(--space-4);
    padding: var(--space-3);
    border: 1px solid #fecaca;
    border-radius: 8px;
    background: #fff7f7;
    color: #7f1d1d;
    font-size: 13px;
    line-height: 1.45;
  }
  .modal-warning p {
    margin: 4px 0 0;
  }
  .confirm-label {
    display: flex;
    flex-direction: column;
    gap: 6px;
    color: var(--neutral-700);
    font-size: 13px;
    font-weight: 700;
  }
  .confirm-label code {
    width: fit-content;
    max-width: 100%;
    overflow: hidden;
    text-overflow: ellipsis;
    background: var(--neutral-100);
    border-radius: 4px;
    color: var(--neutral-800);
    font-size: 12px;
    padding: 2px 6px;
    white-space: nowrap;
  }
  .confirm-input {
    width: 100%;
    height: 40px;
    margin-top: var(--space-2);
    border: 1px solid var(--neutral-300);
    border-radius: 8px;
    color: var(--neutral-800);
    font-size: 14px;
    padding: 0 12px;
  }
  .confirm-input:focus {
    border-color: #dc2626;
    box-shadow: 0 0 0 3px rgba(220, 38, 38, 0.12);
    outline: none;
  }
  .modal-actions {
    display: flex;
    justify-content: flex-end;
    gap: var(--space-3);
    margin-top: var(--space-5);
  }
  .wide {
    width: 100%;
  }
  .empty-state {
    font-size: 13px;
    color: var(--neutral-400);
    text-align: center;
    padding: var(--space-6) 0;
    font-style: italic;
  }

  /* Banner warn */
  .banner {
    padding: var(--space-3) var(--space-4);
    border-radius: 8px;
    display: flex;
    justify-content: space-between;
    align-items: center;
    font-size: 13px;
    font-weight: 600;
  }
  .banner.warning {
    background: #fffbeb;
    border: 1px solid #fef3c7;
    color: #b45309;
  }
  .banner.error {
    background: var(--error-bg);
    border: 1px solid #fecaca;
    color: var(--error);
  }

  /* Onboarding Flow Styles */
  .onboarding-container {
    display: flex;
    flex-direction: column;
    gap: var(--space-6);
    flex: 1;
    min-height: 0;
  }
  .onboarding-header-card {
    background: #eff6ff;
    border: 1px solid #bfdbfe;
    border-radius: 12px;
    padding: 24px;
    display: flex;
    justify-content: space-between;
    align-items: center;
    box-shadow: 0 1px 3px rgba(0,0,0,0.02);
  }
  .onboarding-title {
    font-size: 20px;
    font-weight: 800;
    color: #1e3a8a;
    margin: 0 0 6px 0;
  }
  .onboarding-subtitle {
    font-size: 14px;
    color: #1e40af;
    margin: 0;
  }
  .onboarding-content-grid {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: var(--space-6);
    flex: 1;
    min-height: 0;
  }
  .report-pane, .findings-pane {
    padding: 20px;
    background: white;
    border: 1px solid var(--neutral-200);
    border-radius: 12px;
    display: flex;
    flex-direction: column;
    min-height: 0;
  }
  .report-pre {
    margin: 0;
    white-space: pre-wrap;
    word-break: break-word;
    font-family: monospace;
    font-size: 13px;
    line-height: 1.6;
    color: var(--neutral-700);
    overflow-y: auto;
    max-height: 500px;
    padding: 12px;
    background: #f8fafc;
    border-radius: 6px;
    border: 1px solid #e2e8f0;
  }
  .section-desc {
    font-size: 13px;
    color: var(--neutral-500);
    margin: 0 0 16px 0;
  }
  .findings-list {
    display: flex;
    flex-direction: column;
    gap: 12px;
    overflow-y: auto;
    flex: 1;
    padding-right: 4px;
  }
  .finding-card {
    background: #f8fafc;
    border: 1px solid #e2e8f0;
    border-radius: 8px;
    padding: 16px;
    display: flex;
    flex-direction: column;
    gap: 8px;
  }
  .finding-card.critical {
    border-left: 4px solid #ef4444;
    background: #fff5f5;
  }
  .finding-card.major {
    border-left: 4px solid #f59e0b;
    background: #fffbeb;
  }
  .finding-card.minor {
    border-left: 4px solid #3b82f6;
    background: #eff6ff;
  }
  .finding-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }
  .finding-badge {
    font-size: 10px;
    font-weight: 700;
    text-transform: uppercase;
    padding: 2px 6px;
    border-radius: 4px;
  }
  .finding-badge.critical {
    background: #fee2e2;
    color: #991b1b;
  }
  .finding-badge.major {
    background: #fef3c7;
    color: #92400e;
  }
  .finding-badge.minor {
    background: #dbeafe;
    color: #1e40af;
  }
  .finding-text {
    font-size: 13px;
    color: var(--neutral-700);
    margin: 0;
    line-height: 1.4;
  }
  .file-path {
    font-family: monospace;
    font-size: 11px;
    background: rgba(0,0,0,0.05);
    padding: 2px 6px;
    border-radius: 4px;
    align-self: flex-start;
  }
  .finding-actions {
    display: flex;
    justify-content: flex-end;
    margin-top: 4px;
  }
  .btn-sm {
    height: 28px;
    padding: 0 12px;
    font-size: 11px;
  }

  @media (max-width: 1100px) {
    .dashboard-root {
      height: auto;
    }
    .project-header,
    .onboarding-header-card {
      align-items: flex-start;
      flex-direction: column;
      gap: var(--space-3);
    }
    .ems-summary {
      grid-template-columns: repeat(2, minmax(0, 1fr));
    }
    .council-summary {
      grid-template-columns: repeat(3, minmax(0, 1fr));
    }
    .doctrine-grid {
      grid-template-columns: repeat(2, minmax(220px, 1fr));
    }
    .ems-grid,
    .dashboard-grid,
    .onboarding-content-grid {
      grid-template-columns: 1fr;
    }
  }

  @media (max-width: 720px) {
    .council-summary,
    .doctrine-grid {
      grid-template-columns: 1fr;
    }
  }
</style>
