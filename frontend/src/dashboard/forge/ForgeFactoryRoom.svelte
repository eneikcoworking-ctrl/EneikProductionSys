<script lang="ts">
  // Factory room (2026-08-10) - factory-wide only, never scoped to one project (no projectId prop by
  // design). Real data: TOC's own factory-wide DBR status/anomalies, Six Sigma layer=factory, and
  // Kaizen entries whose category is about the factory's own process (SYSTEMIC_DEFECT/
  // ROLE_QUALITY_DRIFT), not about any single delivered product.
  import { onMount, onDestroy } from 'svelte';
  import { API_BASE } from '../../lib/api';
  import type { TocDbrStatus, TocAnomalyReport, SixSigmaAuditReport, KaizenProposalDto } from '../../lib/types';
  import GaugeDial from './GaugeDial.svelte';

  let dbrStatus: TocDbrStatus | null = null;
  let anomalies: TocAnomalyReport[] = [];
  let sixSigma: SixSigmaAuditReport | null = null;
  let kaizenEntries: KaizenProposalDto[] = [];
  let loading = true;
  let pollTimer: ReturnType<typeof setInterval> | undefined;

  const FACTORY_CATEGORIES = new Set(['SYSTEMIC_DEFECT', 'ROLE_QUALITY_DRIFT']);

  async function load() {
    try {
      const [statusRes, anomaliesRes, sigmaRes, kaizenRes] = await Promise.all([
        fetch(`${API_BASE}/api/toc/status`),
        fetch(`${API_BASE}/api/toc/anomalies`),
        fetch(`${API_BASE}/api/audit/six-sigma?layer=factory`),
        fetch(`${API_BASE}/api/kaizen/history`),
      ]);
      if (statusRes.ok) dbrStatus = await statusRes.json();
      if (anomaliesRes.ok) anomalies = await anomaliesRes.json();
      if (sigmaRes.ok) sixSigma = await sigmaRes.json();
      if (kaizenRes.ok) {
        const all: KaizenProposalDto[] = await kaizenRes.json();
        kaizenEntries = all.filter((p) => FACTORY_CATEGORIES.has(p.category));
      }
    } finally {
      loading = false;
    }
  }

  onMount(() => {
    load();
    pollTimer = setInterval(load, 15000);
  });
  onDestroy(() => {
    if (pollTimer) clearInterval(pollTimer);
  });

  let sigmaTone: 'healthy' | 'attention' | 'critical';
  $: sigmaTone = !sixSigma ? 'healthy' : sixSigma.sigmaLevel >= 4 ? 'healthy' : sixSigma.sigmaLevel >= 2 ? 'attention' : 'critical';
</script>

{#if loading}
  <div class="forge-loading">Reading the factory's own instruments…</div>
{:else}
  <div class="forge-grid">
    <div class="forge-panel gauge-panel">
      <GaugeDial
        value={sixSigma?.sigmaLevel ?? 0}
        label="Factory quality"
        sublabel="{sixSigma ? sixSigma.sigmaLevel.toFixed(2) : '0.00'}σ across every project"
        tone={sigmaTone}
      />
    </div>

    <div class="forge-panel">
      <span class="forge-eyebrow">Drum · Buffer · Rope</span>
      <h3>{dbrStatus?.primaryConstraintNode && dbrStatus.primaryConstraintNode !== 'NONE' ? dbrStatus.primaryConstraintNode : 'No bottleneck right now'}</h3>
      {#if dbrStatus}
        <div class="mechanism">
          <div class="pipe">
            <span class="station"></span>
            <span class="station station-drum" class:throttled={dbrStatus.ropeThrottlingActive}>drum</span>
            <span class="station"></span>
          </div>
          <div class="mech-readout">
            <span>Utilization: <strong>{(dbrStatus.constraintUtilization * 100).toFixed(0)}%</strong></span>
            <span>Buffer: <strong>{dbrStatus.bufferSize}/{dbrStatus.maxBufferCapacity}</strong></span>
            <span class:rope-active={dbrStatus.ropeThrottlingActive}>
              Rope: <strong>{dbrStatus.ropeThrottlingActive ? 'pacing new work' : 'slack'}</strong>
            </span>
          </div>
        </div>
        {#if dbrStatus.recommendation}
          <p class="dbr-note">{dbrStatus.recommendation}</p>
        {/if}
      {/if}
    </div>

    <div class="forge-panel span-2">
      <span class="forge-eyebrow">Notes about the factory itself</span>
      <h3>Kaizen — process-level findings</h3>
      {#if kaizenEntries.length === 0}
        <p class="forge-empty">Nothing noticed about the factory's own process right now.</p>
      {:else}
        <div class="forge-ledger">
          {#each kaizenEntries as entry (entry.id)}
            <div class="forge-ledger-row">
              <div class="row-title">{entry.title}</div>
              <div class="row-detail">{entry.actionDescription}</div>
            </div>
          {/each}
        </div>
      {/if}
    </div>

    <div class="forge-panel span-2">
      <span class="forge-eyebrow">Real-time interception log</span>
      <h3>Anomalies</h3>
      {#if anomalies.length === 0}
        <p class="forge-empty">Nothing to report - clean run.</p>
      {:else}
        <div class="forge-ledger">
          {#each anomalies.slice(0, 6) as a (a.id)}
            <div class="forge-ledger-row">
              <div class="row-title">{a.nodeName ?? a.type}</div>
              <div class="row-detail">{a.details} — {a.actionTaken}</div>
            </div>
          {/each}
        </div>
      {/if}
    </div>
  </div>
{/if}

<style>
  .forge-grid {
    display: grid;
    gap: var(--space-4);
    grid-template-columns: repeat(2, 1fr);
  }

  .span-2 {
    grid-column: span 2;
  }

  .gauge-panel {
    align-items: center;
    display: flex;
    justify-content: center;
  }

  .mechanism {
    display: flex;
    flex-direction: column;
    gap: 10px;
  }

  .pipe {
    align-items: center;
    background: var(--forge-line-soft);
    border-radius: 999px;
    display: flex;
    height: 10px;
    justify-content: space-between;
    padding: 0 4px;
  }

  .station {
    background: var(--forge-surface);
    border: 2px solid var(--forge-gold);
    border-radius: 50%;
    height: 14px;
    width: 14px;
  }

  .station-drum {
    align-items: center;
    background: var(--forge-gold-soft);
    border-color: var(--forge-attention);
    display: flex;
    font-size: 9px;
    height: 26px;
    justify-content: center;
    text-transform: uppercase;
    width: 26px;
  }

  .station-drum.throttled {
    border-color: var(--forge-attention);
    box-shadow: 0 0 0 3px var(--forge-gold-soft);
  }

  .mech-readout {
    color: var(--forge-ink-muted);
    display: flex;
    font-size: 12.5px;
    gap: 16px;
  }

  .mech-readout strong {
    color: var(--forge-ink);
  }

  .rope-active strong {
    color: var(--forge-attention);
  }

  .dbr-note {
    color: var(--forge-ink-muted);
    font-size: 12.5px;
    margin: 10px 0 0;
  }

  @media (max-width: 720px) {
    .forge-grid { grid-template-columns: 1fr; }
    .span-2 { grid-column: span 1; }
  }
</style>
