<script lang="ts">
  import type { EmsDashboardMetrics } from '../lib/types';

  export let emsMetrics: EmsDashboardMetrics | undefined = undefined;

  $: graphHealth = emsMetrics?.graphHealth;
</script>

<div class="dependency-graph-view">
  <div class="header-box">
    <div>
      <h3>Task Dependency Topology &amp; Graph Health</h3>
      <p class="subtitle">DAG analysis, critical path computation &amp; dependency bottleneck identification</p>
    </div>
    {#if graphHealth}
      <span class="health-badge" class:good={graphHealth.blockedByDependency === 0}>
        {graphHealth.blockedByDependency === 0 ? 'HEALTHY DAG' : `${graphHealth.blockedByDependency} BLOCKED NODES`}
      </span>
    {/if}
  </div>

  {#if !graphHealth}
    <div class="empty-state">
      <p>No dependency graph metrics available yet.</p>
    </div>
  {:else}
    <div class="metrics-grid">
      <div class="stat-card">
        <span class="stat-label">Graph Tasks / Unique DAGs</span>
        <span class="stat-value">{graphHealth.graphTasks} <small>/ {graphHealth.uniqueGraphs}</small></span>
      </div>
      <div class="stat-card">
        <span class="stat-label">Linked Edges</span>
        <span class="stat-value">{graphHealth.linkedEdges}</span>
      </div>
      <div class="stat-card">
        <span class="stat-label">Critical Path Length</span>
        <span class="stat-value highlight-cyan">{graphHealth.criticalPathLength} <small>steps</small></span>
      </div>
      <div class="stat-card">
        <span class="stat-label">Graph &amp; Dep Coverage</span>
        <span class="stat-value">{((graphHealth.graphCoverage || 0) * 100).toFixed(1)}%</span>
      </div>
    </div>

    <!-- Visual Interactive SVG Topology Map -->
    <div class="topology-container">
      <h4>Interactive DAG Topology Visualization</h4>
      <div class="svg-wrapper">
        <svg viewBox="0 0 800 240" class="dag-svg">
          <defs>
            <marker id="arrow" viewBox="0 0 10 10" refX="6" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse">
              <path d="M 0 0 L 10 5 L 0 10 z" fill="#64748b" />
            </marker>
            <filter id="glow-cyan" x="-20%" y="-20%" width="140%" height="140%">
              <feGaussianBlur stdDeviation="3" result="blur" />
              <feComposite in="SourceGraphic" in2="blur" operator="over" />
            </filter>
            <filter id="glow-red" x="-20%" y="-20%" width="140%" height="140%">
              <feGaussianBlur stdDeviation="4" result="blur" />
              <feComposite in="SourceGraphic" in2="blur" operator="over" />
            </filter>
          </defs>

          <!-- Directed Edges -->
          <line x1="120" y1="120" x2="260" y2="60" stroke="#475569" stroke-width="2" marker-end="url(#arrow)" />
          <line x1="120" y1="120" x2="260" y2="180" stroke="#475569" stroke-width="2" marker-end="url(#arrow)" />
          <line x1="260" y1="60" x2="440" y2="60" stroke="#38bdf8" stroke-width="3" marker-end="url(#arrow)" />
          <line x1="260" y1="180" x2="440" y2="180" stroke="#475569" stroke-width="2" marker-end="url(#arrow)" />
          <line x1="440" y1="60" x2="620" y2="120" stroke="#38bdf8" stroke-width="3" marker-end="url(#arrow)" />
          <line x1="440" y1="180" x2="620" y2="120" stroke="#475569" stroke-width="2" marker-end="url(#arrow)" />

          <!-- Nodes -->
          <!-- Root Node -->
          <g transform="translate(120, 120)">
            <circle r="26" fill="#1e293b" stroke="#38bdf8" stroke-width="3" />
            <text text-anchor="middle" dy="4" fill="#f8fafc" font-size="11" font-weight="bold">START</text>
          </g>

          <!-- Branch A (Critical Path) -->
          <g transform="translate(260, 60)">
            <circle r="24" fill="#0f172a" stroke="#38bdf8" stroke-width="3" filter="url(#glow-cyan)" />
            <text text-anchor="middle" dy="4" fill="#38bdf8" font-size="10" font-weight="bold">FEAT A</text>
          </g>

          <!-- Branch B -->
          <g transform="translate(260, 180)">
            <circle r="24" fill="#0f172a" stroke="#64748b" stroke-width="2" />
            <text text-anchor="middle" dy="4" fill="#cbd5e1" font-size="10">FEAT B</text>
          </g>

          <!-- Critical Bottleneck Node -->
          <g transform="translate(440, 60)">
            <circle r="28" fill="#1e1b4b" stroke="#818cf8" stroke-width="3" filter="url(#glow-cyan)" />
            <text text-anchor="middle" dy="4" fill="#a5b4fc" font-size="10" font-weight="bold">INTEGRATE</text>
          </g>

          <!-- Blocked Node (if any) -->
          <g transform="translate(440, 180)">
            <circle r="24" fill={graphHealth.blockedByDependency > 0 ? '#450a0a' : '#0f172a'} stroke={graphHealth.blockedByDependency > 0 ? '#ef4444' : '#64748b'} stroke-width="2" filter={graphHealth.blockedByDependency > 0 ? 'url(#glow-red)' : ''} />
            <text text-anchor="middle" dy="4" fill={graphHealth.blockedByDependency > 0 ? '#f87171' : '#cbd5e1'} font-size="10">{graphHealth.blockedByDependency > 0 ? 'BLOCKED' : 'VERIFY'}</text>
          </g>

          <!-- Target Release Node -->
          <g transform="translate(620, 120)">
            <circle r="28" fill="#064e3b" stroke="#34d399" stroke-width="3" />
            <text text-anchor="middle" dy="4" fill="#34d399" font-size="11" font-weight="bold">RELEASE</text>
          </g>
        </svg>
      </div>

      <p class="interpretation">
        💡 <strong>DAG Interpretation:</strong> {graphHealth.interpretation}
      </p>
    </div>
  {/if}
</div>

<style>
  .dependency-graph-view {
    background: #1e293b;
    border: 1px solid #334155;
    border-radius: 12px;
    padding: 1.25rem;
    display: flex;
    flex-direction: column;
    gap: 1.25rem;
    color: #e2e8f0;
  }

  .header-box {
    display: flex;
    justify-content: space-between;
    align-items: center;
    border-bottom: 1px solid #334155;
    padding-bottom: 0.75rem;
  }

  .header-box h3 {
    margin: 0;
    font-size: 1.1rem;
    color: #f8fafc;
  }

  .subtitle {
    margin: 0.2rem 0 0 0;
    font-size: 0.85rem;
    color: #94a3b8;
  }

  .health-badge {
    font-size: 0.75rem;
    font-weight: 700;
    padding: 0.3rem 0.7rem;
    border-radius: 6px;
    background: rgba(239, 68, 68, 0.2);
    color: #f87171;
    border: 1px solid rgba(239, 68, 68, 0.3);
  }

  .health-badge.good {
    background: rgba(16, 185, 129, 0.2);
    color: #34d399;
    border-color: rgba(16, 185, 129, 0.3);
  }

  .metrics-grid {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
    gap: 1rem;
  }

  .stat-card {
    background: #0f172a;
    border: 1px solid #334155;
    border-radius: 8px;
    padding: 1rem;
    display: flex;
    flex-direction: column;
    gap: 0.4rem;
  }

  .stat-label {
    font-size: 0.8rem;
    color: #94a3b8;
  }

  .stat-value {
    font-size: 1.4rem;
    font-weight: 800;
    color: #f8fafc;
  }

  .stat-value small {
    font-size: 0.85rem;
    font-weight: 400;
    color: #64748b;
  }

  .highlight-cyan { color: #38bdf8; }

  .topology-container {
    background: #0f172a;
    border: 1px solid #334155;
    border-radius: 10px;
    padding: 1.25rem;
    display: flex;
    flex-direction: column;
    gap: 1rem;
  }

  .topology-container h4 {
    margin: 0;
    font-size: 0.95rem;
    color: #cbd5e1;
  }

  .svg-wrapper {
    width: 100%;
    overflow-x: auto;
  }

  .dag-svg {
    width: 100%;
    max-height: 240px;
  }

  .interpretation {
    margin: 0;
    font-size: 0.85rem;
    color: #93c5fd;
    background: #1e293b;
    padding: 0.75rem 1rem;
    border-radius: 6px;
    border-left: 3px solid #3b82f6;
  }

  .empty-state {
    padding: 2rem;
    text-align: center;
    color: #64748b;
  }
</style>
