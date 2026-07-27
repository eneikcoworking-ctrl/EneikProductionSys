<script lang="ts">
  import { onMount, onDestroy } from 'svelte';
  import type { SixSigmaAuditReport, KaizenProposalDto, ProjectSummary } from '../lib/types';
  import { projectsStore, currentDashboardStore } from '../lib/dashboardStore';

  const API_BASE = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';

  let sixSigmaReport: SixSigmaAuditReport | null = null;
  let proposals: KaizenProposalDto[] = [];
  let availableProjects: ProjectSummary[] = [];
  let selectedProjectId: string = '';
  let loading = true;
  let scanning = false;
  let executingId: string | null = null;
  let errorMsg = '';
  let pollInterval: ReturnType<typeof setInterval> | null = null;

  $: {
    availableProjects = $projectsStore || [];
    const activeDash = $currentDashboardStore;
    if (activeDash && activeDash.project && !selectedProjectId) {
      selectedProjectId = activeDash.project.id;
    }
  }

  async function loadData() {
    try {
      errorMsg = '';
      const url = selectedProjectId
        ? `${API_BASE}/api/audit/six-sigma?projectId=${selectedProjectId}`
        : `${API_BASE}/api/audit/six-sigma`;

      const [sixSigmaRes, kaizenRes] = await Promise.all([
        fetch(url),
        fetch(`${API_BASE}/api/kaizen/history`)
      ]);

      if (sixSigmaRes.ok) {
        sixSigmaReport = await sixSigmaRes.json();
      }
      if (kaizenRes.ok) {
        proposals = await kaizenRes.json();
      }
    } catch (e: any) {
      errorMsg = e?.message || 'Failed to fetch Six Sigma & Kaizen telemetry';
    } finally {
      loading = false;
    }
  }

  function onProjectScopeChange(e: Event) {
    const target = e.target as HTMLSelectElement;
    selectedProjectId = target.value;
    loadData();
  }

  async function triggerKaizenScan() {
    scanning = true;
    try {
      const res = await fetch(`${API_BASE}/api/kaizen/scan`, { method: 'POST' });
      if (res.ok) {
        await loadData();
      }
    } catch (e: any) {
      errorMsg = e?.message || 'Failed to scan for Kaizen opportunities';
    } finally {
      scanning = false;
    }
  }

  async function executeKaizenStep(proposalId: string) {
    executingId = proposalId;
    try {
      const res = await fetch(`${API_BASE}/api/kaizen/step/${proposalId}`, { method: 'POST' });
      if (res.ok) {
        await loadData();
      }
    } catch (e: any) {
      errorMsg = e?.message || 'Failed to execute Kaizen micro-step';
    } finally {
      executingId = null;
    }
  }

  onMount(() => {
    loadData();
    pollInterval = setInterval(loadData, 8000);
  });

  onDestroy(() => {
    if (pollInterval) clearInterval(pollInterval);
  });
</script>

