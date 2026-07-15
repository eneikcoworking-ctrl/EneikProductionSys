<script lang="ts">
  import { onMount } from 'svelte';

  export let projectId: string = '';

  const API_BASE = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';

  let metrics: any = null;
  let loading = true;
  let error: string | null = null;
  let loadedProjectId = '';

  async function fetchMetrics() {
    try {
      const url = projectId ? `${API_BASE}/api/system-status?projectId=${projectId}` : `${API_BASE}/api/system-status`;
      const response = await fetch(url);
      if (!response.ok) throw new Error('Failed to load system metrics');
      metrics = await response.json();
    } catch (e: any) {
      error = e.message;
    } finally {
      loading = false;
    }
  }

  $: if (projectId) {
    if (projectId !== loadedProjectId) {
      loadedProjectId = projectId;
      loading = true;
      error = null;
      fetchMetrics();
    }
  }

  onMount(() => {
    if (!projectId) {
      fetchMetrics();
    }
  });

  function score(value: number | undefined | null): string {
    return `${Math.round(value ?? 0)}`;
  }

  function percent(value: number | undefined | null): string {
    return `${Math.round((value ?? 0) * 100)}%`;
  }

  function scoreWidth(value: number | undefined | null): string {
    const clamped = Math.max(0, Math.min(100, Math.round(value ?? 0)));
    return `${clamped}%`;
  }

  function stanceLabel(value: string): string {
    return (value || 'unknown').replace(/_/g, ' ');
  }

  function pressureLabel(value: string): string {
    return (value || 'none').replace(/_/g, '-');
  }
</script>

