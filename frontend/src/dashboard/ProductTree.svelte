<script lang="ts">
  // The primary view (2026-08-02 redesign): one living tree instead of the old fragmented
  // tab-per-subsystem dashboard. Trunk = project start. Branches = real features (the same
  // PRODUCT_ITERATION_SOURCES-filtered set already trusted at GET /{projectId}/epics - see
  // ProjectTreeService's own doc comment). Position along the trunk is chronological
  // (FeatureEntity.createdAt) - there is no real feature-to-feature dependency data anywhere in
  // this system, so this deliberately does NOT draw fake dependency edges (confirmed with the
  // operator). A branch pulses while a real Jules session is active on it; its color reflects real
  // Six Sigma health computed server-side, never a client-simulated state.
  //
  // Visual language (2026-08-02, second pass): adapted from the operator's own Stitch reference
  // ("Verdant Flow" project) - thin silver branch lines with the health state carried entirely by
  // the glowing node dots at each tip, on a warm cream canvas, cream/sage/gold/burnt-orange palette
  // straight from that project's own design tokens. No persistent on-canvas labels (they collided
  // once there were a dozen-plus real branches); a title only appears in a hover card, real data
  // rendered live, not a static illustration.
  import { onMount, onDestroy } from 'svelte';
  import { API_BASE } from '../lib/api';
  import SeedPlanter from './tree/SeedPlanter.svelte';
  import BranchAnnotation from './tree/BranchAnnotation.svelte';
  // 2026-08-10 (Роща): two additive ambient bands around the same trunk/branches - roots reflect the
  // factory-wide TOC constraint (shared infrastructure, never project-specific), the canopy glow
  // reflects whether the DELIVERED product is actually alive right now (ClientRuntimeObservability's
  // Beta-posterior), never build/merge status. Neither band touches branch/leaf/pulse logic above.
  import type { TocDbrStatus, RuntimeHealthSummary } from '../lib/types';
  // Purely decorative backdrop (2026-08-10: Art Nouveau tree, generated via the project's own Stitch
  // design system - Libre Caslon Text/IBM Plex Sans/#7d8570/#c99a2e, the same tokens as this file's own
  // CSS below) - sits low-opacity behind the real, live, data-driven branches below. Never the source
  // of any real information itself - just atmosphere, the same way the ambient glow gradient is.
  // Clean isolated illustration (no baked-in chrome/nav to crop out, unlike the earlier reference).
  import treeBackdrop from '../assets/grove-tree-artnouveau.jpg';

  export let projectId: string;

  // Real branch-growth animation: draws the line from trunk to tip once, the first time a given
  // branch's path element mounts. Svelte's keyed {#each} (branch.featureId) only creates this node
  // once per real branch, so this fires exactly when a genuinely new branch first appears - not a
  // decorative loop replayed on every 10s poll, an animation tied to a real event (a feature sprouted).
  function drawIn(node: SVGPathElement) {
    const length = node.getTotalLength();
    node.style.strokeDasharray = `${length}`;
    node.style.strokeDashoffset = `${length}`;
    requestAnimationFrame(() => {
      node.style.transition = 'stroke-dashoffset 1.1s cubic-bezier(0.22, 0.61, 0.36, 1)';
      node.style.strokeDashoffset = '0';
    });
    return {
      destroy() {
        node.style.transition = '';
      }
    };
  }

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
    rootWishlistId: string;
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
  interface SeedDto {
    wishlistId: string;
    content: string;
    status: string;
    createdAt: string;
    featureId: string | null;
  }
  interface ProjectTreeDto {
    projectId: string;
    branches: FeatureBranchDto[];
    seeds: SeedDto[];
    trunkAnnotations: AnnotationDto[];
  }

  let tree: ProjectTreeDto | null = null;
  let loading = true;
  let error: string | null = null;
  let pollTimer: ReturnType<typeof setInterval> | undefined;
  let ambientPollTimer: ReturnType<typeof setInterval> | undefined;
  // Роща ambient bands - fetched alongside the tree, on the same poll cycle, never blocking it:
  // both are best-effort (a factory TOC hiccup or a not-yet-observed product must never break the
  // tree itself, which is why fetchAmbient swallows its own errors below).
  let tocStatus: TocDbrStatus | null = null;
  let runtimeHealth: RuntimeHealthSummary | null = null;
  let openBranchId: string | null = null;
  let showTrunkLog = false;
  let hoveredBranch: FeatureBranchDto | null = null;
  let hoverPos = { x: 0, y: 0 };
  let canvasEl: HTMLDivElement;

  // Fixed canvas: the tree always fits one frame regardless of branch count, per the operator's
  // explicit direction ("должно полностью в экран влазить") - a real canopy fans out and gets denser
  // as more real features exist, it never grows the page and forces scrolling.
  const VIEW_W = 960;
  const VIEW_H = 620;
  const TRUNK_BASE_X = VIEW_W / 2;
  const TRUNK_BASE_Y = VIEW_H - 46;
  const TRUNK_TOP_X = VIEW_W / 2;
  const TRUNK_TOP_Y = VIEW_H * 0.42;
  const MIN_BRANCH_LEN = 90;
  const MAX_BRANCH_LEN = 300;
  const CANOPY_SPREAD_DEG = 168;
  const MAX_VISIBLE_SEEDS = 18;

  // Decorative backdrop placement: a clean, isolated 512x512 illustration (no chrome to crop out),
  // centered so its own trunk/root falls roughly where the live SVG trunk (TRUNK_TOP_Y..TRUNK_BASE_Y)
  // sits, so the atmosphere and the real data-driven branches read as one tree, not two layers.
  const BACKDROP_TARGET_W = 460;
  const BACKDROP_TARGET_H = 460;
  const BACKDROP_IMG_X = VIEW_W / 2 - BACKDROP_TARGET_W / 2;
  const BACKDROP_IMG_Y = TRUNK_BASE_Y - BACKDROP_TARGET_H + 40;
  const BACKDROP_TARGET_X = BACKDROP_IMG_X;
  const BACKDROP_TARGET_Y = BACKDROP_IMG_Y;
  const BACKDROP_IMG_W = BACKDROP_TARGET_W;
  const BACKDROP_IMG_H = BACKDROP_TARGET_H;

  async function fetchTree() {
    try {
      const res = await fetch(`${API_BASE}/api/projects/${projectId}/tree`);
      if (!res.ok) throw new Error('Failed to load project tree');
      tree = await res.json();
      error = null;
    } catch (e: any) {
      error = e.message ?? 'Failed to load project tree';
    } finally {
      loading = false;
    }
  }

  // Best-effort, silent on failure - these two ambient signals decorate the tree, they never gate it.
  async function fetchAmbient() {
    try {
      const res = await fetch(`${API_BASE}/api/toc/status`);
      if (res.ok) tocStatus = await res.json();
    } catch { /* roots stay calm/undecorated - not a tree-view failure */ }
    try {
      const res = await fetch(`${API_BASE}/api/projects/${projectId}/runtime-health`);
      if (res.ok) runtimeHealth = await res.json();
    } catch { /* canopy stays undecorated - not a tree-view failure */ }
  }

  onMount(() => {
    fetchTree();
    fetchAmbient();
    pollTimer = setInterval(fetchTree, 10000);
    ambientPollTimer = setInterval(fetchAmbient, 30000);
  });
  onDestroy(() => {
    if (pollTimer) clearInterval(pollTimer);
    if (ambientPollTimer) clearInterval(ambientPollTimer);
  });

  // Roots tint: calm sage by default. Amber when the factory's own rope is actively throttling
  // admissions (a real, deliberate protective state, not a fault) - never invents urgency from
  // absence of data (tocStatus null -> stays calm).
  $: rootsStrained = tocStatus?.ropeThrottlingActive === true;

  // Canopy pulse: only rendered once there is at least one real observation (Phase 0/1 must have
  // actually run) - absence of data means no glow at all, never a fabricated "everything's fine".
  $: hasProductSignal = (runtimeHealth?.observationCount ?? 0) > 0;
  $: productHealthy = runtimeHealth?.lastObservationHealthy === true;
  $: productGlowOpacity = hasProductSignal ? 0.28 + (runtimeHealth?.posteriorMean ?? 0) * 0.32 : 0;

  // Chronological order is the layout's only real signal - no fabricated dependency edges.
  $: sortedBranches = (tree?.branches ?? [])
    .slice()
    .sort((a, b) => new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime());

  $: seedCount = tree?.seeds?.length ?? 0;
  $: seedRows = Math.ceil(Math.min(seedCount, MAX_VISIBLE_SEEDS) / 6);

  // Real data drives branch length: more merged deliverables, further it reaches - not decoration.
  function branchLength(branch: FeatureBranchDto): number {
    const grown = Math.min(1, branch.mergedItemCount / 8);
    return MIN_BRANCH_LEN + grown * (MAX_BRANCH_LEN - MIN_BRANCH_LEN);
  }

  // Deterministic per-feature variation (not random per render) so the organic curve stays stable
  // across polls - only real data (position, length, color) changes; the wobble is the branch's own
  // fixed "handwriting", derived from its id.
  function hashSeed(id: string): number {
    let h = 0;
    for (let i = 0; i < id.length; i++) {
      h = (h * 31 + id.charCodeAt(i)) | 0;
    }
    return (((h % 1000) + 1000) % 1000) / 1000;
  }

  interface BranchGeometry {
    path: string;
    endX: number;
    endY: number;
  }

  // A thin whiplash curve - deterministic organic wobble, rendered as a single stroked line (no
  // taper, no fill) per the reference: the branch structure stays a delicate neutral line, all the
  // meaning lives in the node dot at its tip.
  function branchGeometry(branch: FeatureBranchDto, angleDeg: number, len: number): BranchGeometry {
    const angle = (angleDeg * Math.PI) / 180;
    const dirX = Math.cos(angle);
    const dirY = Math.sin(angle);
    const endX = TRUNK_TOP_X + dirX * len;
    const endY = TRUNK_TOP_Y + dirY * len;

    const wobble = (hashSeed(branch.featureId) - 0.5) * 50;
    const normalX = -dirY;
    const normalY = dirX;
    const c1x = TRUNK_TOP_X + dirX * len * 0.32 + normalX * wobble;
    const c1y = TRUNK_TOP_Y + dirY * len * 0.32 + normalY * wobble;
    const c2x = TRUNK_TOP_X + dirX * len * 0.68 - normalX * wobble * 0.6;
    const c2y = TRUNK_TOP_Y + dirY * len * 0.68 - normalY * wobble * 0.6;

    const path = `M ${TRUNK_TOP_X} ${TRUNK_TOP_Y} C ${c1x} ${c1y}, ${c2x} ${c2y}, ${endX} ${endY}`;
    return { path, endX, endY };
  }

  function healthColor(health: HealthDto): string {
    if (health.opportunities === 0 && health.prConflictOpportunities === 0) return 'var(--neutral-400)';
    if (health.sigmaLevel >= 4) return 'var(--tree-healthy)';
    if (health.sigmaLevel >= 2) return 'var(--tree-attention)';
    return 'var(--tree-critical)';
  }

  function healthClass(health: HealthDto): string {
    if (health.opportunities === 0 && health.prConflictOpportunities === 0) return 'neutral';
    if (health.sigmaLevel >= 4) return 'healthy';
    if (health.sigmaLevel >= 2) return 'warning';
    return 'unhealthy';
  }

  function toggleBranch(featureId: string) {
    openBranchId = openBranchId === featureId ? null : featureId;
  }

  // Seeds settle as a loose scatter of small leaves at the roots, not a mechanical grid of dots -
  // position/rotation are still deterministic per wishlistId (stable across polls), just organic
  // instead of aligned to a rigid lattice.
  interface SeedLeaf {
    x: number;
    y: number;
    rotation: number;
    scale: number;
  }
  function seedLeafPlacement(seed: SeedDto, index: number): SeedLeaf {
    const row = Math.floor(index / 6);
    const col = index % 6;
    const jitterX = (hashSeed(seed.wishlistId + 'x') - 0.5) * 10;
    const jitterY = (hashSeed(seed.wishlistId + 'y') - 0.5) * 6;
    const x = TRUNK_BASE_X - 84 + col * 17 + jitterX;
    const y = TRUNK_BASE_Y + 18 - row * 13 + jitterY;
    const rotation = (hashSeed(seed.wishlistId + 'r') - 0.5) * 70;
    const scale = 0.8 + hashSeed(seed.wishlistId + 's') * 0.5;
    return { x, y, rotation, scale };
  }

  // A cluster of small leaves fanned around each branch tip - a fan of bare colored dots read as a
  // diagnostic scatter-plot, not a tree; real foliage silhouette is what makes the canopy actually
  // look like a tree. Deterministic per branch (hashSeed), health color still carries the only real
  // signal - this is shape, not new information.
  const LEAVES_PER_TIP = 6;
  interface TipLeaf {
    dx: number;
    dy: number;
    rot: number;
    scale: number;
  }
  function leafCluster(branch: FeatureBranchDto, angleDeg: number): TipLeaf[] {
    const leaves: TipLeaf[] = [];
    for (let i = 0; i < LEAVES_PER_TIP; i++) {
      const spread = (hashSeed(branch.featureId + 'sp' + i) - 0.5) * 150;
      const leafAngle = angleDeg + spread;
      const rad = (leafAngle * Math.PI) / 180;
      const dist = 5 + hashSeed(branch.featureId + 'ds' + i) * 10;
      leaves.push({
        dx: Math.cos(rad) * dist,
        dy: Math.sin(rad) * dist,
        rot: leafAngle + 90,
        scale: 0.85 + hashSeed(branch.featureId + 'sc' + i) * 0.55
      });
    }
    return leaves;
  }

  function onBranchEnter(branch: FeatureBranchDto, e: MouseEvent) {
    hoveredBranch = branch;
    updateHoverPos(e);
  }
  function updateHoverPos(e: MouseEvent) {
    if (!canvasEl) return;
    const rect = canvasEl.getBoundingClientRect();
    hoverPos = { x: e.clientX - rect.left, y: e.clientY - rect.top };
  }
  function onBranchLeave() {
    hoveredBranch = null;
  }