<div class="six-sigma-kaizen-panel">
  <div class="header-banner">
    <div class="title-group">
      <span class="badge-tag">Lean &amp; Six Sigma Governance</span>
      <h2>Six Sigma Quality Audit &amp; Kaizen Micro-Improvements</h2>
      <p class="subtitle">Continuous Quality Control (DPMO, Yield %) &amp; Autonomous PDCA Micro-Optimization Cycle</p>
    </div>
    <div class="action-buttons">
      <div class="scope-selector-group">
        <label for="six-sigma-project-scope" class="scope-label">Scope Audit:</label>
        <select
          id="six-sigma-project-scope"
          class="scope-select"
          value={selectedProjectId}
          onchange={onProjectScopeChange}
        >
          <option value="">🏭 Factory-Wide Engine Audit (All Projects)</option>
          {#each availableProjects as proj}
            <option value={proj.id}>📦 Project: {proj.name}</option>
          {/each}
        </select>
      </div>

      <button class="btn-scan" onclick={triggerKaizenScan} disabled={scanning}>
        {scanning ? '🔍 Scanning...' : '🔍 Scan Kaizen Opportunities'}
      </button>
      <button class="btn-refresh" onclick={loadData}>
        ↻ Refresh
      </button>
    </div>
  </div>

  {#if loading && !sixSigmaReport}
    <div class="loading-state">
      <div class="spinner"></div>
      <p>Loading Six Sigma &amp; Kaizen Telemetry...</p>
    </div>
  {:else}
    {#if errorMsg}
      <div class="error-banner">⚠️ {errorMsg}</div>
    {/if}

    <!-- Scope Benchmark Banner -->
    {#if sixSigmaReport?.projectId && sixSigmaReport?.factoryWideBenchmark}
      <div class="benchmark-banner">
        <span class="banner-icon">📊</span>
        <div class="banner-text">
          <strong>Active Scope: {sixSigmaReport.projectName}</strong>
          <span>
            Project DPMO: <strong>{sixSigmaReport.dpmo.toFixed(1)}</strong> vs Factory Benchmark DPMO: <strong>{sixSigmaReport.factoryWideBenchmark.factoryDpmo?.toFixed(1) || '0.0'}</strong> (Factory Sigma: <strong>{sixSigmaReport.factoryWideBenchmark.factorySigmaLevel?.toFixed(2) || '6.0'}&sigma;</strong>)
          </span>
        </div>
      </div>
    {/if}

    <!-- Section 1: Six Sigma Metrics Overview -->
    <div class="section-container">
      <h3>Six Sigma Quality Metrics &amp; Defect Analysis</h3>
      
      <div class="metrics-grid">
        <!-- Sigma Level Card -->
        <div class="metric-card sigma-card">
          <span class="card-label">Sigma Level (Z-score)</span>
          <div class="card-value">
            {sixSigmaReport?.sigmaLevel ? sixSigmaReport.sigmaLevel.toFixed(2) : '6.00'}&sigma;
          </div>
          <span class="tier-badge" class:good={sixSigmaReport?.sigmaLevel && sixSigmaReport.sigmaLevel >= 3.0}>
            {sixSigmaReport?.qualityTier || 'SIX_SIGMA_WORLD_CLASS'}
          </span>
        </div>

        <!-- DPMO Card -->
        <div class="metric-card dpmo-card">
          <span class="card-label">DPMO (Defects Per Million)</span>
          <div class="card-value">
            {sixSigmaReport?.dpmo ? sixSigmaReport.dpmo.toLocaleString('en-US', { maximumFractionDigits: 1 }) : '0'}
          </div>
          <span class="subtext">Opportunities: {sixSigmaReport?.totalOpportunities || 0} | Defects: {sixSigmaReport?.totalDefects || 0}</span>
        </div>

        <!-- First Time Yield Card -->
        <div class="metric-card yield-card">
          <span class="card-label">First Time Yield Rate</span>
          <div class="card-value">
            {sixSigmaReport?.yieldRatePercent ? sixSigmaReport.yieldRatePercent.toFixed(1) : '100.0'}%
          </div>
          <div class="progress-bar-bg">
            <div 
              class="progress-bar-fill" 
              style="width: {Math.min(100, sixSigmaReport?.yieldRatePercent || 100)}%"
            ></div>
          </div>
        </div>
      </div>

      <!-- Defect Breakdown Table -->
      {#if sixSigmaReport?.defectBreakdown}
        <div class="breakdown-container">
          <h4>Defect Opportunities Channel Breakdown</h4>
          <table class="breakdown-table">
            <thead>
              <tr>
                <th>Channel / Subsystem</th>
                <th>Opportunities</th>
                <th>Defects Recorded</th>
                <th>Channel DPMO</th>
              </tr>
            </thead>
            <tbody>
              {#each Object.entries(sixSigmaReport.defectBreakdown) as [channel, data]}
                <tr>
                  <td class="channel-name">{channel}</td>
                  <td>{data.opportunities}</td>
                  <td class:has-defects={data.defects > 0}>{data.defects}</td>
                  <td>{data.dpmo.toFixed(1)}</td>
                </tr>
              {/each}
            </tbody>
          </table>
        </div>
      {/if}
    </div>

    <!-- Section 2: Kaizen Continuous Micro-Improvement (PDCA) -->
    <div class="section-container">
      <div class="kaizen-header">
        <div>
          <h3>Kaizen Micro-Improvement Engine (PDCA Cycle)</h3>
          <p class="section-subtitle">Plan-Do-Check-Act: Autonomous search for Muda (waste) &amp; incremental bounded optimizations.</p>
        </div>
      </div>

      {#if proposals.length === 0}
        <div class="empty-state">
          <span>🌱</span>
          <p>No open Kaizen micro-improvements. Click "Scan Kaizen Opportunities" to analyze telemetry for waste reduction.</p>
        </div>
      {:else}
        <div class="proposals-grid">
          {#each proposals as proposal}
            <div class="proposal-card" class:standardized={proposal.status === 'STANDARDIZED'} class:applied={proposal.status === 'APPLIED'}>
              <div class="proposal-top">
                <span class="category-badge {proposal.category.toLowerCase()}">{proposal.category.replace('_', ' ')}</span>
                <span class="status-badge {proposal.status.toLowerCase()}">{proposal.status}</span>
              </div>

              <h4 class="proposal-title">{proposal.title}</h4>
              <p class="proposal-desc">{proposal.actionDescription}</p>

              <div class="proposal-metrics">
                <span>Target: <strong>{proposal.targetComponent}</strong></span>
                <span>Expected Gain: <strong class="gain">+{proposal.expectedGainPercent}%</strong></span>
              </div>

              {#if proposal.baselineMetric !== undefined || proposal.postMetric !== undefined}
                <div class="pdca-metric-comparison">
                  <span>Baseline: <strong>{proposal.baselineMetric ?? 'N/A'}</strong></span>
                  <span>→ Post-Step: <strong>{proposal.postMetric ?? 'Measuring...'}</strong></span>
                </div>
              {/if}

              <div class="proposal-footer">
                <span class="created-time">{new Date(proposal.createdAt).toLocaleTimeString()}</span>
                
                {#if proposal.status === 'PROPOSED'}
                  <button 
                    class="btn-apply" 
                    onclick={() => executeKaizenStep(proposal.id)}
                    disabled={executingId === proposal.id}
                  >
                    {executingId === proposal.id ? 'Applying...' : '⚡ Apply Micro-Step (PDCA)'}
                  </button>
                {:else if proposal.status === 'STANDARDIZED'}
                  <span class="verified-tag">✓ Verified &amp; Standardized</span>
                {/if}
              </div>
            </div>
          {/each}
        </div>
      {/if}
    </div>
  {/if}
</div>

<style>
  .six-sigma-kaizen-panel {
    display: flex;
    flex-direction: column;
    gap: 1.5rem;
    padding: 1.5rem;
    color: #e2e8f0;
  }

  .header-banner {
    display: flex;
    justify-content: space-between;
    align-items: center;
    background: linear-gradient(135deg, #1e1b4b 0%, #0f172a 100%);
    border: 1px solid #3730a3;
    border-radius: 12px;
    padding: 1.5rem;
  }

  .badge-tag {
    font-size: 0.75rem;
    font-weight: 700;
    text-transform: uppercase;
    letter-spacing: 0.05em;
    color: #a5b4fc;
    background: rgba(165, 180, 252, 0.1);
    padding: 0.25rem 0.6rem;
    border-radius: 6px;
    border: 1px solid rgba(165, 180, 252, 0.2);
  }

  .title-group h2 {
    margin: 0.5rem 0 0.25rem 0;
    font-size: 1.5rem;
    font-weight: 700;
    color: #f8fafc;
  }

  .subtitle {
    margin: 0;
    color: #94a3b8;
    font-size: 0.875rem;
  }

  .scope-selector-group {
    display: flex;
    align-items: center;
    gap: 0.5rem;
  }

  .scope-label {
    font-size: 0.8rem;
    font-weight: 600;
    color: #94a3b8;
  }

  .scope-select {
    background: #0f172a;
    color: #f8fafc;
    border: 1px solid #334155;
    border-radius: 6px;
    padding: 0.4rem 0.8rem;
    font-size: 0.85rem;
    font-weight: 600;
    cursor: pointer;
  }

  .scope-select:focus {
    outline: 2px solid #38bdf8;
  }

  .benchmark-banner {
    display: flex;
    align-items: center;
    gap: 0.75rem;
    background: rgba(56, 189, 248, 0.1);
    border: 1px solid rgba(56, 189, 248, 0.3);
    border-radius: 8px;
    padding: 0.75rem 1rem;
    margin-bottom: 1rem;
    color: #e0f2fe;
    font-size: 0.88rem;
  }

  .banner-icon {
    font-size: 1.2rem;
  }

  .banner-text {
    display: flex;
    flex-direction: column;
    gap: 0.2rem;
  }

  .proposal-footer {
    display: flex;
    justify-content: flex-end;
  }

  .btn-execute {
    background: #3b82f6;
    color: #fff;
    border: none;
    padding: 0.4rem 0.8rem;
    border-radius: 6px;
    font-size: 0.8rem;
    font-weight: 600;
    cursor: pointer;
    transition: background 0.2s;
  }

  .btn-execute:hover:not(:disabled) {
    background: #2563eb;
  }

  .btn-execute:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }

  .action-buttons {
    display: flex;
    gap: 0.75rem;
  }

  .btn-scan {
    background: #6366f1;
    color: #fff;
    border: none;
    padding: 0.6rem 1.2rem;
    border-radius: 8px;
    font-weight: 600;
    cursor: pointer;
    transition: background 0.2s;
  }
  .btn-scan:hover:not(:disabled) { background: #4f46e5; }
  .btn-scan:disabled { opacity: 0.6; cursor: not-allowed; }

  .btn-refresh {
    background: #334155;
    color: #e2e8f0;
    border: 1px solid #475569;
    padding: 0.6rem 1rem;
    border-radius: 8px;
    font-weight: 600;
    cursor: pointer;
  }

  .section-container {
    background: #1e293b;
    border: 1px solid #334155;
    border-radius: 12px;
    padding: 1.5rem;
    display: flex;
    flex-direction: column;
    gap: 1.25rem;
  }

  .section-container h3 {
    margin: 0;
    font-size: 1.15rem;
    color: #f8fafc;
  }

  .section-subtitle {
    margin: 0.25rem 0 0 0;
    font-size: 0.85rem;
    color: #94a3b8;
  }

  .metrics-grid {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
    gap: 1.25rem;
  }

  .metric-card {
    background: #0f172a;
    border: 1px solid #334155;
    border-radius: 10px;
    padding: 1.25rem;
    display: flex;
    flex-direction: column;
    gap: 0.5rem;
  }

  .card-label {
    font-size: 0.85rem;
    font-weight: 600;
    color: #94a3b8;
  }

  .card-value {
    font-size: 2rem;
    font-weight: 800;
    color: #f8fafc;
  }

  .subtext {
    font-size: 0.8rem;
    color: #64748b;
  }

  .tier-badge {
    align-self: flex-start;
    font-size: 0.75rem;
    font-weight: 700;
    padding: 0.25rem 0.6rem;
    border-radius: 4px;
    background: #334155;
    color: #cbd5e1;
  }

  .tier-badge.good {
    background: rgba(16, 185, 129, 0.2);
    color: #34d399;
  }

  .progress-bar-bg {
    width: 100%;
    height: 8px;
    background: #334155;
    border-radius: 4px;
    overflow: hidden;
  }

  .progress-bar-fill {
    height: 100%;
    background: #10b981;
  }

  .breakdown-container h4 {
    margin: 1rem 0 0.5rem 0;
    font-size: 0.95rem;
    color: #cbd5e1;
  }

  .breakdown-table {
    width: 100%;
    border-collapse: collapse;
    font-size: 0.875rem;
  }

  .breakdown-table th, .breakdown-table td {
    padding: 0.65rem 1rem;
    text-align: left;
    border-bottom: 1px solid #334155;
  }

  .breakdown-table th {
    background: #0f172a;
    color: #94a3b8;
  }

  .channel-name { font-weight: 600; color: #38bdf8; }
  .has-defects { color: #f87171; font-weight: 700; }

  .proposals-grid {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
    gap: 1.25rem;
  }

  .proposal-card {
    background: #0f172a;
    border: 1px solid #334155;
    border-radius: 10px;
    padding: 1.25rem;
    display: flex;
    flex-direction: column;
    gap: 0.75rem;
  }

  .proposal-card.standardized {
    border-color: #10b981;
    background: linear-gradient(135deg, #0f172a 0%, #064e3b 100%);
  }

  .proposal-top {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  .category-badge {
    font-size: 0.7rem;
    font-weight: 700;
    padding: 0.2rem 0.5rem;
    border-radius: 4px;
    background: #334155;
    color: #cbd5e1;
    text-transform: uppercase;
  }

  .category-badge.waste_reduction { background: rgba(239, 68, 68, 0.2); color: #f87171; }
  .category-badge.buffer_tuning { background: rgba(245, 158, 11, 0.2); color: #fbbf24; }
  .category-badge.defect_elimination { background: rgba(168, 85, 247, 0.2); color: #c084fc; }

  .status-badge {
    font-size: 0.7rem;
    font-weight: 800;
    padding: 0.2rem 0.5rem;
    border-radius: 4px;
    background: #3b82f6;
    color: #fff;
  }

  .status-badge.standardized { background: #10b981; }
  .status-badge.reverted { background: #64748b; }

  .proposal-title {
    margin: 0;
    font-size: 1rem;
    font-weight: 700;
    color: #f8fafc;
  }

  .proposal-desc {
    margin: 0;
    font-size: 0.85rem;
    color: #94a3b8;
    line-height: 1.4;
  }

  .proposal-metrics {
    display: flex;
    justify-content: space-between;
    font-size: 0.8rem;
    color: #cbd5e1;
    border-top: 1px dashed #334155;
    padding-top: 0.5rem;
  }

  .gain { color: #34d399; }

  .pdca-metric-comparison {
    display: flex;
    gap: 1rem;
    font-size: 0.8rem;
    background: #1e293b;
    padding: 0.4rem 0.6rem;
    border-radius: 6px;
    color: #93c5fd;
  }

  .proposal-footer {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-top: auto;
    padding-top: 0.5rem;
  }

  .created-time {
    font-size: 0.75rem;
    color: #64748b;
  }

  .btn-apply {
    background: #10b981;
    color: #022c22;
    border: none;
    padding: 0.5rem 0.9rem;
    border-radius: 6px;
    font-weight: 700;
    font-size: 0.8rem;
    cursor: pointer;
    transition: background 0.2s;
  }

  .btn-apply:hover:not(:disabled) { background: #059669; color: #fff; }

  .verified-tag {
    font-size: 0.8rem;
    font-weight: 700;
    color: #34d399;
  }

  .empty-state {
    padding: 2.5rem;
    text-align: center;
    background: #0f172a;
    border-radius: 10px;
    color: #94a3b8;
  }

  .empty-state span { font-size: 2rem; }

  .loading-state, .error-banner {
    padding: 3rem;
    text-align: center;
    background: #1e293b;
    border-radius: 12px;
  }
</style>