<div class="metrics-root">
  <header class="section-title">
    <h2>System Monitoring And Structured Metrics</h2>
    <p class="section-desc">Production metrics grouped by pipeline, quality, and role controls.</p>
  </header>

  {#if loading}
    <div class="loader-container">
      <div class="loader-spinner"></div>
      <p>Loading live system metrics...</p>
    </div>
  {:else if error}
    <div class="banner error">
      <p>Warning: {error}</p>
    </div>
  {:else if metrics}
    <div class="metrics-grid">

      <!-- BLOCK 1: System Pipeline -->
      <section class="metric-card shadow">
        <h3>System Pipeline</h3>
        <p class="card-subtitle">Execution status for tasks and AI-agent sessions</p>

        <div class="stats-subgrid">
          <div class="stat-item">
            <span class="stat-number">{metrics.tasks?.data?.queued ?? 0}</span>
            <span class="stat-label">Queued Tasks</span>
          </div>
          <div class="stat-item">
            <span class="stat-number text-blue">{metrics.tasks?.data?.claimed ?? 0}</span>
            <span class="stat-label">Claimed By Agents</span>
          </div>
          <div class="stat-item">
            <span class="stat-number text-indigo">{metrics.tasks?.data?.in_progress ?? 0}</span>
            <span class="stat-label">In Progress</span>
          </div>
          <div class="stat-item">
            <span class="stat-number text-yellow">{metrics.tasks?.data?.review ?? 0}</span>
            <span class="stat-label">Code Review</span>
          </div>
          <div class="stat-item">
            <span class="stat-number text-green">{metrics.tasks?.data?.done ?? 0}</span>
            <span class="stat-label">Done</span>
          </div>
          <div class="stat-item">
            <span class="stat-number text-red">{metrics.tasks?.data?.failed ?? 0}</span>
            <span class="stat-label">Failed Tasks</span>
          </div>
        </div>

        <div class="linear-completeness-box">
          <h4>Linear Synchronization</h4>
          <p>Total Linear issues: <strong>{metrics.linearCompleteness?.data?.totalIssues ?? 0}</strong></p>
          <div class="progress-bar-container">
            <div class="progress-bar-fill" style="width: {Math.round((metrics.linearCompleteness?.data?.completeness_rate ?? 0) * 100)}%"></div>
          </div>
          <p class="text-xs text-right mt-1">{Math.round((metrics.linearCompleteness?.data?.completeness_rate ?? 0) * 100)}% complete against DoD standards</p>
        </div>
      </section>

      <!-- BLOCK 2: Quality And Defects -->
      <section class="metric-card shadow">
        <h3>Quality And Defects</h3>
        <p class="card-subtitle">Quality gate and merge conflict DPMO</p>

        <div class="dpmo-subgrid">
          <div class="dpmo-box">
            <span class="stat-number text-red">{Math.round(metrics.qualityGate?.data?.dpmo ?? 0)}</span>
            <span class="stat-label">Quality Gate DPMO</span>
            <small class="text-xs">{metrics.qualityGate?.data?.defects ?? 0} defects across {metrics.qualityGate?.data?.totalOpportunities ?? 0} checks</small>
          </div>
          <div class="dpmo-box">
            <span class="stat-number text-red">{Math.round(metrics.conflictDpmo?.data?.dpmo ?? 0)}</span>
            <span class="stat-label">Merge Conflict DPMO</span>
            <small class="text-xs">{metrics.conflictDpmo?.data?.conflicts ?? 0} conflicts across {metrics.conflictDpmo?.data?.totalMergeAttempts ?? 0} merge attempts</small>
          </div>
        </div>

        <div class="active-conflicts-section">
          <h4>Active Merge Conflicts ({metrics.conflictDpmo?.data?.activeConflicts?.length ?? 0})</h4>
          <div class="conflicts-list">
            {#each metrics.conflictDpmo?.data?.activeConflicts || [] as conflict}
              <div class="conflict-item">
                <p class="conflict-desc"><strong>Task:</strong> {conflict.taskDescription}</p>
                <code class="conflict-files">Files: {conflict.conflictingFiles || 'unknown'}</code>
                <span class="badge offline text-xs mt-1">{conflict.resolutionStatus}</span>
              </div>
            {:else}
              <p class="empty-state">No active merge conflicts.</p>
            {/each}
          </div>
        </div>
      </section>

    </div>

    <!-- BLOCK 3: Role Metrics -->
    <section class="roles-metrics-section">
      <h3>BARCAN Council Readiness</h3>
      <p class="section-desc">Doctrine satisfaction for all 12 BARCAN roles, separated from execution workload.</p>

      {#if metrics.emsMetrics?.data?.roleDoctrineReadiness}
        <div class="role-readiness-summary">
          <div>
            <span class="label-xs">Readiness</span>
            <strong>{score(metrics.emsMetrics.data.roleDoctrineReadiness.readinessScore)}</strong>
          </div>
          <div>
            <span class="label-xs">Satisfied</span>
            <strong>{metrics.emsMetrics.data.roleDoctrineReadiness.satisfied}</strong>
          </div>
          <div>
            <span class="label-xs">Almost</span>
            <strong>{metrics.emsMetrics.data.roleDoctrineReadiness.almostSatisfied}</strong>
          </div>
          <div>
            <span class="label-xs">Objects</span>
            <strong>{metrics.emsMetrics.data.roleDoctrineReadiness.objects}</strong>
          </div>
          <div>
            <span class="label-xs">Refuses</span>
            <strong>{metrics.emsMetrics.data.roleDoctrineReadiness.refuses}</strong>
          </div>
          <div>
            <span class="label-xs">Unknown</span>
            <strong>{metrics.emsMetrics.data.roleDoctrineReadiness.unknown}</strong>
          </div>
        </div>

        <p class="council-interpretation">{metrics.emsMetrics.data.roleDoctrineReadiness.interpretation}</p>

        <div class="roles-grid">
          {#each metrics.emsMetrics.data.roleDoctrineReadiness.roles as role}
            <article class="role-metric-card doctrine {role.stance}">
              <div class="role-header">
                <span class="role-tag-badge">{role.roleTag}</span>
                <span class="role-status-badge {role.stance}">{stanceLabel(role.stance)}</span>
              </div>
              <h4>{role.doctrineName}</h4>
              <p class="role-focus">{role.doctrineFocus}</p>
              <div class="role-score-row">
                <div class="role-score-track">
                  <div class="role-score-fill {role.stance}" style="width: {scoreWidth(role.satisfactionScore)}"></div>
                </div>
                <strong>{score(role.satisfactionScore)}</strong>
              </div>
              <div class="role-body">
                <p class="label-xs">Doctrine pressure:</p>
                <p class="role-metric-name">{pressureLabel(role.kanoPressure)} / {role.cynefinBias}</p>
                <p class="label-xs mt-2">Evidence:</p>
                <p class="role-metric-value">Source {role.sourceWishlistPending}/{role.sourceWishlistTotal} · Owner {role.ownerTasksDone}/{role.ownerTasksTotal} · Confidence {percent(role.confidence)}</p>
                <p class="role-objection">{role.topObjection}</p>
              </div>
            </article>
          {/each}
        </div>
      {:else}
        <p class="empty-state">Role doctrine readiness is not available yet.</p>
      {/if}
    </section>
  {/if}
</div>

<style>
  .metrics-root {
    display: flex;
    flex-direction: column;
    gap: var(--space-6);
  }
  .section-title h2 {
    font-size: 24px;
    font-weight: 800;
    color: var(--neutral-800);
  }
  .section-desc {
    font-size: 14px;
    color: var(--neutral-500);
    margin-top: 4px;
  }

  .metrics-grid {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(400px, 1fr));
    gap: var(--space-6);
  }

  .metric-card {
    background: var(--surface);
    border: 1px solid var(--neutral-200);
    border-radius: 12px;
    padding: var(--space-6);
  }
  .metric-card h3 {
    font-size: 18px;
    font-weight: 700;
    color: var(--neutral-800);
    margin: 0 0 var(--space-1) 0;
  }
  .card-subtitle {
    font-size: 12px;
    color: var(--neutral-500);
    margin-bottom: var(--space-6);
  }

  .stats-subgrid {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: var(--space-4);
    margin-bottom: var(--space-6);
  }
  .stat-item {
    background: var(--neutral-50);
    border: 1px solid var(--neutral-100);
    border-radius: 8px;
    padding: var(--space-3);
    text-align: center;
    display: flex;
    flex-direction: column;
    justify-content: center;
  }
  .stat-number {
    font-size: 28px;
    font-weight: 800;
  }
  .stat-label {
    font-size: 11px;
    color: var(--neutral-500);
    margin-top: 4px;
    font-weight: 600;
  }

  .text-blue { color: var(--primary); }
  .text-indigo { color: var(--accent); }
  .text-yellow { color: var(--warning); }
  .text-green { color: var(--success); }
  .text-red { color: var(--error); }

  .linear-completeness-box {
    border-top: 1px solid var(--neutral-200);
    padding-top: var(--space-4);
  }
  .linear-completeness-box h4 {
    font-size: 14px;
    font-weight: 700;
    margin-bottom: var(--space-2);
  }
  .progress-bar-container {
    height: 8px;
    background: var(--neutral-200);
    border-radius: 4px;
    overflow: hidden;
    margin-top: var(--space-2);
  }
  .progress-bar-fill {
    height: 100%;
    background: var(--primary);
    border-radius: 4px;
  }

  .dpmo-subgrid {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: var(--space-4);
    margin-bottom: var(--space-6);
  }
  .dpmo-box {
    background: var(--neutral-50);
    border: 1px solid var(--neutral-100);
    border-radius: 8px;
    padding: var(--space-4);
    text-align: center;
    display: flex;
    flex-direction: column;
    align-items: center;
  }

  .active-conflicts-section {
    border-top: 1px solid var(--neutral-200);
    padding-top: var(--space-4);
  }
  .active-conflicts-section h4 {
    font-size: 14px;
    font-weight: 700;
    margin-bottom: var(--space-3);
  }
  .conflicts-list {
    display: flex;
    flex-direction: column;
    gap: var(--space-3);
    max-height: 180px;
    overflow-y: auto;
  }
  .conflict-item {
    background: #fffbeb;
    border: 1px solid #fef3c7;
    border-radius: 6px;
    padding: var(--space-3);
  }
  .conflict-desc {
    font-size: 12px;
    color: #92400e;
  }
  .conflict-files {
    font-family: monospace;
    font-size: 10px;
    background: rgba(0,0,0,0.05);
    padding: 1px 4px;
    border-radius: 3px;
    display: inline-block;
    margin-top: 4px;
  }

  .roles-metrics-section {
    margin-top: var(--space-8);
  }
  .role-readiness-summary {
    display: grid;
    grid-template-columns: repeat(6, minmax(0, 1fr));
    gap: var(--space-3);
    margin-top: var(--space-4);
  }
  .role-readiness-summary > div {
    background: var(--surface);
    border: 1px solid var(--neutral-200);
    border-radius: 8px;
    padding: var(--space-3);
  }
  .role-readiness-summary strong {
    display: block;
    font-size: 22px;
    color: var(--neutral-800);
    line-height: 1.1;
  }
  .council-interpretation {
    margin-top: var(--space-3);
    color: var(--neutral-600);
    font-size: 13px;
    line-height: 1.45;
  }
  .roles-grid {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
    gap: var(--space-4);
    margin-top: var(--space-4);
  }
  .role-metric-card {
    background: var(--surface);
    border: 1px solid var(--neutral-200);
    border-radius: 10px;
    padding: var(--space-4);
    display: flex;
    flex-direction: column;
    gap: var(--space-2);
  }
  .role-metric-card.doctrine {
    border-left: 4px solid var(--neutral-300);
  }
  .role-metric-card.doctrine.satisfied {
    border-left-color: #0d9488;
  }
  .role-metric-card.doctrine.almost_satisfied {
    border-left-color: #f59e0b;
  }
  .role-metric-card.doctrine.objects {
    border-left-color: #ea580c;
  }
  .role-metric-card.doctrine.refuses {
    border-left-color: #dc2626;
  }
  .role-metric-card.doctrine.unknown {
    border-left-color: #4f46e5;
  }
  .role-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: var(--space-2);
  }
  .role-tag-badge {
    font-size: 9px;
    font-weight: 800;
    background: var(--neutral-100);
    color: var(--neutral-600);
    padding: 2px 6px;
    border-radius: 4px;
  }
  .role-status-badge {
    font-size: 9px;
    font-weight: 800;
    background: #ecfdf5;
    color: #047857;
    padding: 2px 6px;
    border-radius: 4px;
  }
  .role-status-badge.satisfied {
    background: #d1fae5;
    color: #065f46;
  }
  .role-status-badge.almost_satisfied,
  .role-status-badge.objects {
    background: #fef3c7;
    color: #92400e;
  }
  .role-status-badge.refuses {
    background: #fee2e2;
    color: #991b1b;
  }
  .role-status-badge.unknown {
    background: #e0e7ff;
    color: #3730a3;
  }
  .role-metric-card h4 {
    font-size: 14px;
    font-weight: 700;
    color: var(--neutral-800);
    margin: var(--space-1) 0;
  }
  .role-focus {
    min-height: 36px;
    color: var(--neutral-600);
    font-size: 12px;
    line-height: 1.35;
  }
  .role-score-row {
    display: grid;
    grid-template-columns: 1fr auto;
    gap: var(--space-2);
    align-items: center;
  }
  .role-score-track {
    height: 8px;
    background: var(--neutral-100);
    border-radius: 999px;
    overflow: hidden;
  }
  .role-score-fill {
    height: 100%;
    border-radius: inherit;
  }
  .role-score-fill.satisfied {
    background: #0d9488;
  }
  .role-score-fill.almost_satisfied {
    background: #f59e0b;
  }
  .role-score-fill.objects {
    background: #ea580c;
  }
  .role-score-fill.refuses {
    background: #dc2626;
  }
  .role-score-fill.unknown {
    background: #4f46e5;
  }
  .role-metric-name {
    font-size: 13px;
    font-weight: 600;
    color: var(--neutral-700);
  }
  .role-metric-value {
    font-size: 15px;
    font-weight: 800;
    color: var(--primary);
  }
  .role-objection {
    margin-top: var(--space-2);
    color: var(--neutral-600);
    font-size: 11px;
    line-height: 1.4;
  }

  @media (max-width: 900px) {
    .role-readiness-summary {
      grid-template-columns: repeat(3, minmax(0, 1fr));
    }
  }

  @media (max-width: 620px) {
    .metrics-grid,
    .role-readiness-summary {
      grid-template-columns: 1fr;
    }
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
</style>
