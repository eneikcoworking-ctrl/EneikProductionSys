<script lang="ts">
  // Per-branch detail drawer, opened by clicking a branch node in ProductTree. Slides in from the
  // right rather than a centered modal - keeps the tree itself visible and anchors the detail to the
  // branch that was clicked. Shows only real, already-happened events (FeatureThreadEntity-sourced
  // annotations) - never a "needs your decision" affordance, matching the whole system's
  // autonomous-by-design principle.
  import { fly, fade } from 'svelte/transition';

  interface HealthDto {
    defects: number;
    opportunities: number;
    prConflictDefects: number;
    prConflictOpportunities: number;
    dpmo: number;
    sigmaLevel: number;
  }
  interface AnnotationDto {
    type: string;
    text: string;
    occurredAt: string;
    prUrl: string | null;
  }
  interface FeatureBranchDto {
    featureId: string;
    title: string | null;
    createdAt: string;
    kanoClass: string | null;
    cynefinDomain: string | null;
    tocConstraintRef: string | null;
    complete: boolean;
    codeProducingItemCount: number;
    mergedItemCount: number;
    livePulse: boolean;
    health: HealthDto;
    annotations: AnnotationDto[];
  }

  export let branch: FeatureBranchDto;
  export let onClose: () => void;

  $: realAnnotations = branch.annotations.filter(a => a.type !== 'active');
  $: vitality = Math.round(Math.min(1, branch.mergedItemCount / Math.max(1, branch.codeProducingItemCount)) * 100);
</script>

<svelte:window onkeydown={(e) => e.key === 'Escape' && onClose()} />

<div class="drawer-backdrop" onclick={onClose} role="presentation" transition:fade={{ duration: 200 }}></div>
<div
  class="drawer"
  role="dialog"
  aria-label={branch.title ?? 'Branch'}
  transition:fly={{ x: 40, duration: 260, opacity: 1 }}
>
  <div class="drawer-inner">
    <div class="card-head">
      <div>
        <span class="eyebrow-tree">{branch.complete ? 'Grown' : 'Growing'}</span>
        <h3>{branch.title ?? '(untitled)'}</h3>
      </div>
      <button class="close-btn" onclick={onClose} aria-label="Close">✕</button>
    </div>

    <div class="card-meta">
      {#if branch.livePulse}<span class="meta-pill live">active now</span>{/if}
      {#if branch.cynefinDomain}<span class="meta-pill">{branch.cynefinDomain}</span>{/if}
      {#if branch.kanoClass}<span class="meta-pill">{branch.kanoClass}</span>{/if}
    </div>

    <section class="vitality-section">
      <div class="vitality-head">
        <span class="eyebrow-tree">Merged into main</span>
        <span class="vitality-value">{vitality}%</span>
      </div>
      <div class="vitality-track">
        <div class="vitality-fill" style="width: {vitality}%"></div>
      </div>
      <p class="vitality-sub">{branch.mergedItemCount} of {branch.codeProducingItemCount} tasks merged</p>
    </section>

    <section class="card-health">
      <span class="eyebrow-tree">Quality</span>
      <span class="health-value">σ {branch.health.sigmaLevel.toFixed(2)}</span>
      <span class="health-sub">
        {branch.health.defects}/{branch.health.opportunities} checks with an issue ·
        {branch.health.prConflictDefects}/{branch.health.prConflictOpportunities} merge conflicts
      </span>
    </section>

    <section class="card-annotations">
      <span class="eyebrow-tree">What happened on its own</span>
      {#if realAnnotations.length === 0}
        <p class="empty-note">Quiet — nothing happened on its own.</p>
      {:else}
        {#each realAnnotations as note}
          <div class="annotation-row {note.type}">
            <span class="annotation-text">{note.text}</span>
            <span class="annotation-time">{new Date(note.occurredAt).toLocaleString('en-US')}</span>
            {#if note.prUrl}
              <a href={note.prUrl} target="_blank" rel="noopener noreferrer" class="annotation-link">View PR</a>
            {/if}
          </div>
        {/each}
      {/if}
    </section>
  </div>
</div>

<style>
  .drawer-backdrop {
    background: rgba(11, 28, 48, 0.28);
    inset: 0;
    position: fixed;
    z-index: 40;
  }

  .drawer {
    background: var(--surface);
    border-left: 1px solid var(--neutral-200);
    bottom: 0;
    box-shadow: -8px 0 24px rgba(11, 28, 48, 0.1);
    max-width: 90vw;
    overflow-y: auto;
    position: fixed;
    right: 0;
    top: 0;
    width: 380px;
    z-index: 41;
  }

  .drawer-inner {
    display: grid;
    gap: 20px;
    padding: 28px 24px;
  }

  .eyebrow-tree {
    color: var(--tree-healthy, var(--secondary));
    display: block;
    font-family: var(--font-body);
    font-size: 11px;
    font-weight: 700;
    letter-spacing: 0.1em;
    margin-bottom: 4px;
    text-transform: uppercase;
  }

  .card-head {
    align-items: flex-start;
    display: flex;
    justify-content: space-between;
    gap: 10px;
  }

  .card-head h3 {
    font-family: var(--font-display);
    font-size: 20px;
  }

  .close-btn {
    background: transparent;
    border: none;
    color: var(--neutral-500);
    min-height: 28px;
    padding: 0 6px;
  }

  .card-meta {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
  }

  .meta-pill {
    background: var(--neutral-100);
    border-radius: var(--radius-pill);
    color: var(--neutral-700);
    font-size: 11px;
    font-weight: 700;
    padding: 3px 10px;
    text-transform: uppercase;
  }

  .meta-pill.live {
    background: var(--success-bg);
    color: var(--success);
  }

  .vitality-section,
  .card-health,
  .card-annotations {
    border-top: 1px solid var(--neutral-200);
    display: grid;
    gap: 6px;
    padding-top: 16px;
  }

  .vitality-head {
    align-items: baseline;
    display: flex;
    justify-content: space-between;
  }

  .vitality-value {
    color: var(--tree-healthy, var(--primary));
    font-family: var(--font-display);
    font-size: 22px;
  }

  .vitality-track {
    background: var(--neutral-100);
    border-radius: var(--radius-pill);
    height: 6px;
    overflow: hidden;
  }

  .vitality-fill {
    background: var(--tree-healthy, var(--primary));
    height: 100%;
    transition: width 0.6s ease;
  }

  .vitality-sub,
  .health-sub {
    color: var(--neutral-500);
    font-size: 12px;
  }

  .health-value {
    font-size: 15px;
  }

  .empty-note {
    color: var(--neutral-500);
    font-size: 13px;
    margin: 0;
  }

  .annotation-row {
    display: grid;
    font-size: 13px;
    gap: 2px;
    padding: 6px 0;
  }

  .annotation-row.abandoned .annotation-text {
    color: var(--error);
  }

  .annotation-time {
    color: var(--neutral-500);
    font-size: 11px;
  }

  .annotation-link {
    color: var(--primary);
    font-size: 12px;
    justify-self: start;
  }
</style>
