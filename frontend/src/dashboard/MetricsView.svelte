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
    const clamped = Math.max(0, Math.min(100, Math.round(value ?? 0)));
    return `${clamped}`;
  }

  function percent(value: number | undefined | null): string {
    const clamped = Math.max(0, Math.min(100, Math.round((value ?? 0) * 100)));
    return `${clamped}%`;
  }

  function scoreWidth(value: number | undefined | null): string {
    const clamped = Math.max(0, Math.min(100, Math.round(value ?? 0)));
    return `${clamped}%`;
  }

  function getSatisfactionColor(score: number | undefined | null): string {
    // Clamp before deriving hue/sat/light — an out-of-range score otherwise produces negative
    // saturation/lightness, an invalid CSS value the browser drops, rendering an invisible bar.
    const val = Math.max(0, Math.min(100, score ?? 0));
    const hue = 280 - (val / 100.0) * (280 - 140);
    const sat = 85 - (val / 100.0) * (85 - 75);
    const light = 45 - (val / 100.0) * (45 - 40);
    return `hsl(${hue}, ${sat}%, ${light}%)`;
  }

  function stanceLabel(value: string): string {
    return (value || 'unknown').replace(/_/g, ' ');
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

  function sigmaClass(value: number | undefined | null): string {
    const sigma = value ?? 0;
    if (sigma >= 5) return 'excellent';
    if (sigma >= 4) return 'controlled';
    if (sigma >= 3) return 'improve';
    return 'critical';
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
        <div class="quality-header">
          <div>
            <h3>Six Sigma Quality Control</h3>
            <p class="card-subtitle">CTQ defects, DPMO, sigma level, yield, and Pareto root-cause focus</p>
          </div>
          <span class="sigma-status {metrics.sixSigma?.data?.statusLabel || 'no_data'}">{metrics.sixSigma?.data?.statusLabel || 'no data'}</span>
        </div>

        {#if metrics.sixSigma?.data}
          <div class="sixsigma-summary">
            <div class="sigma-tile {sigmaClass(metrics.sixSigma.data.sigmaLevel)}">
              <span class="label-xs">Sigma level</span>
              <strong>{(metrics.sixSigma.data.sigmaLevel ?? 0).toFixed(2)}</strong>
            </div>
            <div>
              <span class="label-xs">Process yield</span>
              <strong>{percent(metrics.sixSigma.data.yieldRate)}</strong>
            </div>
            <div>
              <span class="label-xs">DPMO</span>
              <strong>{compactNumber(metrics.sixSigma.data.dpmo)}</strong>
            </div>
            <div>
              <span class="label-xs">COPQ proxy</span>
              <strong>{metrics.sixSigma.data.copqProxy}</strong>
            </div>
          </div>

          <div class="sixsigma-note">
            <strong>{metrics.sixSigma.data.recommendedAction}</strong>
            <p>{metrics.sixSigma.data.interpretation}</p>
          </div>

          <div class="ctq-grid">
            <div class="ctq-card">
              <div class="ctq-head">
                <strong>Quality Gate CTQ</strong>
                <span class="sigma-chip {sigmaClass(metrics.qualityGate?.data?.sigmaLevel)}">{(metrics.qualityGate?.data?.sigmaLevel ?? 0).toFixed(2)}σ</span>
              </div>
              <div class="ctq-stats">
                <span>{metrics.qualityGate?.data?.defects ?? 0} defects</span>
                <span>{metrics.qualityGate?.data?.totalOpportunities ?? 0} opportunities</span>
                <span>{percent(metrics.qualityGate?.data?.yieldRate)} yield</span>
              </div>
            </div>
            <div class="ctq-card">
              <div class="ctq-head">
                <strong>Merge Conflict CTQ</strong>
                <span class="sigma-chip {sigmaClass(metrics.conflictDpmo?.data?.sigmaLevel)}">{(metrics.conflictDpmo?.data?.sigmaLevel ?? 0).toFixed(2)}σ</span>
              </div>
              <div class="ctq-stats">
                <span>{metrics.conflictDpmo?.data?.conflicts ?? 0} conflicts</span>
                <span>{metrics.conflictDpmo?.data?.totalMergeAttempts ?? 0} merge attempts</span>
                <span>{percent(metrics.conflictDpmo?.data?.yieldRate)} yield</span>
              </div>
            </div>
          </div>

          <div class="pareto-section">
            <h4>CTQ Pareto</h4>
            <div class="pareto-list">
              {#each metrics.sixSigma.data.ctqPareto || [] as item}
                <div class="pareto-row">
                  <div class="pareto-label">
                    <strong>{item.ctq || item.name}</strong>
                    <span>{item.defects} defects · {compactNumber(item.dpmo)} DPMO</span>
                  </div>
                  <div class="pareto-track">
                    <div class="pareto-fill" style="width: {scoreWidth(((item.defects ?? 0) / Math.max(1, metrics.sixSigma.data.totalDefects || 1)) * 100)}"></div>
                  </div>
                </div>
              {:else}
                <p class="empty-state">No CTQ defects recorded yet.</p>
              {/each}
            </div>
          </div>
        {:else}
          <p class="empty-state">Six Sigma metrics are not available yet.</p>
        {/if}

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
      <p class="section-desc">Doctrine satisfaction for all 13 BARCAN roles, separated from execution workload.</p>

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
                  <div class="role-score-fill {role.stance}" style="width: {scoreWidth(role.satisfactionScore)}; background: {getSatisfactionColor(role.satisfactionScore)}"></div>
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
  .quality-header {
    display: flex;
    justify-content: space-between;
    gap: var(--space-3);
    align-items: flex-start;
    margin-bottom: var(--space-4);
  }
  .sigma-status {
    font-size: 10px;
    font-weight: 900;
    text-transform: uppercase;
    border-radius: 5px;
    padding: 4px 8px;
    white-space: nowrap;
  }
  .sigma-status.no_data {
    background: #e0e7ff;
    color: #3730a3;
  }
  .sigma-status.critical,
  .sigma-chip.critical {
    background: #fee2e2;
    color: #991b1b;
  }
  .sigma-status.improve,
  .sigma-chip.improve {
    background: #fef3c7;
    color: #92400e;
  }
  .sigma-status.controlled,
  .sigma-chip.controlled {
    background: #dbeafe;
    color: #1d4ed8;
  }
  .sigma-status.excellent,
  .sigma-chip.excellent {
    background: #d1fae5;
    color: #065f46;
  }
  .sixsigma-summary {
    display: grid;
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: var(--space-3);
    margin-bottom: var(--space-3);
  }
  .sixsigma-summary > div {
    background: var(--neutral-50);
    border: 1px solid var(--neutral-100);
    border-radius: 8px;
    padding: var(--space-3);
    min-width: 0;
  }
  .sixsigma-summary strong {
    display: block;
    font-size: 22px;
    line-height: 1.1;
    color: var(--neutral-800);
  }
  .sigma-tile.critical strong {
    color: #b91c1c;
  }
  .sigma-tile.improve strong {
    color: #b45309;
  }
  .sigma-tile.controlled strong {
    color: #1d4ed8;
  }
  .sigma-tile.excellent strong {
    color: #047857;
  }
  .sixsigma-note {
    background: #f8fafc;
    border: 1px solid var(--neutral-200);
    border-radius: 8px;
    padding: var(--space-3);
    margin-bottom: var(--space-4);
  }
  .sixsigma-note strong {
    display: block;
    color: var(--neutral-800);
    font-size: 13px;
    margin-bottom: 3px;
  }
  .sixsigma-note p {
    color: var(--neutral-600);
    font-size: 12px;
    line-height: 1.4;
  }
  .ctq-grid {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: var(--space-3);
    margin-bottom: var(--space-4);
  }
  .ctq-card {
    border: 1px solid var(--neutral-200);
    border-radius: 8px;
    padding: var(--space-3);
  }
  .ctq-head {
    display: flex;
    justify-content: space-between;
    gap: var(--space-2);
    align-items: center;
    margin-bottom: var(--space-2);
  }
  .ctq-head strong {
    color: var(--neutral-800);
    font-size: 13px;
  }
  .sigma-chip {
    border-radius: 4px;
    font-size: 10px;
    font-weight: 900;
    padding: 2px 6px;
    white-space: nowrap;
  }
  .ctq-stats {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: var(--space-2);
    color: var(--neutral-600);
    font-size: 11px;
  }
  .ctq-stats span {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .pareto-section {
    border-top: 1px solid var(--neutral-200);
    padding-top: var(--space-4);
    margin-bottom: var(--space-4);
  }
  .pareto-section h4 {
    font-size: 14px;
    font-weight: 700;
    margin-bottom: var(--space-3);
  }
  .pareto-list {
    display: flex;
    flex-direction: column;
    gap: var(--space-2);
    max-height: 180px;
    overflow-y: auto;
    padding-right: 2px;
  }
  .pareto-row {
    display: grid;
    grid-template-columns: minmax(150px, 1fr) 1.2fr;
    gap: var(--space-2);
    align-items: center;
  }
  .pareto-label {
    display: flex;
    flex-direction: column;
    gap: 1px;
    min-width: 0;
  }
  .pareto-label strong {
    color: var(--neutral-700);
    font-size: 11px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .pareto-label span {
    color: var(--neutral-500);
    font-size: 10px;
  }
  .pareto-track {
    height: 8px;
    background: var(--neutral-100);
    border-radius: 999px;
    overflow: hidden;
  }
  .pareto-fill {
    height: 100%;
    background: #dc2626;
    border-radius: inherit;
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
    .sixsigma-summary,
    .ctq-grid {
      grid-template-columns: repeat(2, minmax(0, 1fr));
    }
    .role-readiness-summary {
      grid-template-columns: repeat(3, minmax(0, 1fr));
    }
  }

  @media (max-width: 620px) {
    .metrics-grid,
    .sixsigma-summary,
    .ctq-grid,
    .pareto-row,
    .role-readiness-summary {
      grid-template-columns: 1fr;
    }
    .quality-header {
      flex-direction: column;
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
