<script lang="ts">
  // Delivery room (2026-08-10) - this ONE project's own construction journey only. Never factory-wide
  // data, never a finished-product-only view (that's the other two rooms).
  import { onMount, onDestroy } from 'svelte';
  import { API_BASE } from '../../lib/api';
  import type { SixSigmaAuditReport, KaizenProposalDto, CoherenceGraphSnapshot, GeminiObserverJournalEntry } from '../../lib/types';
  import GaugeDial from './GaugeDial.svelte';

  export let projectId: string;

  let sixSigma: SixSigmaAuditReport | null = null;
  let kaizenEntries: KaizenProposalDto[] = [];
  let coherence: CoherenceGraphSnapshot | null = null;
  let journal: GeminiObserverJournalEntry[] = [];
  let loading = true;
  let pollTimer: ReturnType<typeof setInterval> | undefined;

  const DELIVERY_CATEGORIES = new Set(['WASTE_REDUCTION', 'DEFECT_ELIMINATION', 'BUFFER_TUNING']);

  async function load() {
    try {
      const [sigmaRes, kaizenRes, coherenceRes, journalRes] = await Promise.all([
        fetch(`${API_BASE}/api/audit/six-sigma?projectId=${projectId}&layer=delivery`),
        fetch(`${API_BASE}/api/kaizen/history?projectId=${projectId}`),
        fetch(`${API_BASE}/api/projects/${projectId}/coherence-graph`),
        fetch(`${API_BASE}/api/projects/${projectId}/observer-journal`),
      ]);
      if (sigmaRes.ok) sixSigma = await sigmaRes.json();
      if (kaizenRes.ok) {
        const all: KaizenProposalDto[] = await kaizenRes.json();
        kaizenEntries = all.filter((p) => DELIVERY_CATEGORIES.has(p.category));
      }
      if (coherenceRes.ok) coherence = await coherenceRes.json();
      if (journalRes.ok) journal = await journalRes.json();
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

  // Deterministic scatter position per node - stable across polls, driven only by the node's own id.
  // 2026-08-10: the previous "fix" (prefixing 'x'/'y' onto the same rolling hash) was still broken -
  // real node ids are UUIDs, which are ALL the same length (36 chars), so the 'x' vs 'y' seed
  // (charCodes 120 vs 121, differing by exactly 1) produces the SAME constant multiplicative offset
  // (31^36 mod 2^32) for every single node - x and y stayed an affine function of each other for any
  // fixed-length id, just a differently-shaped correlation than the original suffix bug. Confirmed
  // live: real coherence-graph screenshot still showed a near-perfect diagonal. The actual fix is two
  // STRUCTURALLY different hash recurrences (different multiplier/mixing, not just a different seed
  // string), so no constant id-length-dependent relationship can exist between the two axes.
  function hashSeedX(id: string): number {
    let h = 0;
    for (let i = 0; i < id.length; i++) h = (h * 31 + id.charCodeAt(i)) | 0;
    return (((h % 1000) + 1000) % 1000) / 1000;
  }
  function hashSeedY(id: string): number {
    let h = 0;
    for (let i = 0; i < id.length; i++) h = (h * 131 + id.charCodeAt(i) * 7 + 13) | 0;
    return (((h % 1000) + 1000) % 1000) / 1000;
  }
  function nodePos(id: string) {
    return { x: 20 + hashSeedX(id) * 260, y: 20 + hashSeedY(id) * 160 };
  }
  // Capped to the most recent 80 for legibility/performance - a real backlog (355 nodes seen live)
  // should read as a tidy recent constellation, not a solid smear; the panel's own coherenceScore/
  // acceptedNodes/totalNodes line still reports the true, uncapped counts.
  const MAX_GRAPH_NODES = 80;
  $: visibleNodes = coherence
    ? [...coherence.nodes].sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()).slice(0, MAX_GRAPH_NODES)
    : [];

  // Cluster edges: only between nodes that genuinely share a real featureId or prNumber - never a
  // guessed connection, mirrors EvidenceCoherenceService's own STRONG-cluster definition.
  $: clusterEdges = (() => {
    if (!coherence) return [];
    const edges: [string, string][] = [];
    const nodes = visibleNodes;
    for (let i = 0; i < nodes.length; i++) {
      for (let j = i + 1; j < nodes.length; j++) {
        const a = nodes[i], b = nodes[j];
        const sharesFeature = a.featureId && a.featureId === b.featureId;
        const sharesPr = a.prNumber != null && a.prNumber === b.prNumber;
        if (sharesFeature || sharesPr) edges.push([a.id, b.id]);
      }
    }
    return edges;
  })();
</script>

{#if loading}
  <div class="forge-loading">Reading this project's own record…</div>
{:else}
  <div class="forge-grid">
    <div class="forge-panel gauge-panel">
      <GaugeDial
        value={sixSigma?.sigmaLevel ?? 0}
        label="This project's quality"
        sublabel="{sixSigma ? sixSigma.sigmaLevel.toFixed(2) : '0.00'}σ, full history"
        tone={sigmaTone}
      />
    </div>

    <div class="forge-panel">
      <span class="forge-eyebrow">Thagard ECHO · Gärdenfors AGM</span>
      <h3>Evidence coherence</h3>
      {#if coherence && coherence.hasCoherenceRun}
        <svg viewBox="0 0 300 200" class="coherence-graph" role="img" aria-label="Evidence coherence graph">
          {#each clusterEdges as [a, b]}
            {@const na = visibleNodes.find((n) => n.id === a)}
            {@const nb = visibleNodes.find((n) => n.id === b)}
            {#if na && nb}
              {@const pa = nodePos(na.id)}
              {@const pb = nodePos(nb.id)}
              <line x1={pa.x} y1={pa.y} x2={pb.x} y2={pb.y} class="edge" />
            {/if}
          {/each}
          {#each visibleNodes as node (node.id)}
            {@const p = nodePos(node.id)}
            <circle cx={p.x} cy={p.y} r={node.accepted ? 5 : 3} class="node" class:accepted={node.accepted}>
              <title>{node.summaryText}</title>
            </circle>
          {/each}
        </svg>
        <p class="coherence-score">Coherence score: <strong>{coherence.coherenceScore.toFixed(3)}</strong> · {coherence.acceptedNodes}/{coherence.totalNodes} beliefs currently held</p>
      {:else}
        <p class="forge-empty">No coherence run yet for this project.</p>
      {/if}
    </div>

    <div class="forge-panel">
      <span class="forge-eyebrow">Gemini's own record</span>
      <h3>Observer's log</h3>
      {#if journal.length === 0}
        <p class="forge-empty">No real observation cycle yet.</p>
      {:else}
        <div class="forge-ledger">
          {#each journal as entry (entry.id)}
            <div class="forge-ledger-row">
              <div class="row-title">{new Date(entry.createdAt).toLocaleString('en-US')}</div>
              <div class="row-detail">{entry.entry}</div>
            </div>
          {/each}
        </div>
      {/if}
    </div>

    <div class="forge-panel span-2">
      <span class="forge-eyebrow">Notes about how this project was built</span>
      <h3>Kaizen — this project's own findings</h3>
      {#if kaizenEntries.length === 0}
        <p class="forge-empty">Nothing noticed about this project's own build process right now.</p>
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

  .coherence-graph {
    background: var(--forge-bg);
    border: 1px solid var(--forge-line-soft);
    border-radius: 8px;
    width: 100%;
  }

  .edge {
    stroke: var(--forge-gold);
    stroke-opacity: 0.4;
    stroke-width: 1;
  }

  .node {
    fill: var(--forge-line-soft);
    stroke: var(--forge-line);
    stroke-width: 0.5;
  }

  .node.accepted {
    fill: var(--forge-gold);
    stroke: var(--forge-attention);
  }

  .coherence-score {
    color: var(--forge-ink-muted);
    font-size: 12px;
    margin: 8px 0 0;
  }

  .coherence-score strong {
    color: var(--forge-ink);
  }

  @media (max-width: 720px) {
    .forge-grid { grid-template-columns: 1fr; }
    .span-2 { grid-column: span 1; }
  }
</style>
