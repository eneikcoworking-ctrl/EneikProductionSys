<script lang="ts">
  import { onMount, onDestroy } from 'svelte';
  import type { TocDbrStatus, TocGraphData, TocAnomalyReport } from '../lib/types';
  import { API_BASE } from '../lib/api';

  let dbrStatus: TocDbrStatus | null = null;
  let graphData: TocGraphData | null = null;
  let anomalies: TocAnomalyReport[] = [];
  let loading = true;
  let errorMsg = '';
  let pollInterval: ReturnType<typeof setInterval> | null = null;

  async function loadTocData() {
    try {
      errorMsg = '';
      const [statusRes, graphRes, anomaliesRes] = await Promise.all([
        fetch(`${API_BASE}/api/toc/status`),
        fetch(`${API_BASE}/api/toc/graph`),
        fetch(`${API_BASE}/api/toc/anomalies`)
      ]);

      if (statusRes.ok) {
        dbrStatus = await statusRes.json();
      }
      if (graphRes.ok) {
        graphData = await graphRes.json();
      }
      if (anomaliesRes.ok) {
        anomalies = await anomaliesRes.json();
      }
    } catch (e: any) {
      errorMsg = e?.message || 'Failed to fetch TOC Sentinel telemetry';
    } finally {
      loading = false;
    }
  }

  onMount(() => {
    loadTocData();
    pollInterval = setInterval(loadTocData, 5000);
  });

  onDestroy(() => {
    if (pollInterval) clearInterval(pollInterval);
  });
</script>

