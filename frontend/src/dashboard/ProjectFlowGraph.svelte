<script lang="ts">
  import type { EmsDashboardMetrics } from '../lib/types';

  export let emsMetrics: EmsDashboardMetrics | undefined = undefined;

  function percent(value: number | undefined | null): string {
    const clamped = Math.max(0, Math.min(100, Math.round((value ?? 0) * 100)));
    return `${clamped}%`;
  }

  function width(value: number | undefined | null): string {
    const clamped = Math.max(0, Math.min(100, Math.round((value ?? 0) * 100)));
    return `${clamped}%`;
  }
</script>

{#if emsMetrics && emsMetrics.flowChart}
  <section class="flow-graph-container">
    <div class="flow-header">
      <h4>EMS Delivery Flow &amp; Completion Stages</h4>
      <div class="flow-summary">
        <span>Completion: <strong>{percent(emsMetrics.flowChart.completionRate)}</strong></span>
        <span>Weighted Progress: <strong>{percent(emsMetrics.flowChart.weightedProgress)}</strong></span>
        <span>Total Tasks: <strong>{emsMetrics.flowChart.totalTasks}</strong></span>
      </div>
    </div>

    <div class="stages-grid">
      {#each emsMetrics.flowChart.stages as stage}
        <div class="stage-card">
          <div class="stage-top">
            <span class="stage-label">{stage.label}</span>
            <span class="stage-rate">{percent(stage.completionRate)}</span>
          </div>

          <div class="progress-bar-bg">
            <div 
              class="progress-bar-fill"
              style="width: {width(stage.completionRate)}"
            ></div>
          </div>

          <div class="stage-footer">
            <span>Done: {stage.done}</span>
            <span>Queued/Active: {stage.queued + stage.active}</span>
            {#if stage.blocked > 0}
              <span class="blocked-count">Blocked: {stage.blocked}</span>
            {/if}
          </div>
        </div>
      {/each}
    </div>
  </section>
{/if}

<style>
  .flow-graph-container {
    background: #1e293b;
    border: 1px solid #334155;
    border-radius: 12px;
    padding: 1.25rem;
    display: flex;
    flex-direction: column;
    gap: 1rem;
    color: #e2e8f0;
  }

  .flow-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    border-bottom: 1px solid #334155;
    padding-bottom: 0.75rem;
  }

  .flow-header h4 {
    margin: 0;
    font-size: 1.05rem;
    color: #f8fafc;
  }

  .flow-summary {
    display: flex;
    gap: 1.25rem;
    font-size: 0.85rem;
    color: #94a3b8;
  }

  .flow-summary strong {
    color: #38bdf8;
  }

  .stages-grid {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
    gap: 1rem;
  }

  .stage-card {
    background: #0f172a;
    border: 1px solid #334155;
    border-radius: 8px;
    padding: 0.9rem;
    display: flex;
    flex-direction: column;
    gap: 0.5rem;
  }

  .stage-top {
    display: flex;
    justify-content: space-between;
    font-size: 0.85rem;
    font-weight: 600;

  }

  .stage-label { color: #f1f5f9; }
  .stage-rate { color: #34d399; font-weight: 700; }

  .progress-bar-bg {
    width: 100%;
    height: 6px;
    background: #334155;
    border-radius: 3px;
    overflow: hidden;
  }

  .progress-bar-fill {
    height: 100%;
    background: #3b82f6;
    border-radius: 3px;
    transition: width 0.3s ease;
  }

  .stage-footer {
    display: flex;
    justify-content: space-between;
    font-size: 0.75rem;
    color: #94a3b8;
    margin-top: 0.25rem;
  }

  .blocked-count { color: #f87171; font-weight: 700; }
</style>