</script>

<div class="product-tree">
  <div class="tree-header">
    <div>
      <span class="eyebrow-tree">Where wishes take root</span>
      <h2>The Tree</h2>
      <p class="subtitle">
        {sortedBranches.length} branches · {tree?.seeds?.length ?? 0} seeded
      </p>
    </div>
    <button class="trunk-log-toggle" onclick={() => (showTrunkLog = !showTrunkLog)}>
      {showTrunkLog ? 'Hide log' : 'Trunk log'}
    </button>
  </div>

  {#if loading && !tree}
    <div class="loading-state">Loading the tree…</div>
  {:else if error && !tree}
    <div class="error-state">⚠️ {error}</div>
  {:else}
    {#if showTrunkLog}
      <div class="trunk-log">
        {#if !tree?.trunkAnnotations?.length}
          <p class="empty-note">Nothing happened on its own — quiet and stable.</p>
        {:else}
          {#each tree.trunkAnnotations as note}
            <div class="trunk-note">
              <span class="note-time">{new Date(note.occurredAt).toLocaleString('en-US')}</span>
              <span class="note-text">{note.text}</span>
            </div>
          {/each}
        {/if}
      </div>
    {/if}

    <div class="tree-canvas" bind:this={canvasEl}>
      <svg viewBox="0 0 {VIEW_W} {VIEW_H}" class="tree-svg" role="img" aria-label="Project feature tree" preserveAspectRatio="xMidYMid meet">
        <defs>
          <filter id="pulse-glow" x="-80%" y="-80%" width="260%" height="260%">
            <feGaussianBlur stdDeviation="4" result="blur" />
            <feComposite in="SourceGraphic" in2="blur" operator="over" />
          </filter>
          <filter id="node-glow" x="-160%" y="-160%" width="420%" height="420%">
            <feGaussianBlur stdDeviation="4.5" result="blur" />
            <feComposite in="SourceGraphic" in2="blur" operator="over" />
          </filter>
          <radialGradient id="canopy-glow" cx="50%" cy="38%" r="55%">
            <stop offset="0%" stop-color="var(--tree-healthy)" stop-opacity="0.14" />
            <stop offset="100%" stop-color="var(--tree-healthy)" stop-opacity="0" />
          </radialGradient>
          <!-- Product-alive glow (2026-08-10) - a second, independent canopy layer: whether the
               DELIVERED product is actually running right now (ClientRuntimeObservability), never
               build/merge status which canopy-glow above already reflects via branch health. -->
          <radialGradient id="product-pulse-glow" cx="50%" cy="18%" r="30%">
            <stop offset="0%" stop-color={productHealthy ? 'var(--tree-gold)' : 'var(--tree-attention)'} stop-opacity="0.55" />
            <stop offset="100%" stop-color={productHealthy ? 'var(--tree-gold)' : 'var(--tree-attention)'} stop-opacity="0" />
          </radialGradient>
          <!-- Soft radial mask instead of a hard rectangle - the crop from the original mockup
               screenshot fades out at its own edges rather than reading as a pasted photo frame,
               which also further suppresses the leftover header/legend text right at those edges. -->
          <radialGradient id="backdrop-fade" cx="50%" cy="46%" r="58%">
            <stop offset="0%" stop-color="#fff" stop-opacity="1" />
            <stop offset="72%" stop-color="#fff" stop-opacity="0.9" />
            <stop offset="100%" stop-color="#fff" stop-opacity="0" />
          </radialGradient>
          <mask id="backdrop-mask">
            <rect x={BACKDROP_TARGET_X} y={BACKDROP_TARGET_Y} width={BACKDROP_TARGET_W} height={BACKDROP_TARGET_H} fill="url(#backdrop-fade)" />
          </mask>
        </defs>

        <!-- The Stitch illustration itself, as the tree's real visual foundation - the live branch
             lines/dots/hover-cards on top of it are still the only source of real per-feature
             information (this backdrop can't represent 13, or 1, or 40 real branches - only Stitch's
             own illustration), but it's the dominant artwork the operator asked for, not a faint
             ghost behind a schematic. Cropped to just the tree itself (scaled + clipped), excluding
             the surrounding mockup chrome (nav sidebar, "The Neural Ecosystem" header, legend text)
             that came baked into the original screenshot. -->
        <g mask="url(#backdrop-mask)">
          <image
            href={treeBackdrop}
            x={BACKDROP_IMG_X} y={BACKDROP_IMG_Y}
            width={BACKDROP_IMG_W} height={BACKDROP_IMG_H}
            opacity="0.6"
          />
        </g>

        <!-- Ambient canopy glow - depth without a WebGL shader -->
        <ellipse cx={VIEW_W / 2} cy={TRUNK_TOP_Y} rx={VIEW_W * 0.48} ry={VIEW_H * 0.4} fill="url(#canopy-glow)" />

        <!-- Product-alive glow (2026-08-10) - only appears once Phase 0/1 has produced at least one
             real observation; a flicker (not a hard error color) when the most recent check failed,
             since one failed check is evidence, not yet a confirmed shift (RuntimeHealthShiftDetector
             owns that judgment, not this view). -->
        {#if hasProductSignal}
          <ellipse
            cx={VIEW_W / 2} cy={TRUNK_TOP_Y} rx="170" ry="130"
            fill="url(#product-pulse-glow)" opacity={productGlowOpacity}
            class:canopy-flicker={!productHealthy}
          />
        {/if}

        <!-- Trunk: a single thin sway, matching the branches' own delicate line weight -->
        <path
          d="M {TRUNK_BASE_X} {TRUNK_BASE_Y}
             C {TRUNK_BASE_X + 10} {TRUNK_BASE_Y - (TRUNK_BASE_Y - TRUNK_TOP_Y) * 0.4},
               {TRUNK_TOP_X - 10} {TRUNK_TOP_Y + (TRUNK_BASE_Y - TRUNK_TOP_Y) * 0.35},
               {TRUNK_TOP_X} {TRUNK_TOP_Y}"
          fill="none" stroke="var(--tree-line)" stroke-width="2.5" stroke-linecap="round" opacity="0.8"
        />

        <!-- Root flourish -->
        <path
          d="M {TRUNK_BASE_X} {TRUNK_BASE_Y}
             Q {TRUNK_BASE_X - 34} {TRUNK_BASE_Y + 10} {TRUNK_BASE_X - 54} {TRUNK_BASE_Y - 2}
             M {TRUNK_BASE_X} {TRUNK_BASE_Y}
             Q {TRUNK_BASE_X + 32} {TRUNK_BASE_Y + 12} {TRUNK_BASE_X + 50} {TRUNK_BASE_Y + 1}"
          fill="none" stroke="var(--tree-line)" stroke-width="1.5" stroke-linecap="round" opacity="0.45"
        />

        <!-- Shared-factory root network (2026-08-10, Роща) - two deeper tendrils with a glowing tip
             each, representing the same infrastructure every project's tree grows from (TOC's own
             drum-buffer-rope). Calm sage by default; ambient tint shifts warm only while the factory
             is genuinely, deliberately throttling admissions - never a fabricated urgency. -->
        <path
          d="M {TRUNK_BASE_X - 54} {TRUNK_BASE_Y - 2} Q {TRUNK_BASE_X - 70} {TRUNK_BASE_Y + 26} {TRUNK_BASE_X - 66} {TRUNK_BASE_Y + 44}
             M {TRUNK_BASE_X + 50} {TRUNK_BASE_Y + 1} Q {TRUNK_BASE_X + 68} {TRUNK_BASE_Y + 28} {TRUNK_BASE_X + 64} {TRUNK_BASE_Y + 46}"
          fill="none" stroke={rootsStrained ? 'var(--tree-attention)' : 'var(--tree-line)'} stroke-width="1.6" stroke-linecap="round" opacity="0.75"
        />
        <circle cx={TRUNK_BASE_X - 66} cy={TRUNK_BASE_Y + 44} r="3.6" fill={rootsStrained ? 'var(--tree-attention)' : 'var(--tree-gold)'} filter="url(#node-glow)" class:root-pulse={rootsStrained}>
          <title>{rootsStrained ? 'The shared workshop is deliberately pacing itself right now' : 'The shared workshop, quietly running'}</title>
        </circle>
        <circle cx={TRUNK_BASE_X + 64} cy={TRUNK_BASE_Y + 46} r="3.6" fill={rootsStrained ? 'var(--tree-attention)' : 'var(--tree-gold)'} filter="url(#node-glow)" class:root-pulse={rootsStrained}>
          <title>{rootsStrained ? 'The shared workshop is deliberately pacing itself right now' : 'The shared workshop, quietly running'}</title>
        </circle>

        <!-- Seeds at the base - a scattered leaf cluster (capped so a large real backlog reads as a
             tidy pile, not a solid block), never a grid of unexplained dots. -->
        {#each (tree?.seeds ?? []).slice(0, MAX_VISIBLE_SEEDS) as seed, i (seed.wishlistId)}
          {@const p = seedLeafPlacement(seed, i)}
          <path
            d="M 0 0 Q 3.5 -5.5 0 -11 Q -3.5 -5.5 0 0 Z"
            class="seed-leaf"
            transform="translate({p.x} {p.y}) rotate({p.rotation}) scale({p.scale})"
          >
            <title>{seed.content}</title>
          </path>
        {/each}
        <!-- Always captioned right here - a pile of unlabeled dots at the trunk read as noise
             without it, the legend alone (physically far below) wasn't enough context on its own. -->
        {#if seedCount > 0}
          <text
            x={TRUNK_BASE_X - 90} y={TRUNK_BASE_Y + 20 - seedRows * 15 - 8}
            class="seed-caption"
          >{seedCount} wish{seedCount === 1 ? '' : 'es'} waiting to sprout{seedCount > MAX_VISIBLE_SEEDS ? ` (${seedCount - MAX_VISIBLE_SEEDS} not shown)` : ''}</text>
        {/if}

        <!-- Canopy: branches fan out from the trunk top, always within this one fixed frame -->
        {#each sortedBranches as branch, i (branch.featureId)}
          {@const angleDeg = sortedBranches.length > 1
            ? -90 - CANOPY_SPREAD_DEG / 2 + (i / (sortedBranches.length - 1)) * CANOPY_SPREAD_DEG
            : -90}
          {@const len = branchLength(branch)}
          {@const geo = branchGeometry(branch, angleDeg, len)}
          {@const leaves = leafCluster(branch, angleDeg)}
          <g
            class="branch-group"
            class:pulse={branch.livePulse}
            style="transform-box: view-box; transform-origin: {TRUNK_TOP_X}px {TRUNK_TOP_Y}px; animation-delay: {-hashSeed(branch.featureId) * 5}s; animation-duration: {3.6 + hashSeed(branch.featureId + 'd') * 2}s"
            role="button"
            tabindex="0"
            aria-label={branch.title ?? 'Feature'}
            onclick={() => toggleBranch(branch.featureId)}
            onkeydown={(e) => (e.key === 'Enter' || e.key === ' ') && toggleBranch(branch.featureId)}
            onmouseenter={(e) => onBranchEnter(branch, e)}
            onmousemove={updateHoverPos}
            onmouseleave={onBranchLeave}
            onfocus={(e) => onBranchEnter(branch, e as unknown as MouseEvent)}
            onblur={onBranchLeave}
          >
            <path d={geo.path} class="branch-line" use:drawIn />
            <g filter="url(#node-glow)">
              {#each leaves as leaf}
                <path
                  d="M 0 0 Q 7.5 -12 0 -24 Q -7.5 -12 0 0 Z"
                  fill={healthColor(branch.health)}
                  class="branch-leaf {healthClass(branch.health)}"
                  transform="translate({geo.endX + leaf.dx} {geo.endY + leaf.dy}) rotate({leaf.rot}) scale({leaf.scale})"
                />
              {/each}
            </g>
            <circle cx={geo.endX} cy={geo.endY} r="2.4" class="branch-node-core" />
            {#if branch.livePulse}
              <circle cx={geo.endX} cy={geo.endY} r="9" class="branch-node-ring" fill="none" filter="url(#pulse-glow)" />
            {/if}
            {#if branch.annotations.some(a => a.type !== 'active')}
              <circle cx={geo.endX + 10} cy={geo.endY - 8} r="3" class="branch-note-dot" />
            {/if}
          </g>
        {/each}
      </svg>

      {#if hoveredBranch}
        {@const b = hoveredBranch}
        <div class="hover-card" style="left: {hoverPos.x + 16}px; top: {hoverPos.y - 12}px;">
          <span class="hover-eyebrow">{b.complete ? 'Grown' : 'Growing'}{b.livePulse ? ' · active now' : ''}</span>
          <strong class="hover-title">{b.title ?? '(untitled)'}</strong>
          <span class="hover-meta">{b.mergedItemCount}/{b.codeProducingItemCount} merged · σ {b.health.sigmaLevel.toFixed(2)}</span>
        </div>
      {/if}
    </div>

    <!-- 2026-08-11 (bounded live-preview window): appears only while ClientRuntimeObservabilityService's
         last launch is still within its idle window (client-runtime-observability.live-preview-idle-
         minutes) - a real, currently-reachable instance, never a stale link to something already torn
         down. Gradually appears/disappears on its own as observation cycles come and go, by design. -->
    {#if runtimeHealth?.liveUrl}
      <a class="live-preview-link" href={runtimeHealth.liveUrl} target="_blank" rel="noopener noreferrer">
        🌿 Open the live product
      </a>
    {/if}

    <div class="tree-legend">
      <span class="legend-item"><span class="legend-dot healthy"></span>Healthy</span>
      <span class="legend-item"><span class="legend-dot attention"></span>Needs attention</span>
      <span class="legend-item"><span class="legend-dot live"></span>Active now</span>
      <span class="legend-item"><span class="legend-dot seed"></span>Seeded, not yet grown</span>
    </div>

    {#if sortedBranches.length === 0}
      <div class="empty-state">No branches yet — plant the first wish below.</div>
    {/if}

    {#if openBranchId}
      {@const openBranch = sortedBranches.find(b => b.featureId === openBranchId)}
      {#if openBranch}
        <BranchAnnotation branch={openBranch} onClose={() => (openBranchId = null)} />
      {/if}
    {/if}
  {/if}

  <SeedPlanter {projectId} onPlanted={fetchTree} />
</div>

<style>
  /* "Verdant Flow" palette, adapted from the operator's own Stitch reference project - scoped to
     this one living view only, the rest of the app keeps its established Electric Cobalt/off-white
     system untouched. */
  .product-tree {
    --tree-bg: #fbf9f1;
    --tree-line: #7d8570;
    --tree-healthy: #3f7d32;
    --tree-attention: #d97b29;
    --tree-critical: #e0342f;
    --tree-gold: #c99a2e;
    display: flex;
    flex-direction: column;
    gap: var(--space-4);
  }

  .eyebrow-tree {
    color: var(--tree-healthy);
    display: block;
    font-family: var(--font-body);
    font-size: 11px;
    font-weight: 700;
    letter-spacing: 0.12em;
    margin-bottom: 4px;
    text-transform: uppercase;
  }

  .tree-header {
    align-items: center;
    display: flex;
    justify-content: space-between;
  }

  .subtitle {
    color: var(--neutral-500);
    font-size: 14px;
    margin-top: 4px;
  }

  .trunk-log-toggle {
    background: var(--surface);
    border: 1px solid var(--neutral-300);
    color: var(--neutral-700);
    font-size: 13px;
    min-height: 34px;
    padding: 0 12px;
  }

  .trunk-log {
    background: var(--surface);
    border: 1px solid var(--neutral-200);
    border-radius: var(--radius);
    display: grid;
    gap: 8px;
    padding: 14px;
  }

  .trunk-note {
    display: flex;
    gap: 10px;
    font-size: 13px;
  }

  .note-time {
    color: var(--neutral-500);
    flex-shrink: 0;
    white-space: nowrap;
  }

  .empty-note {
    color: var(--neutral-500);
    font-size: 13px;
    margin: 0;
  }

  .tree-canvas {
    background: var(--tree-bg);
    border: 1px solid var(--neutral-200);
    border-radius: var(--radius-lg);
    padding: 12px;
    position: relative;
  }

  .tree-svg {
    aspect-ratio: 960 / 620;
    display: block;
    max-height: 72vh;
    width: 100%;
  }

  .branch-group {
    animation: sway 4s ease-in-out infinite;
    cursor: pointer;
  }

  @keyframes sway {
    0%, 100% { transform: rotate(-0.9deg); }
    50% { transform: rotate(0.9deg); }
  }

  @media (prefers-reduced-motion: reduce) {
    .branch-group { animation: none; }
  }

  .branch-line {
    fill: none;
    stroke: var(--tree-line);
    stroke-linecap: round;
    stroke-width: 2;
    transition: stroke 0.4s ease;
  }

  .seed-leaf {
    fill: var(--tree-gold);
    opacity: 0.8;
  }

  .seed-caption {
    fill: var(--neutral-500);
    font-family: var(--font-body);
    font-size: 10px;
  }

  .branch-leaf {
    opacity: 0.92;
    stroke: rgba(27, 28, 23, 0.12);
    stroke-width: 0.5;
    transition: fill 0.4s ease;
  }

  .branch-node-core {
    fill: #fff8e8;
    opacity: 0.9;
    pointer-events: none;
  }

  .branch-note-dot {
    fill: var(--tree-gold);
  }

  .branch-node-ring {
    animation: ring-expand 1.6s ease-out infinite;
    stroke: var(--tree-healthy);
    stroke-width: 1.4;
  }

  .live-preview-link {
    align-items: center;
    background: var(--tree-healthy);
    border-radius: 999px;
    color: var(--neutral-0, #fff);
    display: inline-flex;
    font-size: 13px;
    font-weight: 600;
    gap: 6px;
    margin: 4px 4px 0;
    padding: 6px 14px;
    text-decoration: none;
    transition: opacity 0.15s ease;
    width: fit-content;
  }

  .live-preview-link:hover {
    opacity: 0.85;
  }

  .tree-legend {
    color: var(--neutral-600);
    display: flex;
    flex-wrap: wrap;
    font-size: 12px;
    gap: 18px;
    padding: 0 4px;
  }

  .legend-item {
    align-items: center;
    display: inline-flex;
    gap: 6px;
  }

  .legend-dot {
    border-radius: 50%;
    display: inline-block;
    height: 8px;
    width: 8px;
  }

  .legend-dot.healthy { background: var(--tree-healthy); }
  .legend-dot.attention { background: var(--tree-attention); }
  .legend-dot.live {
    background: var(--tree-healthy);
    animation: breathe 1.6s ease-in-out infinite;
  }
  .legend-dot.seed { background: var(--tree-gold); }

  @media (prefers-reduced-motion: reduce) {
    .legend-dot.live { animation: none; }
  }

  @keyframes ring-expand {
    0% { r: 8; stroke-opacity: 0.55; }
    100% { r: 17; stroke-opacity: 0; }
  }

  @media (prefers-reduced-motion: reduce) {
    .branch-node-ring { animation: none; display: none; }
  }

  .branch-group.pulse .branch-leaf {
    animation: breathe 1.6s ease-in-out infinite;
  }

  @keyframes breathe {
    0%, 100% { opacity: 0.92; }
    50% { opacity: 0.5; }
  }

  @media (prefers-reduced-motion: reduce) {
    .branch-group.pulse .branch-leaf {
      animation: none;
    }
  }

  /* Роща ambient bands (2026-08-10) - same slow-breathe idiom as the rest of the tree, never a sharp
     blink; a genuine shift in product health is Kaizen's job to raise loudly, not this glow's. */
  .canopy-flicker {
    animation: breathe 3.2s ease-in-out infinite;
  }

  .root-pulse {
    animation: breathe 2.4s ease-in-out infinite;
  }

  @media (prefers-reduced-motion: reduce) {
    .canopy-flicker, .root-pulse { animation: none; }
  }

  .hover-card {
    background: var(--tree-bg);
    border: 1px solid var(--tree-gold);
    border-radius: var(--radius);
    box-shadow: 0 6px 18px rgba(27, 28, 23, 0.14);
    display: grid;
    gap: 3px;
    max-width: 240px;
    padding: 10px 12px;
    pointer-events: none;
    position: absolute;
    z-index: 5;
  }

  .hover-eyebrow {
    color: var(--tree-healthy);
    font-size: 10px;
    font-weight: 700;
    letter-spacing: 0.08em;
    text-transform: uppercase;
  }

  .hover-title {
    color: var(--neutral-900);
    font-family: var(--font-display);
    font-size: 14px;
    line-height: 1.3;
  }

  .hover-meta {
    color: var(--neutral-500);
    font-size: 11px;
  }

  .loading-state,
  .error-state,
  .empty-state {
    background: var(--surface);
    border: 1px solid var(--neutral-200);
    border-radius: var(--radius);
    color: var(--neutral-500);
    padding: 32px;
    text-align: center;
  }

  .error-state {
    color: var(--error);
  }
</style>