<div class="toc-sentinel-view">
  <div class="header-banner">
    <div class="title-group">
      <span class="badge-tag">Theory of Constraints</span>
      <span class="badge-tag badge-layer">🏭 Factory layer - all projects</span>
      <h2>TOC Sentinel Service &amp; DBR Operational Control</h2>
      <p class="subtitle">Deterministic State Machine, Dynamic Bottleneck Identification &amp; Real-time Anomaly Detection. This view is system-wide by design - it tracks the one execution graph the whole factory shares, not any single project.</p>
    </div>
    <button class="btn-refresh" onclick={loadTocData}>
      ↻ Refresh Telemetry
    </button>
  </div>

  {#if loading && !dbrStatus}
    <div class="loading-state">
      <div class="spinner"></div>
      <p>Fetching TOC Sentinel Graph Telemetry...</p>
    </div>
  {:else if errorMsg && !dbrStatus}
    <div class="error-banner">
      ⚠️ {errorMsg}
    </div>
  {:else}
    <!-- Summary Cards Grid -->
    <div class="cards-grid">
      <!-- Primary Constraint Card -->
      <div class="card constraint-card" class:has-constraint={dbrStatus?.primaryConstraintNode !== 'NONE'}>
        <div class="card-header">
          <span class="card-label">Primary Constraint (The Drum)</span>
          <span class="status-indicator" class:alert={dbrStatus?.primaryConstraintNode !== 'NONE'}>
            {dbrStatus?.primaryConstraintNode !== 'NONE' ? 'BOTTLENECK FOUND' : 'OPTIMAL'}
          </span>
        </div>
        <div class="card-value">
          {dbrStatus?.primaryConstraintNode || 'NONE'}
        </div>
        <div class="card-footer">
          <span>Mean Latency: {dbrStatus?.constraintMeanDurationMs ? dbrStatus.constraintMeanDurationMs.toFixed(1) : '0'} ms</span>
          <span>Utilization: {((dbrStatus?.constraintUtilization || 0) * 100).toFixed(1)}%</span>
        </div>
      </div>

      <!-- DBR Buffer & Rope Card -->
      <div class="card rope-card" class:throttled={dbrStatus?.ropeThrottlingActive}>
        <div class="card-header">
          <span class="card-label">Drum-Buffer-Rope Gate (The Rope)</span>
          <span class="status-badge" class:throttled={dbrStatus?.ropeThrottlingActive}>
            {dbrStatus?.ropeThrottlingActive ? 'ROPE THROTTLING ACTIVE' : 'NORMAL ADMISSIONS'}
          </span>
        </div>
        <div class="card-value">
          {dbrStatus?.bufferSize || 0} / {dbrStatus?.maxBufferCapacity || 15}
          <span class="unit">tokens in buffer</span>
        </div>
        <div class="progress-bar-bg">
          <div 
            class="progress-bar-fill" 
            class:danger={dbrStatus?.ropeThrottlingActive}
            style="width: {Math.min(100, (((dbrStatus?.bufferSize || 0) / (dbrStatus?.maxBufferCapacity || 15)) * 100))}%"
          ></div>
        </div>
      </div>

      <!-- Graph Arrival Rate Card -->
      <div class="card metrics-card">
        <div class="card-header">
          <span class="card-label">System Flow Rate (&lambda;)</span>
          <span class="card-sublabel">Active Nodes: {graphData?.nodeCount || 0}</span>
        </div>
        <div class="card-value">
          {(graphData?.arrivalRatePerSec || 0).toFixed(2)}
          <span class="unit">req / sec</span>
        </div>
        <div class="card-footer">
          <span>Active In-Flight Tokens: {graphData?.activeTokenCount || 0}</span>
        </div>
      </div>
    </div>

    {#if dbrStatus?.recommendation}
      <div class="recommendation-banner" class:warning={dbrStatus?.ropeThrottlingActive}>
        💡 <strong>TOC Sentinel Recommendation:</strong> {dbrStatus.recommendation}
      </div>
    {/if}

    <!-- State Machine Nodes Table -->
    <div class="section-container">
      <h3>Execution State Machine Nodes &amp; Statistics</h3>
      <table class="nodes-table">
        <thead>
          <tr>
            <th>Node Name</th>
            <th>In-Flight Queue ($L_q$)</th>
            <th>Completed</th>
            <th>Mean Latency ($\mu$)</th>
            <th>Std Deviation ($\sigma$)</th>
            <th>Utilization ($U$)</th>
            <th>TOC Status</th>
          </tr>
        </thead>
        <tbody>
          {#if !graphData || graphData.nodes.length === 0}
            <tr>
              <td colspan="7" class="empty-cell">No active execution nodes recorded in graph yet.</td>
            </tr>
          {:else}
            {#each graphData.nodes as node}
              <tr class:highlight-constraint={node.primaryConstraint} class:highlight-stall={node.stallBottleneck}>
                <td class="font-mono font-bold">{node.name}</td>
                <td>
                  <span class="queue-badge" class:has-queue={node.inFlightCount > 0}>
                    {node.inFlightCount}
                  </span>
                </td>
                <td>{node.completedCount}</td>
                <td>{node.meanDurationMs.toFixed(2)} ms</td>
                <td>{node.stdDevMs.toFixed(2)} ms</td>
                <td>{(node.utilization * 100).toFixed(1)}%</td>
                <td>
                  {#if node.primaryConstraint}
                    <span class="badge constraint-badge">PRIMARY CONSTRAINT</span>
                  {:else if node.stallBottleneck}
                    <span class="badge stall-badge">STALL BOTTLENECK</span>
                  {:else}
                    <span class="badge optimal-badge">OPTIMAL</span>
                  {/if}
                </td>
              </tr>
            {/each}
          {/if}
        </tbody>
      </table>
    </div>

    <!-- Anomaly Audit Log -->
    <div class="section-container">
      <h3>Real-Time Anomaly &amp; Interception Audit Log</h3>
      {#if anomalies.length === 0}
        <div class="empty-anomalies">
          ✅ Zero anomalies detected. Call stacks, durations, and resource locks are operating cleanly within normal bounds.
        </div>
      {:else}
        <div class="anomalies-list">
          {#each anomalies as anomaly}
            <div class="anomaly-item" class:cycle={anomaly.type === 'CYCLE_DETECTED'} class:deadlock={anomaly.type === 'DEADLOCK_DETECTED'}>
              <div class="anomaly-header">
                <span class="anomaly-type-badge">{anomaly.type}</span>
                <span class="anomaly-time">{new Date(anomaly.timestamp).toLocaleTimeString()}</span>
              </div>
              <p class="anomaly-details">{anomaly.details}</p>
              <div class="anomaly-action">
                🛡️ <strong>Sentinel Action:</strong> {anomaly.actionTaken}
              </div>
            </div>
          {/each}
        </div>
      {/if}
    </div>
  {/if}
</div>

<style>
  .toc-sentinel-view {
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
    background: linear-gradient(135deg, #1e293b 0%, #0f172a 100%);
    border: 1px solid #334155;
    border-radius: 12px;
    padding: 1.5rem;
  }

  .badge-tag {
    font-size: 0.75rem;
    font-weight: 700;
    text-transform: uppercase;
    letter-spacing: 0.05em;
    color: #38bdf8;
    background: rgba(56, 189, 248, 0.1);
    padding: 0.25rem 0.6rem;
    border-radius: 6px;
    border: 1px solid rgba(56, 189, 248, 0.2);
  }

  .badge-layer {
    color: #fbbf24;
    background: rgba(251, 191, 36, 0.1);
    border-color: rgba(251, 191, 36, 0.25);
    margin-left: 0.5rem;
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

  .btn-refresh {
    background: #3b82f6;
    color: #fff;
    border: none;
    padding: 0.6rem 1.2rem;
    border-radius: 8px;
    font-weight: 600;
    cursor: pointer;
    transition: background 0.2s;
  }

  .btn-refresh:hover {
    background: #2563eb;
  }

  .cards-grid {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
    gap: 1.25rem;
  }

  .card {
    background: #1e293b;
    border: 1px solid #334155;
    border-radius: 12px;
    padding: 1.25rem;
    display: flex;
    flex-direction: column;
    gap: 0.75rem;
  }

  .constraint-card.has-constraint {
    border-color: #ef4444;
    background: linear-gradient(135deg, #1e293b 0%, #450a0a 100%);
  }

  .rope-card.throttled {
    border-color: #f59e0b;
    background: linear-gradient(135deg, #1e293b 0%, #451a03 100%);
  }

  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  .card-label {
    font-size: 0.85rem;
    font-weight: 600;
    color: #94a3b8;
  }

  .status-indicator {
    font-size: 0.75rem;
    font-weight: 700;
    padding: 0.2rem 0.5rem;
    border-radius: 4px;
    background: #10b981;
    color: #022c22;
  }

  .status-indicator.alert {
    background: #ef4444;
    color: #fff;
  }

  .status-badge {
    font-size: 0.75rem;
    font-weight: 700;
    padding: 0.2rem 0.5rem;
    border-radius: 4px;
    background: rgba(16, 185, 129, 0.2);
    color: #34d399;
  }

  .status-badge.throttled {
    background: rgba(245, 158, 11, 0.2);
    color: #fbbf24;
  }

  .card-value {
    font-size: 1.75rem;
    font-weight: 800;
    color: #f8fafc;
  }

  .card-value .unit {
    font-size: 0.9rem;
    font-weight: 400;
    color: #94a3b8;
  }

  .card-footer {
    display: flex;
    justify-content: space-between;
    font-size: 0.8rem;
    color: #cbd5e1;
    border-top: 1px solid #334155;
    padding-top: 0.5rem;
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
    transition: width 0.3s ease;
  }

  .progress-bar-fill.danger {
    background: #f59e0b;
  }

  .recommendation-banner {
    background: #0f172a;
    border: 1px solid #3b82f6;
    border-radius: 8px;
    padding: 1rem 1.25rem;
    font-size: 0.9rem;
    color: #93c5fd;
  }

  .recommendation-banner.warning {
    border-color: #f59e0b;
    color: #fde68a;
  }

  .section-container {
    background: #1e293b;
    border: 1px solid #334155;
    border-radius: 12px;
    padding: 1.25rem;
  }

  .section-container h3 {
    margin: 0 0 1rem 0;
    font-size: 1.1rem;
    color: #f8fafc;
  }

  .nodes-table {
    width: 100%;
    border-collapse: collapse;
    font-size: 0.875rem;
  }

  .nodes-table th, .nodes-table td {
    padding: 0.75rem 1rem;
    text-align: left;
    border-bottom: 1px solid #334155;
  }

  .nodes-table th {
    background: #0f172a;
    color: #94a3b8;
    font-weight: 600;
  }

  .nodes-table tr.highlight-constraint {
    background: rgba(239, 68, 68, 0.15);
  }

  .nodes-table tr.highlight-stall {
    background: rgba(245, 158, 11, 0.15);
  }

  .badge {
    font-size: 0.7rem;
    font-weight: 700;
    padding: 0.2rem 0.5rem;
    border-radius: 4px;
  }

  .constraint-badge { background: #ef4444; color: #fff; }
  .stall-badge { background: #f59e0b; color: #fff; }
  .optimal-badge { background: #10b981; color: #022c22; }

  .queue-badge {
    padding: 0.15rem 0.4rem;
    border-radius: 4px;
    background: #334155;
    font-weight: 700;
  }

  .queue-badge.has-queue {
    background: #3b82f6;
    color: #fff;
  }

  .anomalies-list {
    display: flex;
    flex-direction: column;
    gap: 0.75rem;
  }

  .anomaly-item {
    background: #0f172a;
    border: 1px solid #334155;
    border-left: 4px solid #f59e0b;
    border-radius: 8px;
    padding: 1rem;
  }

  .anomaly-item.cycle { border-left-color: #ef4444; }
  .anomaly-item.deadlock { border-left-color: #ec4899; }

  .anomaly-header {
    display: flex;
    justify-content: space-between;
    margin-bottom: 0.4rem;
  }

  .anomaly-type-badge {
    font-size: 0.75rem;
    font-weight: 800;
    color: #f8fafc;
  }

  .anomaly-time {
    font-size: 0.75rem;
    color: #64748b;
  }

  .anomaly-details {
    margin: 0 0 0.5rem 0;
    font-size: 0.875rem;
    color: #cbd5e1;
  }

  .anomaly-action {
    font-size: 0.8rem;
    color: #38bdf8;
  }

  .empty-anomalies {
    padding: 1.5rem;
    text-align: center;
    background: #0f172a;
    border-radius: 8px;
    color: #34d399;
  }

  .loading-state, .error-banner {
    padding: 3rem;
    text-align: center;
    background: #1e293b;
    border-radius: 12px;
  }
</style>
