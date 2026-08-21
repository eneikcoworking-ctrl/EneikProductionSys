<script lang="ts">
  // Product room (2026-08-10) - only the finished, currently-running product. Never work still in
  // progress, never factory-wide data.
  import { onMount, onDestroy } from 'svelte';
  import { API_BASE } from '../../lib/api';
  import type { SixSigmaAuditReport, KaizenProposalDto, RuntimeHealthSummary } from '../../lib/types';
  import GaugeDial from './GaugeDial.svelte';

  export let projectId: string;

  let sixSigma: SixSigmaAuditReport | null = null;
  let kaizenEntries: KaizenProposalDto[] = [];
  let runtimeHealth: RuntimeHealthSummary | null = null;
  let loading = true;
  let pollTimer: ReturnType<typeof setInterval> | undefined;

  async function load() {
    try {
      const [sigmaRes, kaizenRes, healthRes] = await Promise.all([
        fetch(`${API_BASE}/api/audit/six-sigma?projectId=${projectId}&layer=product`),
        fetch(`${API_BASE}/api/kaizen/history?projectId=${projectId}`),
        fetch(`${API_BASE}/api/projects/${projectId}/runtime-health`),
      ]);
      if (sigmaRes.ok) sixSigma = await sigmaRes.json();
      if (kaizenRes.ok) {
        const all: KaizenProposalDto[] = await kaizenRes.json();
        kaizenEntries = all.filter((p) => p.category === 'PRODUCT_RUNTIME_DEFECT');
      }
      if (healthRes.ok) runtimeHealth = await healthRes.json();
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

  // Chronological (oldest-first) so the pulse line reads left-to-right like a real strip chart.
  // Only rows that actually observed the product: a call that never reached the launcher says nothing
  // about the product, and plotting it as a dip drew this factory's own sidecar outage as the client's
  // product going down - 46 consecutive such rows on 2026-08-20 read as 46 product failures here.
  $: chronological = runtimeHealth
    ? [...runtimeHealth.recentAttempts].filter((o) => !o.instrumentFailure).reverse()
    : [];
  $: pulsePoints = chronological
    .map((o, i) => {
      const x = chronological.length > 1 ? (i / (chronological.length - 1)) * 260 + 10 : 140;
      const healthy = o.launchSuccess && o.healthStatusCode != null && o.healthStatusCode >= 200 && o.healthStatusCode < 300;
      const y = healthy ? 20 : 60;
      return `${x},${y}`;
    })
    .join(' ');
</script>

{#if loading}
  <div class="forge-loading">Reading the live product's own vitals…</div>
{:else}
  <div class="forge-grid">
    <div class="forge-panel gauge-panel">
      <GaugeDial
        value={sixSigma?.sigmaLevel ?? 0}
        label="Live product quality"
        sublabel="{sixSigma ? sixSigma.sigmaLevel.toFixed(2) : '0.00'}σ, shipped work only"
        tone={sigmaTone}
      />
    </div>

    <div class="forge-panel">
      <span class="forge-eyebrow">Beta-posterior · ClientRuntimeObservability</span>
      <h3>Is it actually alive?</h3>
      {#if runtimeHealth && runtimeHealth.observationCount > 0}
        <svg viewBox="0 0 280 80" class="pulse-chart" role="img" aria-label="Recent product health checks">
          <line x1="10" y1="20" x2="270" y2="20" class="pulse-gridline" />
          <line x1="10" y1="60" x2="270" y2="60" class="pulse-gridline" />
          <polyline points={pulsePoints} class="pulse-line" />
          {#each chronological as o, i}
            {@const x = chronological.length > 1 ? (i / (chronological.length - 1)) * 260 + 10 : 140}
            {@const healthy = o.launchSuccess && o.healthStatusCode != null && o.healthStatusCode >= 200 && o.healthStatusCode < 300}
            <circle cx={x} cy={healthy ? 20 : 60} r="3" class="pulse-dot" class:down={!healthy}>
              <title>{new Date(o.observedAt).toLocaleString('en-US')} — {healthy ? 'alive' : (o.errorText ?? 'not responding')}</title>
            </circle>
          {/each}
        </svg>
        <p class="posterior-note">
          Confidence it's alive: <strong>{(runtimeHealth.posteriorMean * 100).toFixed(0)}%</strong>
          · {runtimeHealth.observationCount} check{runtimeHealth.observationCount === 1 ? '' : 's'} so far
        </p>
      {:else}
        <p class="forge-empty">No real launch check has run yet for this product.</p>
      {/if}
    </div>

    <div class="forge-panel span-2">
      <span class="forge-eyebrow">Notes about the live product</span>
      <h3>Kaizen — runtime findings</h3>
      {#if kaizenEntries.length === 0}
        <p class="forge-empty">Nothing noticed about the live running product right now.</p>
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

  .pulse-chart {
    background: var(--forge-bg);
    border: 1px solid var(--forge-line-soft);
    border-radius: 8px;
    width: 100%;
  }

  .pulse-gridline {
    stroke: var(--forge-line-soft);
    stroke-dasharray: 3 3;
    stroke-width: 1;
  }

  .pulse-line {
    fill: none;
    stroke: var(--forge-gold);
    stroke-linejoin: round;
    stroke-width: 2;
  }

  .pulse-dot {
    fill: var(--forge-healthy);
  }

  .pulse-dot.down {
    fill: var(--forge-critical);
  }

  .posterior-note {
    color: var(--forge-ink-muted);
    font-size: 12.5px;
    margin: 10px 0 0;
  }

  .posterior-note strong {
    color: var(--forge-ink);
  }

  @media (max-width: 720px) {
    .forge-grid { grid-template-columns: 1fr; }
    .span-2 { grid-column: span 1; }
  }
</style>
